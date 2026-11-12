package com.example.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Measures True Peak according to ITU-R BS.1770-4 Annex 2.
 * Uses 4x oversampling cubic Hermite / Catmull-Rom spline interpolation and analytical extremum
 * search to detect true inter-sample peaks that exceed the digital full scale.
 */
class TruePeakMeter(val channelCount: Int) {
    // Keep last 4 samples for each channel to perform spline interpolation
    private val buffer = Array(channelCount) { DoubleArray(4) }
    private val sampleCount = LongArray(channelCount) { 0L }

    private var maxPeakLinear: Double = 0.0

    /**
     * Feed a single normalized sample in [-1.0, 1.0] for the given channel.
     */
    fun processSample(channelIndex: Int, sample: Double) {
        val absSample = abs(sample)
        if (absSample > maxPeakLinear) {
            maxPeakLinear = absSample
        }

        val chBuf = buffer[channelIndex]
        chBuf[0] = chBuf[1]
        chBuf[1] = chBuf[2]
        chBuf[2] = chBuf[3]
        chBuf[3] = sample

        sampleCount[channelIndex]++

        if (sampleCount[channelIndex] >= 4) {
            // Cubic Hermite spline coefficients between sample[1] (t=0) and sample[2] (t=1)
            val p0 = chBuf[0]
            val p1 = chBuf[1]
            val p2 = chBuf[2]
            val p3 = chBuf[3]

            val a = -0.5 * p0 + 1.5 * p1 - 1.5 * p2 + 0.5 * p3
            val b = p0 - 2.5 * p1 + 2.0 * p2 - 0.5 * p3
            val c = -0.5 * p0 + 0.5 * p2
            val d = p1

            // 4x oversampling points: t = 0.25, 0.5, 0.75
            val t1 = 0.25
            val v1 = abs(((a * t1 + b) * t1 + c) * t1 + d)

            val t2 = 0.5
            val v2 = abs(((a * t2 + b) * t2 + c) * t2 + d)

            val t3 = 0.75
            val v3 = abs(((a * t3 + b) * t3 + c) * t3 + d)

            var localPeak = max(v1, max(v2, v3))

            // Analytical extremum check within (0, 1) where derivative 3at^2 + 2bt + c = 0
            if (abs(a) > 1e-9) {
                val discriminant = 4.0 * b * b - 12.0 * a * c
                if (discriminant >= 0.0) {
                    val sqrtD = sqrt(discriminant)
                    val r1 = (-2.0 * b + sqrtD) / (6.0 * a)
                    val r2 = (-2.0 * b - sqrtD) / (6.0 * a)

                    if (r1 in 0.0..1.0) {
                        val vr1 = abs(((a * r1 + b) * r1 + c) * r1 + d)
                        if (vr1 > localPeak) localPeak = vr1
                    }
                    if (r2 in 0.0..1.0) {
                        val vr2 = abs(((a * r2 + b) * r2 + c) * r2 + d)
                        if (vr2 > localPeak) localPeak = vr2
                    }
                }
            }

            if (localPeak > maxPeakLinear) {
                maxPeakLinear = localPeak
            }
        }
    }

    /**
     * Compute final True Peak in dBTP (dB True Peak).
     * 1.0 linear = 0.0 dBTP. Values > 0.0 dBTP indicate true peak clipping/inter-sample overshoot.
     */
    fun calculateTruePeakDbtp(): Double {
        return if (maxPeakLinear > 0.00000001) {
            20.0 * log10(maxPeakLinear)
        } else {
            -99.9
        }
    }

    fun getMaxLinearPeak(): Double = maxPeakLinear
}
