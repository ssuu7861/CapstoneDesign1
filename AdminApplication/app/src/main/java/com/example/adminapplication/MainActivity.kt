package com.example.adminapplication

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import net.daum.mf.map.api.MapPOIItem
import net.daum.mf.map.api.MapPoint
import net.daum.mf.map.api.MapView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/*
Main Server : 43.201.82.94 - 8080
 Sub Server : 54.89.51.96 - 9000
 */
class MainActivity : AppCompatActivity() {

    private val serverIp = "43.201.82.94"
    private val serverPort = 8080
    private val serverAddress = InetSocketAddress(serverIp,serverPort)
    private lateinit var tcpSocket : Socket
    private lateinit var udpSocket : DatagramSocket
    private var riderList = CopyOnWriteArrayList<Rider>()
    private lateinit var user: User
    private lateinit var mapView : MapView
    private lateinit var mapViewContainer : ViewGroup
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private var selectedValue = -1
    private val riderLocationMap = ConcurrentHashMap<Int, RiderLocation>()
    private val markerMap = ConcurrentHashMap<Int,MapPOIItem>()
    private var riderIOFeedback = CopyOnWriteArrayList<Int>()
    private val TAG = "LogData"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView = MapView(this)
        mapViewContainer = findViewById(R.id.map_view)
        mapViewContainer.addView(mapView)

        mapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(35.89000, 128.60000), true);


        initializeSockets()

        udpTest()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            mapView.currentLocationTrackingMode = MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading
        }



        val startButton = findViewById<View>(R.id.rider_start) as Button
        startButton.setOnClickListener {
            Toast.makeText(this, "startButton", Toast.LENGTH_SHORT).show()
            user.login = true
            tcpReceive()
            udpReceive()
            tcpSend(user.company)

        }

        val settingButton = findViewById<View>(R.id.setting_button) as Button
        settingButton.setOnClickListener {
            val listCheckBox = findViewById<View>(R.id.list_checkbox)
            if (listCheckBox.visibility == View.VISIBLE) {
                listCheckBox.visibility = View.INVISIBLE
            } else {
                listCheckBox.visibility = View.VISIBLE
            }
        }

        val closeButton = findViewById<View>(R.id.close) as Button
        closeButton.setOnClickListener {
            Toast.makeText(this, "closeButton", Toast.LENGTH_SHORT).show()
            user.login = false
        }


        val radioGroup = findViewById<RadioGroup>(R.id.radioGroup)
        for (i in 0 until radioGroup.childCount) {
            val radioButton = radioGroup.getChildAt(i) as RadioButton
            radioButton.setOnClickListener {
                selectedValue = i + 1
            }
        }
        val applyButton = findViewById<Button>(R.id.apply)
        applyButton.setOnClickListener {
            Toast.makeText(this, "applyButton", Toast.LENGTH_SHORT).show()

            if (mapView.poiItems != null) {
                mapView.removeAllPOIItems()
            }
            if (selectedValue != -1) {
                tcpSend(selectedValue)
            } else {
                Toast.makeText(this, "값을 선택해주세요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeSockets() {
        val socketInitializationThread = Thread{
            udpSocket = DatagramSocket()
            tcpSocket = Socket()
            tcpSocket.connect(serverAddress, 5000)
        }
        socketInitializationThread.start()
        try {
            socketInitializationThread.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun tcpSend(branch : Int) {
        Thread{
            if(tcpSocket.isConnected && user.login){
                try {
                    val outputStream = tcpSocket.getOutputStream()
                    val head = 1234;
                    val bodyLength = 24;
                    var buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
                        putInt(head)
                        putInt(user.job)
                        putInt(user.company)
                        putInt(bodyLength)
                    }
                    outputStream.write(buffer.array())
                    buffer.clear()
                    buffer = ByteBuffer.allocate(bodyLength).order(ByteOrder.LITTLE_ENDIAN).apply {
                        putInt(user.id)
                        putInt(branch) // 원하는 브랜치
                        putInt(user.port)
                        putInt(1) // latitude
                        putInt(2) // longitude
                        putInt(20)
                    }
                    outputStream.write(buffer.array())

                }catch (e:Exception){
                    e.printStackTrace()
                }
            }
        }.start()
    }

    private fun tcpReceive() {
        Thread{
            while (tcpSocket.isConnected&&user.login) {
                try {
                    val inputStream = tcpSocket.getInputStream()
                    val buffer = ByteArray(1024)
                    val bytes = inputStream.read(buffer)

                    if (bytes > 0) {
                        val result = String(buffer, 0, bytes, Charsets.UTF_8)
                        Log.d(TAG, "tcpReceiveResult : $result")
                        tcpDataParser(result)
                        udpSend()
                        timer()

                    } else {
                        riderList.clear()
                        Log.d(TAG, "tcpReceiveResult : data 누락")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    private fun tcpDataParser(message: String){
//        if (riderList.isNotEmpty()) {
//            for (rider in riderList) {
//                mapView.removePOIItem(mapView.findPOIItemByTag(rider.id))
//            }
//        }

        riderList.clear()
        val riders = message.split("|")

        riders.forEach{riderStr ->
            val rider = riderStr.split("-")
            riderList.add(Rider(rider[0].toInt(),rider[1], rider[2].toInt() , false))

        }
        riderList.forEach{
                rider ->  Log.d(TAG, "RiderList ID : ${rider.id} IP:${rider.ip}, PORT:${rider.port}")

        }
    }

    private fun checkRiderIO(): CopyOnWriteArrayList<Int> {
        var riderIdList = CopyOnWriteArrayList<Int>()
        riderIdList.clear()
        for (rider in riderList) {
            if (!rider.timeout) {
                riderIdList.add(rider.id)
            }
        }
        return riderIdList
    }

    private fun timer () {
        Thread {
            Thread.sleep(10000)
            riderIOFeedback.clear()
            riderIOFeedback = checkRiderIO()
            for(id in riderIOFeedback){
                Log.d(TAG, "UDP Holepunching Failed User ID : ${id}")
            }

            if(riderIOFeedback.size != 0){

                if(tcpSocket.isConnected && user.login){
                    try {
                        val outputStream = tcpSocket.getOutputStream()
                        val head = 1234;
                        val bodyLength = riderIOFeedback.size * 4
                        Log.d(TAG, "UDP FeedBack before send IDlist size : $bodyLength")
                        var buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
                            putInt(head)
                            putInt(3)
                            putInt(user.company)
                            putInt(bodyLength+8)
                        }
                        outputStream.write(buffer.array())
                        buffer.clear()
                        buffer = ByteBuffer.allocate(bodyLength+8).order(ByteOrder.LITTLE_ENDIAN)
                        buffer.putInt(21)  /*user.id*/
                        buffer.putInt(riderIOFeedback.size)
                        for( id in riderIOFeedback){
                            buffer.putInt(id)
                        }
                        outputStream.write(buffer.array())

                    }catch (e:Exception){
                        e.printStackTrace()
                    }
                }
            }



        }.start()
    }

    private fun udpTest() {
        val udpTestThread = Thread{
            var inetSocketAddress = InetSocketAddress("15.165.22.113",3000)
            val send = "HI".toByteArray()
            var datagramPacket = DatagramPacket(send, send.size, inetSocketAddress)
            udpSocket.send(datagramPacket)
            val buffer = ByteArray(1024)
            datagramPacket = DatagramPacket(buffer, buffer.size)
            udpSocket.receive(datagramPacket)
            val result = String(datagramPacket.data, 0, datagramPacket.length, Charsets.UTF_8)/*.trim()*/
            Log.d(TAG, "udpTest - PORT : $result")
            user = User(result.toInt())
        }
        udpTestThread.start()
        try {
            udpTestThread.join()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun udpSend() {
        Thread{
            if(user.login){
                try{
                    riderList.forEach{rider ->
                        val inetSocketAddress = InetSocketAddress(rider.ip, rider.port)
                        val send = "1".toByteArray()
                        val datagramPacket = DatagramPacket(send, send.size, inetSocketAddress)
                        udpSocket.send(datagramPacket)
                    }

                }catch (e:Exception){
                    e.printStackTrace()
                }
            }
        }.start()
    }

    data class RiderLocation(var latitude: Double, var longitude: Double)


    private fun udpReceive() {
        val receiver = Thread {
            while(user.login){
                try {
                    val buffer = ByteArray(1024)
                    val datagramPacket = DatagramPacket(buffer, buffer.size)
                    udpSocket.receive(datagramPacket)
                    val result = String(datagramPacket.data, 0, datagramPacket.length).trim()
                    Log.d(TAG, "udpReceiveResult: $result")
                    udpDataParser(result)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        receiver.start()
    }

    private fun udpDataParser(message: String) {
        val data = message.split("-")
        if (data.size == 3) {
            val id = data[0].toInt()
            val latitude = data[1].toDouble() / 100000
            val longitude = data[2].toDouble() / 100000

            if (checkRiderList(id)) {
                Log.d(TAG, "checkrider 앞")
                val riderLocation = riderLocationMap[id]
                if (riderLocation == null) {
                    Log.d(TAG, "checkrider 안  NULL ")

                    riderLocationMap[id] = RiderLocation(latitude, longitude)
                    updateMarkerOnMapView(id, latitude, longitude)
                } else {
                    Log.d(TAG, "checkrider 안 ELSE ")
                    riderLocation.latitude = latitude
                    riderLocation.longitude = longitude
                    if (mapView.poiItems != null) {
                        mapView.removePOIItem(markerMap[id])
                    }
                    updateMarkerOnMapView(id, latitude, longitude)
                }

            }
        }
    }

    private fun checkRiderList(userid: Int): Boolean {
        for (rider in riderList) {
            if (rider.id == userid) {
                rider.timeout = true
                return true
            }
        }
        return false
    }

    private fun updateMarkerOnMapView(id: Int, latitude: Double, longitude: Double) {
        val markerLocation = MapPoint.mapPointWithGeoCoord(latitude, longitude)
        val marker = MapPOIItem().apply {
            itemName = "User" + id
            tag = id
            mapPoint = markerLocation
            markerType = MapPOIItem.MarkerType.CustomImage
            customImageResourceId = getMarkerImageResourceId(id)
            isShowCalloutBalloonOnTouch = false // 터치 시 호출 풍선 표시 안 함
        }
        mapView.addPOIItem(marker)
        markerMap[id] = marker
    }
    private fun getMarkerImageResourceId(id: Int): Int {

        return when (id % 5) {
            0 -> R.drawable.marker_1
            1 -> R.drawable.marker_2
            2 -> R.drawable.marker_3
            3 -> R.drawable.marker_4
            4 -> R.drawable.marker_5
            else -> R.drawable.marker_6

        }
    }




    private fun getAppKeyHash() {
        try {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            for (signature in info.signatures) {
                val md: MessageDigest = MessageDigest.getInstance("SHA")
                md.update(signature.toByteArray())
                val hashKey = String(android.util.Base64.encode(md.digest(), 0))
                Log.d("hash", "해시키 : $hashKey")
            }
        } catch (e: Exception) {
            Log.e("error", "해시키를 찾을 수 없습니다 : $e")
        }
    }
}