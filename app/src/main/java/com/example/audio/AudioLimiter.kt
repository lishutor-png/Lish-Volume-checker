package com.example.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Studio-grade stereo-linked True Peak lookahead limiter.
 *
 * Features:
 * - Multi-channel stereo-linked gain reduction (preserves stereo imaging).
 * - 4x oversampled cubic Hermite True Peak estimation so inter-sample peaks are caught.
 * - Lookahead delay buffer with smooth attack ramp and exponential release to avoid harsh clipping distortion.
 * - Brickwall safety clamp to guarantee PCM never exceeds digital full scale.
 */
class AudioLimiter(
    val sampleRate: Int,
    val channelCount: Int = 2,
    ceilingDbtp: Double = -1.0,
    lookaheadMs: Double = 2.0,
    releaseTimeSec: Double = 0.060
) {
    // Linear threshold derived from ceiling in dBTP
    val ceilingLinear: Double = 10.0.pow(ceilingDbtp / 20.0)

    val lookaheadSamples: Int = (sampleRate * (lookaheadMs / 1000.0)).roundToInt().coerceAtLeast(1)

    // Delay ring buffer storing amplified samples [channel][index]
    private val delayBuffer = Array(channelCount) { DoubleArray(lookaheadSamples) }
    private var delayIndex = 0
    private var framesBuffered = 0L

    // 4-sample history per channel for 4x oversampled True Peak estimation
    private val splineHistory = Array(channelCount) { DoubleArray(4) }
    private var splineSampleCount = 0L

    // Sliding window of frame peak gains inside lookahead buffer
    private val frameRequiredGain = DoubleArray(lookaheadSamples) { 1.0 }

    // Exponential attack and release coefficients
    private val attackCoeff: Double = 1.0 - exp(-2.2 / lookaheadSamples)
    private val releaseCoeff: Double = 1.0 - exp(-1.0 / (sampleRate * releaseTimeSec))

    // Current smoothed envelope gain [0.0 .. 1.0]
    private var envelopeGain: Double = 1.0

    // Statistics
    var peaksLimitedCount: Long = 0L
        private set
    var maxGainReductionDb: Double = 0.0
        private set

    /**
     * Convenience single-sample processor for mono / legacy unit usage.
     */
    fun process(inputSample: Double, targetGainLinear: Double): Double {
        val amplified = inputSample * targetGainLinear
        val peak = max(abs(amplified), estimateOversampledPeakForChannel(0, amplified))

        val targetAttenuation = if (peak > ceilingLinear) {
            peaksLimitedCount++
            ceilingLinear / peak
        } else {
            1.0
        }

        if (targetAttenuation < envelopeGain) {
            envelopeGain = targetAttenuation
        } else {
            envelopeGain += (targetAttenuation - envelopeGain) * releaseCoeff
        }

        val gainReductionDb = if (envelopeGain < 1.0) 20.0 * log10(envelopeGain) else 0.0
        if (gainReductionDb < maxGainReductionDb) {
            maxGainReductionDb = gainReductionDb
        }

        // Strict ceiling guard after release smoothing
        val limited = (amplified * envelopeGain).coerceIn(-ceilingLinear, ceilingLinear)
        return max(-1.0, min(1.0, limited))
    }

    /**
     * Processes one multi-channel audio frame with stereo-linked lookahead and True Peak limiting.
     *
     * @param inputFrame Amplified or raw samples of size [channelCount].
     * @param targetGainLinear Linear gain multiplier.
     * @param outFrame Output array of size [channelCount] receiving the delayed, limited frame.
     * @return true if a valid delayed frame was written to [outFrame], false while initial lookahead buffer fills.
     */
    fun processFrame(
        inputFrame: DoubleArray,
        targetGainLinear: Double,
        outFrame: DoubleArray
    ): Boolean {
        var maxFramePeak = 0.0
        for (ch in 0 until channelCount) {
            val amplified = inputFrame[ch] * targetGainLinear
            delayBuffer[ch][delayIndex] = amplified

            val interSamplePeak = estimateOversampledPeakForChannel(ch, amplified)
            val chPeak = max(abs(amplified), interSamplePeak)
            if (chPeak > maxFramePeak) {
                maxFramePeak = chPeak
            }
        }
        splineSampleCount++

        val requiredGain = if (maxFramePeak > ceilingLinear) {
            peaksLimitedCount++
            (ceilingLinear / maxFramePeak)
        } else {
            1.0
        }
        frameRequiredGain[delayIndex] = requiredGain

        // Minimum required gain across the entire lookahead horizon
        var minLookaheadGain = 1.0
        for (i in 0 until lookaheadSamples) {
            if (frameRequiredGain[i] < minLookaheadGain) {
                minLookaheadGain = frameRequiredGain[i]
            }
        }

        // Smooth attack toward upcoming peak, smooth release when peaks pass
        if (minLookaheadGain < envelopeGain) {
            envelopeGain += (minLookaheadGain - envelopeGain) * attackCoeff
            if (envelopeGain < minLookaheadGain) {
                envelopeGain = minLookaheadGain
            }
        } else {
            envelopeGain += (minLookaheadGain - envelopeGain) * releaseCoeff
        }

        val nextIdx = (delayIndex + 1) % lookaheadSamples
        val hasOutput = framesBuffered >= lookaheadSamples

        if (hasOutput) {
            // Check instantaneous peak of the emerging frame so envelope never lets any sample/inter-sample exceed ceiling
            var emergingMax = 0.0
            for (ch in 0 until channelCount) {
                val sampleAbs = abs(delayBuffer[ch][nextIdx])
                if (sampleAbs > emergingMax) emergingMax = sampleAbs
            }
            val strictCapGain = if (emergingMax * envelopeGain > ceilingLinear && emergingMax > 0.0) {
                ceilingLinear / emergingMax
            } else {
                envelopeGain
            }

            val appliedGain = min(envelopeGain, strictCapGain)
            val grDb = if (appliedGain < 1.0) 20.0 * log10(appliedGain) else 0.0
            if (grDb < maxGainReductionDb) {
                maxGainReductionDb = grDb
            }

            for (ch in 0 until channelCount) {
                val outVal = delayBuffer[ch][nextIdx] * appliedGain
                outFrame[ch] = max(-1.0, min(1.0, outVal))
            }
        }

        delayIndex = nextIdx
        framesBuffered++
        return hasOutput
    }

    /**
     * Flushes remaining lookahead samples at end-of-stream to preserve exact track duration.
     */
    fun flush(onFrame: (DoubleArray) -> Unit) {
        val remaining = min(lookaheadSamples.toLong(), framesBuffered).toInt()
        val zeroFrame = DoubleArray(channelCount)
        val outFrame = DoubleArray(channelCount)
        for (i in 0 until remaining) {
            if (processFrame(zeroFrame, 1.0, outFrame)) {
                onFrame(outFrame)
            }
        }
    }

    private fun estimateOversampledPeakForChannel(ch: Int, sample: Double): Double {
        val h = splineHistory[ch]
        h[0] = h[1]
        h[1] = h[2]
        h[2] = h[3]
        h[3] = sample

        if (splineSampleCount < 3) {
            return abs(sample)
        }

        val p0 = h[0]
        val p1 = h[1]
        val p2 = h[2]
        val p3 = h[3]

        val a = -0.5 * p0 + 1.5 * p1 - 1.5 * p2 + 0.5 * p3
        val b = p0 - 2.5 * p1 + 2.0 * p2 - 0.5 * p3
        val c = -0.5 * p0 + 0.5 * p2
        val d = p1

        val v1 = abs(((a * 0.25 + b) * 0.25 + c) * 0.25 + d)
        val v2 = abs(((a * 0.50 + b) * 0.50 + c) * 0.50 + d)
        val v3 = abs(((a * 0.75 + b) * 0.75 + c) * 0.75 + d)
        var peak = max(v1, max(v2, v3))

        if (abs(a) > 1e-9) {
            val disc = 4.0 * b * b - 12.0 * a * c
            if (disc >= 0.0) {
                val sqrtD = sqrt(disc)
                val r1 = (-2.0 * b + sqrtD) / (6.0 * a)
                val r2 = (-2.0 * b - sqrtD) / (6.0 * a)
                if (r1 in 0.0..1.0) {
                    peak = max(peak, abs(((a * r1 + b) * r1 + c) * r1 + d))
                }
                if (r2 in 0.0..1.0) {
                    peak = max(peak, abs(((a * r2 + b) * r2 + c) * r2 + d))
                }
            }
        }
        return peak
    }

    fun reset() {
        envelopeGain = 1.0
        peaksLimitedCount = 0L
        maxGainReductionDb = 0.0
        delayIndex = 0
        framesBuffered = 0L
        splineSampleCount = 0L
        for (ch in 0 until channelCount) {
            delayBuffer[ch].fill(0.0)
            splineHistory[ch].fill(0.0)
        }
        frameRequiredGain.fill(1.0)
    }
}
