package com.example.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object AudioFormatUtils {

    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSecs = (ms / 1000).toInt()
        val minutes = totalSecs / 60
        val seconds = totalSecs % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    fun formatSampleRate(hz: Int): String {
        return if (hz >= 1000) {
            String.format(Locale.US, "%.1f kHz", hz / 1000.0)
        } else {
            "$hz Hz"
        }
    }

    fun formatChannels(channels: Int): String {
        return when (channels) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1 Surround"
            else -> "$channels Ch"
        }
    }

    fun formatLufs(lufs: Double): String {
        return if (lufs <= -70.0) {
            "-∞ LUFS"
        } else {
            String.format(Locale.US, "%.1f LUFS", lufs)
        }
    }

    fun formatDbtp(dbtp: Double): String {
        return if (dbtp <= -90.0) {
            "-∞ dBTP"
        } else {
            val sign = if (dbtp > 0.0) "+" else ""
            String.format(Locale.US, "%s%.1f dBTP", sign, dbtp)
        }
    }

    fun formatGain(gainDb: Double): String {
        return if (abs(gainDb) < 0.05) {
            "0.0 dB"
        } else {
            val sign = if (gainDb > 0.0) "+" else ""
            String.format(Locale.US, "%s%.1f dB", sign, gainDb)
        }
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
        return sdf.format(Date(timestamp))
    }
}
