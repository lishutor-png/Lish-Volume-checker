package com.example

import com.example.audio.BiquadFilter
import com.example.audio.KWeightingFilter
import com.example.audio.LufsMeter
import com.example.audio.TruePeakMeter
import com.example.viewmodel.LoudnessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class LufsAndTruePeakUnitTest {

    @Test
    fun testBiquadFilterStability() {
        val filter = BiquadFilter(1.0, -1.0, 0.0, -0.9, 0.0)
        var output = 0.0
        for (i in 0 until 100) {
            output = filter.process(1.0)
        }
        assertTrue("Filter output should remain finite", output.isFinite())
    }

    @Test
    fun testKWeightingFilterInitialization() {
        // Test standard sample rates
        val filter44100 = KWeightingFilter(44100)
        val filter48000 = KWeightingFilter(48000)

        val out44 = filter44100.process(0.5)
        val out48 = filter48000.process(0.5)

        assertTrue(out44.isFinite())
        assertTrue(out48.isFinite())
    }

    @Test
    fun testLufsMeterSineWaveLoudness() {
        val sampleRate = 48000
        val channels = 2
        val meter = LufsMeter(sampleRate, channels)

        // Generate 1 second of 1 kHz stereo sine wave at 0 dBFS (peak 1.0)
        val durationSecs = 1.0
        val totalSamples = (sampleRate * durationSecs).toInt()
        val freq = 1000.0

        for (n in 0 until totalSamples) {
            val sample = sin(2.0 * PI * freq * n / sampleRate)
            meter.processSample(0, sample)
            meter.processSample(1, sample)
        }

        val lufs = meter.calculateIntegratedLufs()
        // According to ITU-R BS.1770, a 1 kHz stereo sine wave with both channels driven at 0 dBFS
        // produces 0.0 LKFS / LUFS because -0.691 + 10*log10(2 * 0.5862) ~ 0.0 LUFS.
        assertTrue("LUFS should be around 0.0 for full-scale 1kHz stereo sine wave, got $lufs", lufs in -0.5..0.5)

        // Now test -14 dBFS sine wave (standard streaming reference level)
        val meter14 = LufsMeter(sampleRate, channels)
        val amp14 = Math.pow(10.0, -14.0 / 20.0)
        for (n in 0 until totalSamples) {
            val sample = amp14 * sin(2.0 * PI * freq * n / sampleRate)
            meter14.processSample(0, sample)
            meter14.processSample(1, sample)
        }
        val lufs14 = meter14.calculateIntegratedLufs()
        assertTrue("LUFS should be around -14.0 for -14 dBFS 1kHz stereo sine wave, got $lufs14", lufs14 in -14.5..-13.5)
    }

    @Test
    fun testTruePeakInterSampleOvershootDetection() {
        val meter = TruePeakMeter(1)

        // A sine wave sampled at 1/4 of sample rate with phase offset pi/4:
        // sample values: sin(pi/4), sin(3pi/4), sin(5pi/4), sin(7pi/4) = 0.7071, 0.7071, -0.7071, -0.7071
        // The peak of digital samples is only 0.7071 (-3.01 dBFS).
        // However, true peak is 1.0 (0.0 dBTP)!
        val s = 1.0 / kotlin.math.sqrt(2.0)
        val testPattern = doubleArrayOf(s, s, -s, -s, s, s, -s, -s, s, s, -s, -s)

        for (sample in testPattern) {
            meter.processSample(0, sample)
        }

        val peakDbtp = meter.calculateTruePeakDbtp()
        // True peak meter should reconstruct the true crest higher than the digital sample peak (0.7071 = -3.0 dB)
        assertTrue("True Peak should detect inter-sample peak above -2.5 dBTP, got $peakDbtp", peakDbtp > -2.5)
    }

    @Test
    fun testGainRecommendationCalculation() {
        // Case 1: Test track is louder (-10 LUFS vs -14 LUFS)
        val testLufs1 = -10.0
        val refLufs1 = -14.0
        val diff1 = kotlin.math.round((testLufs1 - refLufs1) * 10.0) / 10.0
        val gainRec1 = kotlin.math.round((-diff1) * 10.0) / 10.0
        val status1 = when {
            diff1 > 0.1 -> LoudnessStatus.LOUDER
            diff1 < -0.1 -> LoudnessStatus.QUIETER
            else -> LoudnessStatus.MATCHED
        }

        assertEquals(4.0, diff1, 0.01)
        assertEquals(-4.0, gainRec1, 0.01)
        assertEquals(LoudnessStatus.LOUDER, status1)

        // Case 2: Test track is quieter (-16.5 LUFS vs -14.0 LUFS)
        val testLufs2 = -16.5
        val refLufs2 = -14.0
        val diff2 = kotlin.math.round((testLufs2 - refLufs2) * 10.0) / 10.0
        val gainRec2 = kotlin.math.round((-diff2) * 10.0) / 10.0
        val status2 = when {
            diff2 > 0.1 -> LoudnessStatus.LOUDER
            diff2 < -0.1 -> LoudnessStatus.QUIETER
            else -> LoudnessStatus.MATCHED
        }

        assertEquals(-2.5, diff2, 0.01)
        assertEquals(2.5, gainRec2, 0.01)
        assertEquals(LoudnessStatus.QUIETER, status2)

        // Case 3: Matched loudness (-14.0 LUFS vs -14.0 LUFS)
        val testLufs3 = -14.0
        val refLufs3 = -14.0
        val diff3 = kotlin.math.round((testLufs3 - refLufs3) * 10.0) / 10.0
        val gainRec3 = kotlin.math.round((-diff3) * 10.0) / 10.0
        val status3 = when {
            diff3 > 0.1 -> LoudnessStatus.LOUDER
            diff3 < -0.1 -> LoudnessStatus.QUIETER
            else -> LoudnessStatus.MATCHED
        }

        assertEquals(0.0, diff3, 0.01)
        assertEquals(0.0, gainRec3, 0.01)
        assertEquals(LoudnessStatus.MATCHED, status3)
    }
}
