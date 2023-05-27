package com.example.capstonedesign1

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

class UdpClient(private val ip: String, private val port: Int, private val message: String) : Thread() {
    override fun run() {
        try {
            val socket = DatagramSocket()
            val send = message.toByteArray()
            val inetSocketAddress = InetSocketAddress(ip, port)
            val datagramPacket = DatagramPacket(send, send.size, inetSocketAddress)
            socket.send(datagramPacket)
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}