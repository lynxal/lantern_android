package com.lynxal.lantern_android.model

/**
 * A fully resolved mDNS service instance.
 * All records originate from the same UDP datagram — no cross-device mixing possible.
 *
 * @param instanceName  DNS-SD instance name (e.g. "CanvasGateway-abc._canvas._tcp.local")
 * @param host          IPv4 address from the A record in the same packet, or datagram src IP
 * @param port          from SRV record
 * @param txtRecords    TXT attributes decoded as UTF-8 key=value pairs; use for typical string values
 * @param rawTxtRecords raw TXT attribute byte arrays as received on the wire; use when values are binary
 */
data class ServiceInfo(
    val instanceName: String,
    val host: String,
    val port: Int,
    val txtRecords: Map<String, String>,
    val rawTxtRecords: List<ByteArray>,
)
