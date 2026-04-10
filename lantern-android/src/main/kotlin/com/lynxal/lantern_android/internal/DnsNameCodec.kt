package com.lynxal.lantern_android.internal

import java.nio.ByteBuffer

internal object DnsNameCodec {

    /** Decode a possibly pointer-compressed DNS name at the current buffer position. */
    fun readName(buf: ByteBuffer, raw: ByteArray): String {
        val parts = mutableListOf<String>()
        var jumped = false
        var savedPos = -1

        while (buf.hasRemaining()) {
            val len = buf.get().toInt() and 0xFF
            when {
                len == 0 -> break
                len and 0xC0 == 0xC0 -> {
                    val lo = buf.get().toInt() and 0xFF
                    val offset = ((len and 0x3F) shl 8) or lo
                    if (!jumped) savedPos = buf.position()
                    buf.position(offset)
                    jumped = true
                }
                else -> {
                    val label = ByteArray(len)
                    buf.get(label)
                    parts.add(label.toString(Charsets.UTF_8))
                }
            }
        }
        if (jumped && savedPos >= 0) buf.position(savedPos)
        return parts.joinToString(".")
    }

    /** Encode a dotted DNS name (with or without trailing dot) into buf. */
    fun encodeName(name: String, buf: ByteBuffer) {
        name.trimEnd('.').split('.').forEach { label ->
            buf.put(label.length.toByte())
            buf.put(label.toByteArray(Charsets.UTF_8))
        }
        buf.put(0)
    }
}
