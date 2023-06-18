package com.example.riderapplication

import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.Manifest
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import net.daum.mf.map.api.MapPoint
import net.daum.mf.map.api.MapView
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.timerTask

/*
Main Server : 15.165.129.230 - 8080
 Sub Server : 54.89.51.96 - 9000
 */
class MainActivity : AppCompatActivity(), MapView.CurrentLocationEventListener{

    private val serverIp = "43.201.82.94"
    private val serverPort = 8080
    private val serverAddress = InetSocketAddress(serverIp,serverPort)
    private lateinit var tcpSocket : Socket
    private lateinit var udpSocket : DatagramSocket
    private val tcpTimer = Timer()
    private val udpTimer = Timer()
    private var managerList = CopyOnWriteArrayList<Manager>()
    private val location = AtomicReference<Pair<Int, Int>>()
    private lateinit var user: User
    private lateinit var mapView : MapView
    private lateinit var mapViewContainer : ViewGroup
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val TAG = "LogData"



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView = MapView(this)
        mapViewContainer = findViewById(R.id.map_view)
        mapViewContainer.addView(mapView)
        mapView.currentLocationTrackingMode = MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading
        mapView.setShowCurrentLocationMarker(true)
        mapView.setCurrentLocationEventListener(this)


        initializeSockets()

        udpTest()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            mapView.currentLocationTrackingMode = MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading
        }

        val loginButton = findViewById<View>(R.id.login) as Button
        val logoutButton = findViewById<View>(R.id.logout) as Button


        loginButton.setOnClickListener {
            Toast.makeText(this, "loginButton", Toast.LENGTH_SHORT).show()
            user.login = true

            tcpReceive()
            tcpTimer.scheduleAtFixedRate(timerTask { tcpSend() }, 0, 7000)
            udpTimer.scheduleAtFixedRate(timerTask { udpSend() }, 0, 1000)

        }
        logoutButton.setOnClickListener {
            Toast.makeText(this, "logoutButton", Toast.LENGTH_SHORT).show()
            user.login = false
        }



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
            Log.d(TAG, "udpTestReceive : $result")
            user = User(result.toInt())
        }
        udpTestThread.start()
        try {
            udpTestThread.join()
        } catch (e: Exception) {
            e.printStackTrace()
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

    private fun tcpSend() {
        Thread{
            if(tcpSocket.isConnected && user.login){
                try {
                    val outputStream = tcpSocket.getOutputStream()
    //                outputStream.write("location: $latitude, $longitude ".toByteArray())
                    val head = 1234
                    val bodyLength = 20
                    var buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
                        putInt(head)
                        putInt(user.job)
                        putInt(1)
                        putInt(bodyLength)
                    }
                    outputStream.write(buffer.array())

                    buffer.clear()
                    val (latitude, longitude) = location.get()

                    buffer = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).apply{
                        putInt(user.id)
                        putInt(user.company)
                        putInt(user.port)
                        putInt(latitude)
                        putInt(longitude)
                    }
                    outputStream.write(buffer.array())
                }catch (e:Exception){
                    e.printStackTrace()
                }
            }
        }.start()
    }
    private fun udpSend() {
        Thread{
            if(user.login){
                try{
                    managerList.forEach{manager ->
                        val inetSocketAddress = InetSocketAddress(manager.ip, manager.port)
                        val send = "${user.id}-${location.get().first}-${location.get().second}".toByteArray()
                        val datagramPacket = DatagramPacket(send, send.size, inetSocketAddress)
                        udpSocket.send(datagramPacket)
                    }

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
                    tcpSocket.soTimeout=5000
                    val inputStream = tcpSocket.getInputStream()
                    val buffer = ByteArray(1024)
                    val bytes = inputStream.read(buffer)
                    if (bytes > 0) {
                        val result = String(buffer, 0, bytes, Charsets.UTF_8)
                        Log.d(TAG, "TCP Result : $result")
                        parsing(result)
                    } else {
                        managerList.clear()
                        Log.d(TAG, "TcpReceive : data 누락")
                    }

            }
                catch (e: SocketTimeoutException) {
                    // Timeout occurred
                    managerList.clear()
                    Log.d(TAG, "TcpReceive : 응답이 없음")
                }catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.start()
    }



    override fun onCurrentLocationUpdate(mapView: MapView?, currentLocation: MapPoint, accuracyInMeters: Float) {

        mapView?.setMapCenterPointAndZoomLevel(currentLocation, 1, true)
        val coordinate = currentLocation.mapPointGeoCoord
        Log.d(TAG,  "GPS : ${coordinate.latitude}, ${coordinate.longitude}")
        location.set(Pair((coordinate.latitude * 100000).toInt(), (coordinate.longitude*100000).toInt()))

    }
    //위치 추적 실패
    override fun onCurrentLocationDeviceHeadingUpdate(mapView: MapView?, deviceHeading: Float) {

    }

    // 위치 추적 시 사용자가 지정한 경로를 벗어났을 때
    override fun onCurrentLocationUpdateFailed(mapView: MapView?) {

    }

    // 위치 추적 종료 시
    override fun onCurrentLocationUpdateCancelled(mapView: MapView?) {

    }


    private fun parsing(message: String){
        val managers = message.split("|")
        managerList.clear()
        managers.forEach{managerStr ->
            val manager = managerStr.split("-")
            managerList.add(Manager(manager[0], manager[1].toInt()))

        }
        //초기화 : clear()
        //삭제 :managerList.removeIf { manager -> manager.id == "1"}
        managerList.forEach{
                manager ->  Log.d(TAG, "ManagerList IP:${manager.ip}, PORT:${manager.port}")
        }

    }

    private fun cancelTcpTimer() {
        tcpTimer.cancel()
        tcpTimer.purge()
    }
    private fun cancelUdpTimer() {
        udpTimer.cancel()
        udpTimer.purge()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTcpTimer()
        cancelUdpTimer()
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