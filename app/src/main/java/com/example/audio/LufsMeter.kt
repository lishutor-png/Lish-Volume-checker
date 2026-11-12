package com.example.audio

import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Calculates Integrated Loudness (LUFS) according to ITU-R BS.1770-4 and EBU R128.
 *
 * Algorithm details:
 * - 400 ms sliding window with 75% overlap (100 ms hop).
 * - Per-channel K-weighting filtering.
 * - Absolute gate at -70 LUFS.
 * - Relative gate at -10 LU below the average loudness of blocks above -70 LUFS.
 * - Memory efficient: only 400ms buffer is held in memory, only summary block energies are retained.
 */
class LufsMeter(
    val sampleRate: Int,
    val channelCount: Int
) {
    // Block size: 400 ms, Hop size: 100 ms
    val blockSize: Int = (0.4 * sampleRate).roundToInt()
    val hopSize: Int = (0.1 * sampleRate).roundToInt()

    private val filters = Array(channelCount) { KWeightingFilter(sampleRate) }

    // Channel weighting according to ITU-R BS.1770
    // Channel 0: Left (1.0), Channel 1: Right (1.0), Channel 2: Center (1.0)
    private val channelWeights = DoubleArray(channelCount) { i ->
        when (i) {
            0, 1 -> 1.0 // L, R
            2 -> 1.0    // C
            4, 5 -> 1.41 // Ls, Rs
            else -> 1.0
        }
    }

    // Circular buffers for squared K-weighted samples per channel (size = blockSize)
    private val ringBuffers = Array(channelCount) { DoubleArray(blockSize) }
    private val ringSums = DoubleArray(channelCount) { 0.0 }
    private var ringIndex = 0
    private var samplesProcessedInHop = 0
    private var totalSamplesProcessed = 0L

    // Stored block energies: blockEnergies[j][i] = z_{i, j}
    // For a typical 4-minute song, there are ~2400 blocks. 2400 * 2 doubles = ~38 KB of RAM!
    private val blockEnergies = mutableListOf<DoubleArray>()

    /**
     * Feed decoded audio samples.
     * @param channelIndex 0 for Left, 1 for Right, etc.
     * @param sample normalized audio sample in [-1.0, 1.0]
     */
    fun processSample(channelIndex: Int, sample: Double) {
        val filtered = filters[channelIndex].process(sample)
        val squared = filtered * filtered

        // Subtract the old sample that is being overwritten from running sum
        val oldSquared = ringBuffers[channelIndex][ringIndex]
        ringSums[channelIndex] += (squared - oldSquared)
        ringBuffers[channelIndex][ringIndex] = squared

        // If this is the last channel of this sample frame, advance ring buffer and hop counter
        if (channelIndex == channelCount - 1) {
            ringIndex = (ringIndex + 1) % blockSize
            samplesProcessedInHop++
            totalSamplesProcessed++

            // When a hop interval has elapsed and we have accumulated at least one full block
            if (samplesProcessedInHop >= hopSize) {
                samplesProcessedInHop = 0
                if (totalSamplesProcessed >= blockSize) {
                    val energies = DoubleArray(channelCount)
                    for (ch in 0 until channelCount) {
                        energies[ch] = ringSums[ch] / blockSize.toDouble()
                    }
                    blockEnergies.add(energies)
                }
            }
        }
    }

    /**
     * Compute final Integrated LUFS.
     * Returns -70.0 if audio is entirely silent.
     */
    fun calculateIntegratedLufs(): Double {
        if (blockEnergies.isEmpty()) {
            return -70.0
        }

        // Calculate loudness for each block:
        // L_j = -0.691 + 10 * log10( sum_i (G_i * z_{i, j}) )
        val blockLoudness = DoubleArray(blockEnergies.size)
        val absolutePassedIndices = mutableListOf<Int>()

        for (j in blockEnergies.indices) {
            val energies = blockEnergies[j]
            var sumGz = 0.0
            for (ch in 0 until channelCount) {
                sumGz += channelWeights[ch] * energies[ch]
            }

            val lj = if (sumGz > 0.0) {
                -0.691 + 10.0 * log10(sumGz)
            } else {
                -100.0
            }
            blockLoudness[j] = lj

            // Absolute threshold: -70 LUFS
            if (lj > -70.0) {
                absolutePassedIndices.add(j)
            }
        }

        if (absolutePassedIndices.isEmpty()) {
            return -70.0
        }

        // Compute average energy of blocks passing absolute threshold
        val avgEnergyAbs = DoubleArray(channelCount) { 0.0 }
        for (idx in absolutePassedIndices) {
            val energies = blockEnergies[idx]
            for (ch in 0 until channelCount) {
                avgEnergyAbs[ch] += energies[ch]
            }
        }
        var sumAbsGz = 0.0
        for (ch in 0 until channelCount) {
            avgEnergyAbs[ch] /= absolutePassedIndices.size.toDouble()
            sumAbsGz += channelWeights[ch] * avgEnergyAbs[ch]
        }

        val lufsAbs = if (sumAbsGz > 0.0) {
            -0.691 + 10.0 * log10(sumAbsGz)
        } else {
            -70.0
        }

        // Relative threshold: Gamma_r = lufsAbs - 10.0 LU
        val relativeThreshold = lufsAbs - 10.0

        // Select blocks passing relative threshold
        val relativePassedIndices = mutableListOf<Int>()
        for (idx in absolutePassedIndices) {
            if (blockLoudness[idx] > relativeThreshold) {
                relativePassedIndices.add(idx)
            }
        }

        if (relativePassedIndices.isEmpty()) {
            return -70.0
        }

        // Final integrated loudness computation over relative gated blocks
        val avgEnergyRel = DoubleArray(channelCount) { 0.0 }
        for (idx in relativePassedIndices) {
            val energies = blockEnergies[idx]
            for (ch in 0 until channelCount) {
                avgEnergyRel[ch] += energies[ch]
            }
        }

        var sumRelGz = 0.0
        for (ch in 0 until channelCount) {
            avgEnergyRel[ch] /= relativePassedIndices.size.toDouble()
            sumRelGz += channelWeights[ch] * avgEnergyRel[ch]
        }

        return if (sumRelGz > 0.0) {
            -0.691 + 10.0 * log10(sumRelGz)
        } else {
            -70.0
        }
    }
}
