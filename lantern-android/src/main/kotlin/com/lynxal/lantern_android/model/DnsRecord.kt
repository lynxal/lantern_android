package com.lynxal.lantern_android.model

internal sealed class DnsRecord {
    data class Ptr(val name: String, val target: String, val ttl: Int) : DnsRecord()
    data class Srv(val name: String, val target: String, val port: Int) : DnsRecord()
    data class Txt(val name: String, val rawAttributes: List<ByteArray>) : DnsRecord()
    data class A(val name: String, val address: String) : DnsRecord()
}
