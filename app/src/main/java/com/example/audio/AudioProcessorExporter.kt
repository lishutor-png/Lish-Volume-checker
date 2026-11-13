package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.media.AudioFormat as AndroidAudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import de.sciss.jump3r.lowlevel.LameEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import javax.sound.sampled.AudioFormat
import kotlin.math.pow
import kotlin.math.roundToInt

data class ExportResult(
    val fileUri: Uri,
    val displayPath: String,
    val fileName: String,
    val requestedGainDb: Double,
    val appliedGainDb: Double,
    val wasGainCapped: Boolean,
    val originalLufs: Double,
    val finalLufs: Double,
    val originalTruePeak: Double,
    val finalTruePeak: Double,
    val peaksLimitedCount: Long,
    val maxGainReductionDb: Double,
    val bitrateKbps: Int,
    val durationMs: Long,
    val fileSizeBytes: Long
)

class AudioProcessorExporter(private val context: Context) {

    private val audioDecoder = AudioDecoder(context)

    suspend fun processAndExport(
        sourceUri: Uri,
        requestedGainDb: Double,
        appliedGainDb: Double,
        wasGainCapped: Boolean,
        useLimiter: Boolean = true,
        ceilingDbtp: Double = -1.0,
        originalLufs: Double,
        originalTruePeak: Double,
        onProgress: (progress: Float, statusMessage: String) -> Unit
    ): ExportResult = withContext(Dispatchers.Default) {
        val originalFileName = audioDecoder.getFileName(sourceUri)
        val baseName = originalFileName.substringBeforeLast(".")
            .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            .ifBlank { "audio" }
        val gainSign = if (appliedGainDb >= 0.0) {
            "+${String.format(Locale.US, "%.1f", appliedGainDb)}"
        } else {
            String.format(Locale.US, "%.1f", appliedGainDb)
        }
        val newFileName = "${baseName}_Lish_${gainSign}dB.mp3"

        onProgress(0.02f, "Membaca metadata & bitrate lagu...")
        val metadata = Id3TagWriter.extractMetadata(context, sourceUri)

        val pfd = context.contentResolver.openFileDescriptor(sourceUri, "r")
            ?: throw IOException("Tidak dapat membaca file audio asli.")

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var rawOutputStream: OutputStream? = null
        var bufferedOut: BufferedOutputStream? = null
        var targetUri: Uri? = null
        var fallbackFile: File? = null
        var outputFilePath = ""
        var lameEncoder: LameEncoder? = null

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
                throw IllegalArgumentException("Tidak ditemukan trek audio yang dapat diproses.")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("Format codec audio tidak dikenali.")
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            var actualSampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            var actualChannels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            val formatBitrateBps = if (inputFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_BIT_RATE)
            } else null

            val targetBitrateKbps = Id3TagWriter.resolveTargetBitrateKbps(
                metadata.bitrateBps ?: formatBitrateBps
            )

            // Create target destination file in MediaStore (API 29+) or app external Music (API 24-28)
            onProgress(0.05f, "Menyiapkan file MP3 baru...")
            val outputTarget = createOutputFile(newFileName, metadata)
            targetUri = outputTarget.uri
            rawOutputStream = outputTarget.stream
            bufferedOut = BufferedOutputStream(rawOutputStream, 32768)
            fallbackFile = outputTarget.fallbackFile
            outputFilePath = outputTarget.displayPath

