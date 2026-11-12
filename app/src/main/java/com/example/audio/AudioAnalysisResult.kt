package com.example.audio

/**
 * Result of an audio analysis containing ITU-R BS.1770-4 metrics.
 */
data class AudioAnalysisResult(
    val fileName: String,
    val durationMs: Long,
    val sampleRate: Int,
    val channelCount: Int,
    val integratedLufs: Double,
    val truePeakDbtp: Double
)
