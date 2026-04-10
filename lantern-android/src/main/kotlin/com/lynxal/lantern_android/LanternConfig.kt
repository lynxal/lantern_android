package com.lynxal.lantern_android

data class LanternConfig(
    val serviceType: String,         // e.g. "_canvas._tcp.local."
    val queryIntervalMs: Long = 0L,  // 0 = query once on start; >0 = repeat
    val socketTimeoutMs: Int = 5000,
) {
    init {
        require(serviceType.endsWith(".")) { "serviceType must end with '.'" }
    }
}
