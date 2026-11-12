package com.example.audio

/**
 * Standard 2nd-order Direct Form II Transposed Biquad IIR Filter.
 * Used for ITU-R BS.1770-4 K-weighting pre-filter and RLB high-pass filter.
 */
class BiquadFilter(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double
) {
    // Filter internal state variables
    private var z1: Double = 0.0
    private var z2: Double = 0.0

    fun process(input: Double): Double {
        val output = b0 * input + z1
        z1 = b1 * input - a1 * output + z2
        z2 = b2 * input - a2 * output
        return output
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }
}
