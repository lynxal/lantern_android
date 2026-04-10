package com.lynxal.lantern_android.internal

import com.lynxal.lantern_android.model.DnsRecord
import com.lynxal.lantern_android.model.ServiceInfo
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class ParseResult(val found: List<ServiceInfo>, val lost: List<String>)

internal class LanternPacketParser(private val serviceType: String) {

    private val normalizedType = serviceType.trimEnd('.').lowercase()

    /**
     * Parse a raw mDNS UDP payload.
     *
     * @param data      datagram payload bytes
     * @param len       valid byte count in [data]
     * @param srcIp     source IP of the datagram (fallback when no A record in packet)
     * @return [ParseResult] with found services and lost instance names (TTL=0 goodbye packets)
     */
    fun parse(data: ByteArray, len: Int, srcIp: String): ParseResult {
        val buf = ByteBuffer.wrap(data, 0, len).order(ByteOrder.BIG_ENDIAN)
        return try {
            buf.getShort() // transaction ID (ignored for mDNS)
            val flags = buf.getShort().toInt() and 0xFFFF
            if (flags and 0x8000 == 0) return ParseResult(emptyList(), emptyList()) // not a response

            val qdCount = buf.getShort().toInt() and 0xFFFF
            val anCount = buf.getShort().toInt() and 0xFFFF
            val nsCount = buf.getShort().toInt() and 0xFFFF
            val arCount = buf.getShort().toInt() and 0xFFFF

            repeat(qdCount) {
                DnsNameCodec.readName(buf, data)
                buf.getShort(); buf.getShort() // qtype, qclass
            }

            val records = mutableListOf<DnsRecord>()
            repeat(anCount + nsCount + arCount) {
                readRecord(buf, data)?.let { records.add(it) }
            }

            assemble(records, srcIp)
        } catch (_: Exception) {
            ParseResult(emptyList(), emptyList())
        }
    }

    // ---- Assembly ----------------------------------------------------------

    private fun assemble(records: List<DnsRecord>, srcIp: String): ParseResult {
        val ptrs = records.filterIsInstance<DnsRecord.Ptr>()
            .filter { it.name.lowercase() == normalizedType }

        // TTL=0 means goodbye — service is being deregistered
        val lost = ptrs.filter { it.ttl == 0 }.map { it.target }

        val found = ptrs.filter { it.ttl > 0 }.mapNotNull { ptr ->
            val instanceName = ptr.target
            val normalized = instanceName.lowercase()
            val srv = records.filterIsInstance<DnsRecord.Srv>()
                .firstOrNull { it.name.lowercase() == normalized } ?: return@mapNotNull null
            val txt = records.filterIsInstance<DnsRecord.Txt>()
                .firstOrNull { it.name.lowercase() == normalized } ?: return@mapNotNull null
            val aRec = records.filterIsInstance<DnsRecord.A>()
                .firstOrNull { it.name.lowercase() == srv.target.lowercase() }

            ServiceInfo(
                instanceName = instanceName,
                host = aRec?.address ?: srcIp,
                port = srv.port,
                txtRecords = txt.rawAttributes.toStringMap(),
                rawTxtRecords = txt.rawAttributes,
            )
        }

        return ParseResult(found, lost)
    }

    // ---- Record parsing ----------------------------------------------------

    private fun readRecord(buf: ByteBuffer, raw: ByteArray): DnsRecord? {
        val name = DnsNameCodec.readName(buf, raw)
        val type = buf.getShort().toInt() and 0xFFFF
        buf.getShort()        // class
        val ttl = buf.getInt() // TTL — kept for goodbye packet detection (TTL=0)
        val rdLen = buf.getShort().toInt() and 0xFFFF
        val rdStart = buf.position()

        val record = when (type) {
            TYPE_PTR -> {
                DnsRecord.Ptr(name, DnsNameCodec.readName(buf, raw), ttl)
            }
            TYPE_SRV -> {
                buf.getShort() // priority
                buf.getShort() // weight
                val port = buf.getShort().toInt() and 0xFFFF
                DnsRecord.Srv(name, DnsNameCodec.readName(buf, raw), port)
            }
            TYPE_TXT -> {
                val attrs = mutableListOf<ByteArray>()
                var remaining = rdLen
                while (remaining > 0) {
                    val strLen = buf.get().toInt() and 0xFF
                    remaining--
                    if (strLen == 0 || strLen > remaining) break
                    val strBytes = ByteArray(strLen)
                    buf.get(strBytes)
                    remaining -= strLen
                    attrs.add(strBytes)
                }
                DnsRecord.Txt(name, attrs)
            }
            TYPE_A -> {
                if (rdLen == 4) {
                    val addrBytes = ByteArray(4)
                    buf.get(addrBytes)
                    DnsRecord.A(name, InetAddress.getByAddress(addrBytes).hostAddress ?: "")
                } else null
            }
            else -> null
        }

        buf.position(rdStart + rdLen)
        return record
    }

    companion object {
        private const val TYPE_PTR = 12
        private const val TYPE_SRV = 33
        private const val TYPE_TXT = 16
        private const val TYPE_A   = 1
    }
}

/**
 * Decodes a list of raw TXT attribute byte arrays into a UTF-8 key=value map.
 * Entries without '=' are mapped to an empty string value.
 * Binary values that cannot be decoded as UTF-8 are silently skipped.
 */
private fun List<ByteArray>.toStringMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    forEach { bytes ->
        try {
            val str = bytes.toString(Charsets.UTF_8)
            val eq = str.indexOf('=')
            when {
                eq > 0  -> map[str.substring(0, eq)] = str.substring(eq + 1)
                eq == -1 -> map[str] = "" // boolean flag key, no value
            }
        } catch (_: Exception) {}
    }
    return map
}
