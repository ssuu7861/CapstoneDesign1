package com.example.capstonedesign1

import android.util.Log
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.InetSocketAddress

import java.net.Socket
import java.net.UnknownHostException

class TcpClient(private val ip: String, private val port: Int, private val message: String) : Thread() {

    private val inetSocketAddress = InetSocketAddress(ip, port)
    private val socketTimeout = 5000
    private lateinit var socket: Socket
    private lateinit var outputStream: OutputStream
    private lateinit var inputStream: InputStream
    lateinit var result: String


    override fun run() {
        try {
            socket = Socket()
            socket.connect(inetSocketAddress, socketTimeout)

            outputStream = socket.getOutputStream()
            outputStream.write(message.toByteArray())

            inputStream = socket.getInputStream()
            val buffer = ByteArray(1024)
            val bytes = inputStream.read(buffer)
            result = String(buffer.copyOfRange(0, bytes))
            socket.close()

        } catch (e: Exception){
            e.printStackTrace()
        }
    }
}