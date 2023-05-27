package com.example.riderapplication

import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup

import android.widget.Button
import net.daum.mf.map.api.MapView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.timerTask

/*
Main Server : 15.165.129.230 - 8080
 Sub Server : 54.89.51.96 - 9000
 */
class MainActivity : AppCompatActivity() {

    private val serverIp = "54.89.51.96"
    private val serverPort = 9000
    private val serverAddress = InetSocketAddress(serverIp,serverPort)
    private lateinit var tcpSocket : Socket
    private lateinit var udpSocket : DatagramSocket
    private val tcpTimer = Timer()
    private val udpTimer = Timer()
    private var managerList = CopyOnWriteArrayList<Manager>()
    private var location = CopyOnWriteArrayList<Int>()
    private lateinit var user: User

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapView = MapView(this)
        val mapViewContainer = findViewById<ViewGroup>(R.id.map_view)
        mapViewContainer.addView(mapView)


        initializeSockets()

        user = User(udpSocket.localPort)

        val loginButton = findViewById<View>(R.id.login) as Button
        val logoutButton = findViewById<View>(R.id.logout) as Button
        val randomStartButton = findViewById<View>(R.id.randomStart) as Button
        val randomStopButton = findViewById<View>(R.id.randomStop) as Button

        loginButton.setOnClickListener {
            user.login = true

            tcpReceive()
            tcpTimer.scheduleAtFixedRate(timerTask { tcpSend() }, 0, 7000)
            //udpTimer.scheduleAtFixedRate(timerTask { udpSend() }, 0, 1000)

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
            try {
                val outputStream = tcpSocket.getOutputStream()
//                outputStream.write("port : ${udpSocket.localPort}".toByteArray())
                val head = 1234;
                val bodyLength = 16;
                val buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(head)
                    putInt(user.job)
                    putInt(user.company)
                    putInt(bodyLength)
                }
                outputStream.write(buffer.array())
                buffer.clear()

                val latitude = 1;
                val longitude = 16;

                buffer.putInt(user.id)
                buffer.putInt(user.port)
                buffer.putInt(latitude)
                buffer.putInt(longitude)
                outputStream.write(buffer.array())

            }catch (e:Exception){
                e.printStackTrace()
            }
        }.start()
    }
    private fun udpSend() {
        Thread{
            try{
                managerList.forEach{manager ->
                    //아이디, 위도, 경도 넣기
                    val inetSocketAddress = InetSocketAddress(manager.ip, manager.port)
                    val send = user.id.toString().toByteArray()
                    val datagramPacket = DatagramPacket(send, send.size, inetSocketAddress)
                    udpSocket.send(datagramPacket)
                }

            }catch (e:Exception){
                e.printStackTrace()
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
                    val result = String(buffer.copyOfRange(0, bytes))
                    Log.d("receive", result)
                    parsing(result)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    private fun parsing(message: String){
        val managers = message.split("|")
        managers.forEach{managerStr ->
            val manager = managerStr.split("-")
            managerList.add(Manager(manager[0], manager[1].toInt()))
        }
        //초기화 : clear()
        //삭제 :managerList.removeIf { manager -> manager.id == "1"}
        managerList.forEach{
                manager ->  Log.d("ManagerList", "IP:${manager.ip}, PORT:${manager.port}")
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