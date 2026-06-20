package com.androidservice.singbox

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object TrafficFormatter {
    fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0L) return "0 B/s"
        return "${formatSize(bytesPerSecond)}/s"
    }

    fun formatTotal(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        return formatSize(bytes)
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        val value = bytes.toDouble()
        val digitGroups = (ln(value) / ln(1024.0)).toInt().coerceIn(1, units.size)
        val scaled = value / 1024.0.pow(digitGroups.toDouble())
        return String.format(Locale.US, "%.1f %s", scaled, units[digitGroups - 1])
    }
}
