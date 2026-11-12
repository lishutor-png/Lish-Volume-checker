package com.example.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioDecoder(private val context: Context) {

    /**
     * Resolves the real file name of an Android content URI.
     */
    fun getFileName(uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            name = cursor.getString(index)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        if (name == null) {
            name = uri.lastPathSegment ?: "audio.mp3"
        }
        return name ?: "audio.mp3"
    }

    /**
     * Decodes an entire MP3/audio file and calculates ITU-R BS.1770-4 LUFS and True Peak.
     * Streams buffer-by-buffer for minimal RAM footprint.
     *
     * @param uri The URI of the audio file to analyze.
     * @param onProgress Callback receiving progress from 0.0f to 1.0f.
     * @return AudioAnalysisResult with Integrated LUFS and True Peak dBTP.
     */
    suspend fun analyzeAudio(
        uri: Uri,
        onProgress: (Float) -> Unit
    ): AudioAnalysisResult = withContext(Dispatchers.Default) {
        val fileName = getFileName(uri)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Tidak dapat membuka file audio '$fileName'. File mungkin telah dipindahkan atau izin akses ditolak.")

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(pfd.fileDescriptor)

            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || inputFormat == null) {
                throw IllegalArgumentException("Format file tidak didukung atau file '$fileName' tidak berisi trek audio yang valid.")
            }

            extractor.selectTrack(audioTrackIndex)

            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("Tidak dapat mendeteksi jenis codec audio untuk '$fileName'.")

            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            val initialSampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }

            val initialChannels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }

            var actualSampleRate = initialSampleRate
            var actualChannels = initialChannels
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            var lufsMeter = LufsMeter(actualSampleRate, actualChannels)
            var truePeakMeter = TruePeakMeter(actualChannels)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val kTimeoutUs = 10000L
            var isInputEos = false
            var isOutputEos = false
            var lastReportedProgress = 0f

            while (!isOutputEos) {
                currentCoroutineContext().ensureActive()

                // Feed input buffers to decoder
                if (!isInputEos) {
                    val inputBufferIndex = codec.dequeueInputBuffer(kTimeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                isInputEos = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    presentationTimeUs,
                                    0
                                )
                                extractor.advance()

                                if (durationUs > 0) {
                                    val prog = (presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 0.98f)
                                    if (prog - lastReportedProgress >= 0.02f) {
                                        lastReportedProgress = prog
                                        onProgress(prog)
                                    }
                                }
                            }
                        }
                    }
                }

                // Drain decoded PCM output buffers
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, kTimeoutUs)
                when {
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                            processPcmBuffer(
                                outputBuffer,
                                pcmEncoding,
                                actualChannels,
                                lufsMeter,
                                truePeakMeter
                            )
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isOutputEos = true
                        }
                    }

                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        val newSampleRate = if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        } else actualSampleRate

                        val newChannels = if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        } else actualChannels

                        if (newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }

                        // Reinitialize meters if sample rate or channels changed
                        if (newSampleRate != actualSampleRate || newChannels != actualChannels) {
                            actualSampleRate = newSampleRate
                            actualChannels = newChannels
                            lufsMeter = LufsMeter(actualSampleRate, actualChannels)
                            truePeakMeter = TruePeakMeter(actualChannels)
                        }
                    }
                }
            }

            onProgress(1.0f)

            val integratedLufs = lufsMeter.calculateIntegratedLufs()
            val truePeak = truePeakMeter.calculateTruePeakDbtp()
            val durationMs = durationUs / 1000L

            AudioAnalysisResult(
                fileName = fileName,
                durationMs = durationMs,
                sampleRate = actualSampleRate,
                channelCount = actualChannels,
                integratedLufs = integratedLufs,
                truePeakDbtp = truePeak
            )
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {}
            try {
                codec?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
            try {
                pfd.close()
            } catch (_: Exception) {}
        }
    }

    private fun processPcmBuffer(
        buffer: ByteBuffer,
        encoding: Int,
        channels: Int,
        lufsMeter: LufsMeter,
        truePeakMeter: TruePeakMeter
    ) {
        if (channels <= 0) return

        var channelIndex = 0

        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val floatBuffer = buffer.asFloatBuffer()
            while (floatBuffer.hasRemaining()) {
                val sample = floatBuffer.get().toDouble().coerceIn(-1.5, 1.5)
                lufsMeter.processSample(channelIndex, sample)
                truePeakMeter.processSample(channelIndex, sample)

                channelIndex = (channelIndex + 1) % channels
            }
        } else {
            // Default 16-bit signed PCM
            val shortBuffer = buffer.asShortBuffer()
            while (shortBuffer.hasRemaining()) {
                val sample = (shortBuffer.get().toDouble() / 32768.0).coerceIn(-1.5, 1.5)
                lufsMeter.processSample(channelIndex, sample)
                truePeakMeter.processSample(channelIndex, sample)

                channelIndex = (channelIndex + 1) % channels
            }
        }
    }
}
