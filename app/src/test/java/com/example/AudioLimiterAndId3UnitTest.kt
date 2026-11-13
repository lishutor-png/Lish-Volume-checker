package com.example

import com.example.audio.AudioLimiter
import com.example.audio.AudioMetadata
import com.example.audio.Id3TagWriter
import com.example.audio.TruePeakMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class AudioLimiterAndId3UnitTest {

    @Test
    fun testLimiterClampsExcessiveGain() {
        val sampleRate = 44100
        val limiter = AudioLimiter(sampleRate = sampleRate, channelCount = 1, ceilingDbtp = -0.5)

        val ceiling = limiter.ceilingLinear
        assertTrue("Ceiling should be around 0.944, got $ceiling", ceiling in 0.94..0.95)

        val targetGainLinear = 2.0
        var maxObservedOutput = 0.0

        for (i in 0 until 500) {
            val input = 0.8 * sin(i.toDouble() * 0.1)
            val output = limiter.process(input, targetGainLinear)
            if (abs(output) > maxObservedOutput) {
                maxObservedOutput = abs(output)
            }
            assertTrue("Output sample must never exceed 1.0 brickwall", abs(output) <= 1.0)
        }

        assertTrue("Limiter must trigger limit count on high input", limiter.peaksLimitedCount > 0)
        assertTrue("Max observed output must be capped around ceiling", maxObservedOutput <= ceiling + 0.005)
    }

    @Test
    fun testStereoLookaheadLimiterPreventsInterSampleClipping() {
        val sampleRate = 44100
        val channels = 2
        val ceilingDbtp = -1.0
        val limiter = AudioLimiter(
            sampleRate = sampleRate,
            channelCount = channels,
            ceilingDbtp = ceilingDbtp,
            lookaheadMs = 2.0
        )
        val truePeakMeter = TruePeakMeter(channels)

        // Generate an inter-sample overshoot signal at fs/4 phase pi/4 and boost by +6 dB (2x)!
        val s = 0.75 / sqrt(2.0) // ~0.53 -> after +6dB = 1.06 sample, ~1.50 inter-sample True Peak!
        val pattern = doubleArrayOf(s, s, -s, -s)
        val inFrame = DoubleArray(channels)
        val outFrame = DoubleArray(channels)
        var emittedFrames = 0

        val totalInputFrames = 2000
        for (i in 0 until totalInputFrames) {
            val sample = pattern[i % 4]
            inFrame[0] = sample
            inFrame[1] = sample * 0.8
            if (limiter.processFrame(inFrame, 2.0, outFrame)) {
                emittedFrames++
                truePeakMeter.processSample(0, outFrame[0])
                truePeakMeter.processSample(1, outFrame[1])
            }
        }
        limiter.flush { delayedFrame ->
            emittedFrames++
            truePeakMeter.processSample(0, delayedFrame[0])
            truePeakMeter.processSample(1, delayedFrame[1])
        }

        // Exact frame count preserved
        assertEquals(totalInputFrames, emittedFrames)

        val measuredTruePeak = truePeakMeter.calculateTruePeakDbtp()
        assertTrue(
            "Stereo lookahead limiter must keep True Peak below 0.0 dBTP (around -1.0 dBTP), got $measuredTruePeak",
            measuredTruePeak <= -0.2
        )
        assertTrue("Limiter should have detected and limited peaks", limiter.peaksLimitedCount > 0)
    }

    @Test
    fun testLimiterTransparentBelowCeiling() {
        val sampleRate = 44100
        val limiter = AudioLimiter(sampleRate = sampleRate, channelCount = 1, ceilingDbtp = -0.5)

        val input = 0.1
        val output = limiter.process(input, 1.0)

        assertEquals(input, output, 0.0001)
        assertEquals(0, limiter.peaksLimitedCount)
    }

    @Test
    fun testId3TagWriterBuildsValidTagAndResolvesBitrate() {
        val metadata = AudioMetadata(
            title = "Lish Master Track",
            artist = "Lish Studio",
            album = "Mastering 2026",
            year = "2026",
            genre = "Pop",
            trackNumber = "1",
            bitrateBps = 320000,
            albumArt = byteArrayOf(1, 2, 3, 4, 5)
        )

        val id3Bytes = Id3TagWriter.buildId3v2Tag(metadata)
        assertTrue("ID3 tag must not be empty", id3Bytes.size > 10)

        assertEquals('I'.code.toByte(), id3Bytes[0])
        assertEquals('D'.code.toByte(), id3Bytes[1])
        assertEquals('3'.code.toByte(), id3Bytes[2])
        assertEquals(0x03.toByte(), id3Bytes[3])

        val tagString = String(id3Bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
        assertTrue("Tag should contain TIT2 frame", tagString.contains("TIT2"))
        assertTrue("Tag should contain TPE1 frame", tagString.contains("TPE1"))
        assertTrue("Tag should contain TALB frame", tagString.contains("TALB"))
        assertTrue("Tag should contain TCON frame", tagString.contains("TCON"))
        assertTrue("Tag should contain APIC frame", tagString.contains("APIC"))

        assertEquals(320, Id3TagWriter.resolveTargetBitrateKbps(320000))
        assertEquals(256, Id3TagWriter.resolveTargetBitrateKbps(256000))
        assertEquals(192, Id3TagWriter.resolveTargetBitrateKbps(192000))
        assertEquals(320, Id3TagWriter.resolveTargetBitrateKbps(null))
    }
}
