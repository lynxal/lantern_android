package com.lynxal.lantern_android

import android.content.Context
import android.net.wifi.WifiManager
import com.lynxal.lantern_android.internal.LanternPacketParser
import com.lynxal.lantern_android.internal.LanternQuerySender
import com.lynxal.lantern_android.internal.LanternSocket
import com.lynxal.lantern_android.model.ServiceInfo
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.InetAddress

/**
 * Zero-dependency mDNS/DNS-SD service discovery for Android API 28+.
 *
 * Operates on raw multicast UDP — PTR/SRV/TXT/A records from the same datagram
 * are assembled together, avoiding the NsdManager IP/TXT cross-device mixing bug.
 *
 * Usage:
 * ```
 * val lantern = LanternDiscovery(context, LanternConfig("_canvas._tcp.local."), listener)
 * lantern.start()
 * // ...
 * lantern.stop()
 * ```
 */
class LanternDiscovery(
    context: Context,
    private val config: LanternConfig,
    private val listener: LanternServiceListener,
) {
    private val appContext = context.applicationContext
    private val mdnsAddress = InetAddress.getByName(MDNS_IPV4_ADDRESS)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val parser = LanternPacketParser(config.serviceType)
    private val socketManager = LanternSocket(mdnsAddress, MDNS_PORT, config.socketTimeoutMs)
    private val querySender = LanternQuerySender(config.serviceType, mdnsAddress, MDNS_PORT)

    private val activeInstances = mutableSetOf<String>()

    private lateinit var multicastLock: WifiManager.MulticastLock

    fun start() {
        acquireMulticastLock()
        scope.launch { runDiscovery() }
    }

    fun stop() {
        scope.cancel()
        socketManager.close()
        releaseMulticastLock()
    }

    // ---- Discovery loop ----------------------------------------------------

    private suspend fun runDiscovery() {
        try {
            val socket = socketManager.open()
            querySender.send(socket)

            if (config.queryIntervalMs > 0) {
                scope.launch {
                    while (isActive) {
                        delay(config.queryIntervalMs)
                        try { querySender.send(socket) } catch (_: Exception) {}
                    }
                }
            }

            val buf = ByteArray(4096)
            while (scope.isActive) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val srcIp = packet.address.hostAddress ?: continue
                    val result = parser.parse(packet.data, packet.length, srcIp)
                    result.found.forEach { notifyFound(it) }
                    result.lost.forEach { notifyLost(it) }
                } catch (_: java.net.SocketTimeoutException) {
                    // normal — keep looping
                }
            }
        } catch (e: Exception) {
            if (scope.isActive) {
                withContext(Dispatchers.Main) { listener.onError(e) }
            }
        }
    }

    private suspend fun notifyFound(service: ServiceInfo) {
        val isNew = activeInstances.add(service.instanceName)
        if (isNew) {
            withContext(Dispatchers.Main) { listener.onServiceFound(service) }
        }
    }

    private suspend fun notifyLost(instanceName: String) {
        val wasPresent = activeInstances.remove(instanceName)
        if (wasPresent) {
            withContext(Dispatchers.Main) { listener.onServiceLost(instanceName) }
        }
    }

    // ---- Multicast lock ----------------------------------------------------

    private fun acquireMulticastLock() {
        val wm = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wm.createMulticastLock("LanternDiscovery")
        multicastLock.setReferenceCounted(true)
        multicastLock.acquire()
    }

    private fun releaseMulticastLock() {
        if (::multicastLock.isInitialized && multicastLock.isHeld) {
            multicastLock.release()
        }
    }

    companion object {
        // RFC 6762 §2 — IANA assigned link-local multicast address for mDNS
        const val MDNS_IPV4_ADDRESS = "224.0.0.251"
        const val MDNS_PORT = 5353
    }
}
