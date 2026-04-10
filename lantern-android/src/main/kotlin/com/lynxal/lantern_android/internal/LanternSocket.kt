package com.lynxal.lantern_android.internal

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

internal class LanternSocket(
    private val mdnsAddress: InetAddress,
    private val port: Int,
    private val timeoutMs: Int,
) {
    private var socket: MulticastSocket? = null

    fun open(): MulticastSocket {
        return MulticastSocket(port).apply {
            reuseAddress = true
            soTimeout = timeoutMs
            // Join on all suitable interfaces (handles multi-homed devices)
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.filter { it.isUp && it.supportsMulticast() && !it.isLoopback }
                ?.forEach { iface ->
                    try {
                        joinGroup(InetSocketAddress(mdnsAddress, port), iface)
                    } catch (_: Exception) {}
                }
        }.also { socket = it }
    }

    fun close() {
        try {
            socket?.leaveGroup(mdnsAddress)
            socket?.close()
        } catch (_: Exception) {}
        socket = null
    }
}
