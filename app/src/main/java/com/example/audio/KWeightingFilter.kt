package com.example.audio

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * ITU-R BS.1770-4 K-weighting filter for a single audio channel.
 * Consists of:
 * Stage 1: High-shelf pre-filter (simulating head diffraction).
 * Stage 2: RLB (Revised Low-frequency B-weighting) high-pass filter.
 */
class KWeightingFilter(sampleRate: Int) {
    private val preFilter: BiquadFilter
    private val rlbFilter: BiquadFilter

    init {
        val fs = sampleRate.toDouble()

        // Stage 1: High shelf filter
        // Specification: f0 = 1681.974450955533 Hz, Gain = 3.999843853973347 dB, Q = 0.7071752369274193
        val f0Pre = 1681.974450955533
        val gainDb = 3.999843853973347
        val v0 = 10.0.pow(gainDb / 20.0)
        val kPre = tan(PI * f0Pre / fs)
        val k2Pre = kPre * kPre
        val sqrt2 = sqrt(2.0)
        val sqrt2V0 = sqrt(2.0 * v0)
        val a0Pre = 1.0 + sqrt2 * kPre + k2Pre

        val b0Pre = (v0 + sqrt2V0 * kPre + k2Pre) / a0Pre
        val b1Pre = 2.0 * (k2Pre - v0) / a0Pre
        val b2Pre = (v0 - sqrt2V0 * kPre + k2Pre) / a0Pre
        val a1Pre = 2.0 * (k2Pre - 1.0) / a0Pre
        val a2Pre = (1.0 - sqrt2 * kPre + k2Pre) / a0Pre

        preFilter = BiquadFilter(b0Pre, b1Pre, b2Pre, a1Pre, a2Pre)

        // Stage 2: RLB high-pass filter
        // Specification: f0 = 38.13547087602444 Hz, Q = 0.5003270373238773
        val f0Hp = 38.13547087602444
        val qHp = 0.5003270373238773
        val kHp = tan(PI * f0Hp / fs)
        val k2Hp = kHp * kHp
        val denom = 1.0 + kHp / qHp + k2Hp

        val b0Hp = 1.0 / denom
        val b1Hp = -2.0 / denom
        val b2Hp = 1.0 / denom
        val a1Hp = 2.0 * (k2Hp - 1.0) / denom
        val a2Hp = (1.0 - kHp / qHp + k2Hp) / denom

        rlbFilter = BiquadFilter(b0Hp, b1Hp, b2Hp, a1Hp, a2Hp)
    }

    /**
     * Pass raw sample through Stage 1 then Stage 2.
     */
    fun process(sample: Double): Double {
        val stage1 = preFilter.process(sample)
        return rlbFilter.process(stage1)
    }

    fun reset() {
        preFilter.reset()
        rlbFilter.reset()
    }
}
