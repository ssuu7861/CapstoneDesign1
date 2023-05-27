package com.example.capstonedesign1

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/*
MainServer : ip : 15.165.129.230 / port : 8080

 */

class MainActivity : AppCompatActivity() {

    private val SERVERIP = "15.165.129.230"
    private val SERVERPORT = 8080
    private val tcpSend = "tcpTest0503"

    private lateinit var udpAddressPair: Pair<String, Int>
    private val message : String = "udpTest"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tcpButton = findViewById<View>(R.id.tcp_button) as Button
        tcpButton.setOnClickListener {
            val tcpReceive = connectToServer(SERVERIP, SERVERPORT, tcpSend)
            udpAddressPair = parseJson(tcpReceive)
            Log.d("TcpTest", "IP : ${udpAddressPair.first} , PORT : ${udpAddressPair.second}")

        }

        val udpButton = findViewById<View>(R.id.udp_button) as Button
        udpButton.setOnClickListener{
            connectToAdmin(udpAddressPair.first, udpAddressPair.second, message)
        }
    }
}


//TCP 연결 함수
private fun connectToServer(ip: String, port: Int, message : String) : String{
    val tcpClient = TcpClient(ip, port, message)
    tcpClient.start()
    tcpClient.join()
    val result = tcpClient.result
    return result
}

//UDP 연결 함수
private fun connectToAdmin(ip: String, port: Int, message: String){
    val udpClient = UdpClient(ip, port, message)
    udpClient.start()
}

//JSON Parsing, 후에 여러 ip, port 받게 변경 필요
private fun parseJson(jsonString: String): Pair<String, Int> {
    val jsonObject = JSONObject(jsonString)
    val ip = jsonObject.getString("IP")
    val port = jsonObject.getInt("PORT")
    return Pair(ip, port)
}

//--------------------------------------------------------------------------------


private fun connectToAdmin2(addressArray: ArrayList<ArrayList<String>>, message: String){
    for (subList in addressArray){
        val ip = subList[0]
        val port = subList[1].toInt()
        val udpClient = UdpClient(ip, port, message)
        udpClient.start()
        Log.d("kgs", "${ip} - ${port} UDP CONNETION")
    }
}


//String Parsing용
private fun parsing(msg: String): ArrayList<ArrayList<String>> {
    val tmp = msg.split("-")
    var result = ArrayList<ArrayList<String>>()

    for(i in tmp){
        val a = i.split("|")
        result.add(arrayListOf(a[0], a[1]))
    }
    return result
}