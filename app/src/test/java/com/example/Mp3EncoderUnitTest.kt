package com.example

import de.sciss.jump3r.lowlevel.LameEncoder
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import kotlin.math.PI
import kotlin.math.sin

class Mp3EncoderUnitTest {

    @Test
    fun testEncodeSineWaveWithLameEncoder() {
        val sampleRate = 44100.0f
        val channels = 2
        val format = AudioFormat(sampleRate, 16, channels, true, false)

        val encoder = LameEncoder(
            format,
            192,
            LameEncoder.CHANNEL_MODE_STEREO,
            LameEncoder.QUALITY_HIGHEST,
            false
        )

        val out = ByteArrayOutputStream()
        val numSamples = 1152 * 10
        // Interleaved 16-bit PCM (little endian)
        val pcmBytes = ByteArray(numSamples * 4)

        var idx = 0
        for (i in 0 until numSamples) {
            val sampleVal = (sin(2.0 * PI * 440.0 * i / sampleRate) * 20000.0).toInt().toShort()
            val b0 = (sampleVal.toInt() and 0xFF).toByte()
            val b1 = ((sampleVal.toInt() shr 8) and 0xFF).toByte()

            // Left
            pcmBytes[idx++] = b0
            pcmBytes[idx++] = b1
            // Right
            pcmBytes[idx++] = b0
            pcmBytes[idx++] = b1
        }

        val mp3Buf = ByteArray(encoder.pcmBufferSize)
        var pos = 0
        while (pos < pcmBytes.size) {
            val toWrite = Math.min(encoder.pcmBufferSize, pcmBytes.size - pos)
            val written = encoder.encodeBuffer(pcmBytes, pos, toWrite, mp3Buf)
            if (written > 0) {
                out.write(mp3Buf, 0, written)
            }
            pos += toWrite
        }

        val finishBytes = encoder.encodeFinish(mp3Buf)
        if (finishBytes > 0) {
            out.write(mp3Buf, 0, finishBytes)
        }
        encoder.close()

        val mp3Data = out.toByteArray()
        assertTrue("Output MP3 should not be empty", mp3Data.isNotEmpty())
        println("Generated MP3 bytes: ${mp3Data.size}")
    }
}
