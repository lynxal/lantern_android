package com.lynxal.lantern_android.internal

import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.nio.ByteBuffer

internal class LanternQuerySender(
    private val serviceType: String,
    private val mdnsAddress: InetAddress,
    private val mdnsPort: Int,
) {
    fun send(socket: MulticastSocket) {
        val buf = ByteBuffer.allocate(512)
        buf.putShort(0)  // transaction ID
        buf.putShort(0)  // flags: standard query
        buf.putShort(1)  // QDCount
        buf.putShort(0)  // ANCount
        buf.putShort(0)  // NSCount
        buf.putShort(0)  // ARCount
        DnsNameCodec.encodeName(serviceType, buf)
        buf.putShort(12) // QTYPE = PTR
        buf.putShort(1)  // QCLASS = IN
        val data = buf.array().copyOf(buf.position())
        socket.send(DatagramPacket(data, data.size, mdnsAddress, mdnsPort))
    }
}