            // Write ID3v2 tag at the start of the MP3 file
            val id3Bytes = Id3TagWriter.buildId3v2Tag(metadata)
            var totalBytesWritten = 0L
            if (id3Bytes.isNotEmpty()) {
                bufferedOut.write(id3Bytes)
                totalBytesWritten += id3Bytes.size
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val kTimeoutUs = 10000L
            var isInputEos = false
            var isOutputEos = false
            var pcmEncoding = AndroidAudioFormat.ENCODING_PCM_16BIT

            // Audio pipeline components initialized on first decoded format/frame
            var limiter: AudioLimiter? = null
            var outputLufsMeter: LufsMeter? = null
            var outputTruePeakMeter: TruePeakMeter? = null
            var mp3Buffer: ByteArray? = null
            var pcmScratch = ByteArray(16384)
            var frameInput = DoubleArray(actualChannels)
            var frameOutput = DoubleArray(actualChannels)

            val targetGainLinear = 10.0.pow(appliedGainDb / 20.0)
            var totalDecodedSamples = 0L
            var lastProgress = 0.08f

            fun ensurePipelineInitialized() {
                if (lameEncoder == null) {
                    val audioFormat = AudioFormat(
                        actualSampleRate.toFloat(),
                        16,
                        actualChannels,
                        true,
                        false
                    )
                    val channelMode = if (actualChannels == 1) {
                        LameEncoder.CHANNEL_MODE_MONO
                    } else {
                        LameEncoder.CHANNEL_MODE_STEREO
                    }
                    val encoder = LameEncoder(
                        audioFormat,
                        targetBitrateKbps,
                        channelMode,
                        LameEncoder.QUALITY_HIGHEST,
                        false
                    )
                    lameEncoder = encoder
                    mp3Buffer = ByteArray(encoder.pcmBufferSize.coerceAtLeast(16384))
                    limiter = AudioLimiter(
                        sampleRate = actualSampleRate,
                        channelCount = actualChannels,
                        ceilingDbtp = ceilingDbtp
                    )
                    outputLufsMeter = LufsMeter(actualSampleRate, actualChannels)
                    outputTruePeakMeter = TruePeakMeter(actualChannels)
                    frameInput = DoubleArray(actualChannels)
                    frameOutput = DoubleArray(actualChannels)
                }
            }

            fun encodePcmBytes(pcmBytes: ByteArray, length: Int) {
                val encoder = lameEncoder ?: return
                val outBuf = mp3Buffer ?: return
                val out = bufferedOut ?: return
                var offset = 0
                while (offset < length) {
                    val chunk = minOf(encoder.pcmBufferSize, length - offset)
                    val encoded = encoder.encodeBuffer(pcmBytes, offset, chunk, outBuf)
                    if (encoded > 0) {
                        out.write(outBuf, 0, encoded)
                        totalBytesWritten += encoded
                    }
                    offset += chunk
                }
            }

            onProgress(0.08f, "Menyesuaikan gain & mengenkode MP3 (${targetBitrateKbps} kbps)...")

            while (!isOutputEos) {
                currentCoroutineContext().ensureActive()

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
                                    val decodedProgress = 0.08f + (presentationTimeUs.toFloat() / durationUs.toFloat()) * 0.86f
                                    if (decodedProgress - lastProgress >= 0.02f) {
                                        lastProgress = decodedProgress.coerceIn(0.08f, 0.94f)
                                        val percent = (lastProgress * 100).roundToInt()
                                        onProgress(lastProgress, "Memproses & melindungi audio... $percent%")
                                    }
                                }
                            }
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, kTimeoutUs)
                when {
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            ensurePipelineInitialized()

                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                            val activeLimiter = limiter!!
                            val lufsM = outputLufsMeter!!
                            val tpM = outputTruePeakMeter!!

                            val bytesProduced = processPcmIntoScratch(
                                buffer = outputBuffer,
                                encoding = pcmEncoding,
                                channels = actualChannels,
                                targetGainLinear = targetGainLinear,
                                useLimiter = useLimiter,
                                ceilingLinear = activeLimiter.ceilingLinear,
                                limiter = activeLimiter,
                                outputLufsMeter = lufsM,
                                outputTruePeakMeter = tpM,
                                frameInput = frameInput,
                                frameOutput = frameOutput,
                                scratch = { needed ->
                                    if (pcmScratch.size < needed) {
                                        pcmScratch = ByteArray(needed)
                                    }
                                    pcmScratch
                                }
                            )

                            if (bytesProduced > 0) {
                                totalDecodedSamples += (bytesProduced / (2 * actualChannels))
                                encodePcmBytes(pcmScratch, bytesProduced)
                            }
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

                        if (lameEncoder == null) {
                            actualSampleRate = newSampleRate
                            actualChannels = newChannels
                        }
                    }
                }
            }

            if (totalDecodedSamples == 0L || lameEncoder == null) {
                throw IllegalArgumentException("File audio kosong atau rusak sehingga tidak dapat diekspor.")
            }

            // Flush lookahead tail from limiter so exact track duration is preserved
            if (useLimiter) {
                val activeLimiter = limiter!!
                val lufsM = outputLufsMeter!!
                val tpM = outputTruePeakMeter!!
                val tailBuffer = ByteArray(activeLimiter.lookaheadSamples * actualChannels * 2)
                var tailIdx = 0
                activeLimiter.flush { delayedFrame ->
                    for (ch in 0 until actualChannels) {
                        val sample = delayedFrame[ch]
                        lufsM.processSample(ch, sample)
                        tpM.processSample(ch, sample)
                        val shortVal = (sample * 32767.0).roundToInt().coerceIn(-32768, 32767)
                        tailBuffer[tailIdx++] = (shortVal and 0xFF).toByte()
                        tailBuffer[tailIdx++] = ((shortVal shr 8) and 0xFF).toByte()
                    }
                }
                if (tailIdx > 0) {
                    encodePcmBytes(tailBuffer, tailIdx)
                }
            }

            // Flush LAME encoder frames
            onProgress(0.96f, "Menyelesaikan penulisan file MP3...")
            val encoder = lameEncoder!!
            val outBuf = mp3Buffer!!
            val flushBytes = encoder.encodeFinish(outBuf)
            if (flushBytes > 0) {
                bufferedOut.write(outBuf, 0, flushBytes)
                totalBytesWritten += flushBytes
            }
            bufferedOut.flush()
            bufferedOut.close()
            bufferedOut = null
            rawOutputStream = null

            encoder.close()
            lameEncoder = null

            // Finalize MediaStore pending status
            completeOutputFile(targetUri)

            onProgress(1.0f, "Selesai!")

            val finalLufs = outputLufsMeter!!.calculateIntegratedLufs()
            val finalTruePeak = outputTruePeakMeter!!.calculateTruePeakDbtp()
            val durationMs = durationUs / 1000L

            ExportResult(
                fileUri = targetUri,
                displayPath = outputFilePath,
                fileName = newFileName,
                requestedGainDb = requestedGainDb,
                appliedGainDb = appliedGainDb,
                wasGainCapped = wasGainCapped,
                originalLufs = originalLufs,
                finalLufs = finalLufs,
                originalTruePeak = originalTruePeak,
                finalTruePeak = finalTruePeak,
                peaksLimitedCount = limiter!!.peaksLimitedCount,
                maxGainReductionDb = limiter!!.maxGainReductionDb,
                bitrateKbps = targetBitrateKbps,
                durationMs = durationMs,
                fileSizeBytes = totalBytesWritten
            )
        } catch (e: Exception) {
            try {
                bufferedOut?.close()
            } catch (_: Exception) {}
            try {
                rawOutputStream?.close()
            } catch (_: Exception) {}
            try {
                lameEncoder?.close()
            } catch (_: Exception) {}
            if (fallbackFile != null && fallbackFile.exists()) {
                try {
                    fallbackFile.delete()
                } catch (_: Exception) {}
            } else if (targetUri != null) {
                try {
                    context.contentResolver.delete(targetUri, null, null)
                } catch (_: Exception) {}
            }
            throw e
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
            try {
                bufferedOut?.close()
            } catch (_: Exception) {}
            try {
                rawOutputStream?.close()
            } catch (_: Exception) {}
        }
    }

    private fun processPcmIntoScratch(
        buffer: ByteBuffer,
        encoding: Int,
        channels: Int,
        targetGainLinear: Double,
        useLimiter: Boolean,
        ceilingLinear: Double,
        limiter: AudioLimiter,
        outputLufsMeter: LufsMeter,
        outputTruePeakMeter: TruePeakMeter,
        frameInput: DoubleArray,
        frameOutput: DoubleArray,
        scratch: (neededBytes: Int) -> ByteArray
    ): Int {
        if (channels <= 0) return 0

        if (encoding == AndroidAudioFormat.ENCODING_PCM_FLOAT) {
            val floatBuf = buffer.asFloatBuffer()
            val sampleCount = floatBuf.remaining()
            val frameCount = sampleCount / channels
            val outBytes = scratch(frameCount * channels * 2)
            var byteIdx = 0

            for (f in 0 until frameCount) {
                for (ch in 0 until channels) {
                    frameInput[ch] = floatBuf.get().toDouble().coerceIn(-1.5, 1.5)
                }

                if (useLimiter) {
                    if (limiter.processFrame(frameInput, targetGainLinear, frameOutput)) {
                        for (ch in 0 until channels) {
                            val s = frameOutput[ch]
                            outputLufsMeter.processSample(ch, s)
                            outputTruePeakMeter.processSample(ch, s)
                            val shortVal = (s * 32767.0).roundToInt().coerceIn(-32768, 32767)
                            outBytes[byteIdx++] = (shortVal and 0xFF).toByte()
                            outBytes[byteIdx++] = ((shortVal shr 8) and 0xFF).toByte()
                        }
                    }
                } else {
                    for (ch in 0 until channels) {
                        val s = (frameInput[ch] * targetGainLinear).coerceIn(-ceilingLinear, ceilingLinear)
                        outputLufsMeter.processSample(ch, s)
                        outputTruePeakMeter.processSample(ch, s)
                        val shortVal = (s * 32767.0).roundToInt().coerceIn(-32768, 32767)
                        outBytes[byteIdx++] = (shortVal and 0xFF).toByte()
                        outBytes[byteIdx++] = ((shortVal shr 8) and 0xFF).toByte()
                    }
                }
            }
            return byteIdx
        } else {
            val shortBuf = buffer.asShortBuffer()
            val sampleCount = shortBuf.remaining()
            val frameCount = sampleCount / channels
            val outBytes = scratch(frameCount * channels * 2)
            var byteIdx = 0

            for (f in 0 until frameCount) {
                for (ch in 0 until channels) {
                    frameInput[ch] = shortBuf.get().toDouble() / 32768.0
                }

                if (useLimiter) {
                    if (limiter.processFrame(frameInput, targetGainLinear, frameOutput)) {
                        for (ch in 0 until channels) {
                            val s = frameOutput[ch]
                            outputLufsMeter.processSample(ch, s)
                            outputTruePeakMeter.processSample(ch, s)
                            val shortVal = (s * 32767.0).roundToInt().coerceIn(-32768, 32767)
                            outBytes[byteIdx++] = (shortVal and 0xFF).toByte()
                            outBytes[byteIdx++] = ((shortVal shr 8) and 0xFF).toByte()
                        }
                    }
                } else {
                    for (ch in 0 until channels) {
                        val s = (frameInput[ch] * targetGainLinear).coerceIn(-ceilingLinear, ceilingLinear)
                        outputLufsMeter.processSample(ch, s)
                        outputTruePeakMeter.processSample(ch, s)
                        val shortVal = (s * 32767.0).roundToInt().coerceIn(-32768, 32767)
                        outBytes[byteIdx++] = (shortVal and 0xFF).toByte()
                        outBytes[byteIdx++] = ((shortVal shr 8) and 0xFF).toByte()
                    }
                }
            }
            return byteIdx
        }
    }

    private data class OutputFileTarget(
        val uri: Uri,
        val stream: OutputStream,
        val displayPath: String,
        val fallbackFile: File? = null
    )

    private fun createOutputFile(fileName: String, metadata: AudioMetadata): OutputFileTarget {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.TITLE, metadata.title ?: fileName.substringBeforeLast("."))
                metadata.artist?.let { put(MediaStore.Audio.Media.ARTIST, it) }
                metadata.album?.let { put(MediaStore.Audio.Media.ALBUM, it) }
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/LishVolumeChecker")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Gagal membuat entri file baru di penyimpanan Musik.")
            val stream = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("Gagal membuka stream penulisan file baru.")
            val displayPath = "Musik / LishVolumeChecker / $fileName"
            return OutputFileTarget(uri, stream, displayPath)
        } else {
            // API 24-28: Save to app-accessible external Music directory (zero permissions needed) and expose via FileProvider
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: File(context.filesDir, "Music")
            val musicDir = File(baseDir, "LishVolumeChecker")
            if (!musicDir.exists()) musicDir.mkdirs()
            val file = File(musicDir, fileName)
            val stream = FileOutputStream(file)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            return OutputFileTarget(uri, stream, file.absolutePath, fallbackFile = file)
        }
    }

    private fun completeOutputFile(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }
    }
}
