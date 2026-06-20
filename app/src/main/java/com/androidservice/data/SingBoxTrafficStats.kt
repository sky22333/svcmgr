package com.androidservice.data

data class SingBoxTrafficStats(
    val uplinkSpeed: Long = 0L,
    val downlinkSpeed: Long = 0L,
    val uplinkTotal: Long = 0L,
    val downlinkTotal: Long = 0L,
    val available: Boolean = false,
) {
    val hasTraffic: Boolean
        get() = available && (uplinkSpeed > 0L || downlinkSpeed > 0L || uplinkTotal > 0L || downlinkTotal > 0L)
}
