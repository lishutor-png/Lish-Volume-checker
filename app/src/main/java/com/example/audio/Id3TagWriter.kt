package com.example.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

data class AudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val trackNumber: String? = null,
    val bitrateBps: Int? = null,
    val albumArt: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioMetadata) return false
        return title == other.title &&
                artist == other.artist &&
                album == other.album &&
                year == other.year &&
                genre == other.genre &&
                trackNumber == other.trackNumber &&
                bitrateBps == other.bitrateBps &&
                albumArt?.contentEquals(other.albumArt ?: byteArrayOf()) ?: (other.albumArt == null)
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (year?.hashCode() ?: 0)
        result = 31 * result + (genre?.hashCode() ?: 0)
        result = 31 * result + (trackNumber?.hashCode() ?: 0)
        result = 31 * result + (bitrateBps ?: 0)
        result = 31 * result + (albumArt?.contentHashCode() ?: 0)
        return result
    }
}

object Id3TagWriter {

    /**
     * Extracts existing metadata (Title, Artist, Album, Year, Genre, Track, Bitrate, Cover Art) from an audio URI.
     */
    fun extractMetadata(context: Context, uri: Uri): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            val bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            val art = retriever.embeddedPicture

            AudioMetadata(
                title = title,
                artist = artist,
                album = album,
                year = year,
                genre = genre,
                trackNumber = trackNumber,
                bitrateBps = bitrateBps,
                albumArt = art
            )
        } catch (_: Exception) {
            AudioMetadata()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Maps source bitrate in bps to the highest matching standard MP3 CBR bitrate (192..320 kbps).
     */
    fun resolveTargetBitrateKbps(sourceBitrateBps: Int?): Int {
        if (sourceBitrateBps == null || sourceBitrateBps <= 0) return 320
        val kbps = sourceBitrateBps / 1000
        return when {
            kbps >= 288 -> 320
            kbps >= 240 -> 256
            kbps >= 208 -> 224
            kbps >= 176 -> 192
            kbps >= 144 -> 160
            else -> 128
        }
    }

    /**
     * Builds an ID3v2.3 tag byte array from the metadata.
     */
    fun buildId3v2Tag(metadata: AudioMetadata): ByteArray {
        val framesOut = ByteArrayOutputStream()

        // Text frames helper: UTF-16 with BOM
        metadata.title?.let { writeTextFrame(framesOut, "TIT2", it) }
        metadata.artist?.let { writeTextFrame(framesOut, "TPE1", it) }
        metadata.album?.let { writeTextFrame(framesOut, "TALB", it) }
        metadata.year?.let { writeTextFrame(framesOut, "TYER", it) }
        metadata.genre?.let { writeTextFrame(framesOut, "TCON", it) }
        metadata.trackNumber?.let { writeTextFrame(framesOut, "TRCK", it) }

        // Cover art frame (APIC)
        metadata.albumArt?.let { artBytes ->
            writeApicFrame(framesOut, artBytes)
        }

        val framesData = framesOut.toByteArray()
        if (framesData.isEmpty()) {
            return ByteArray(0)
        }

        // 10-byte ID3v2.3 header
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 0x03 // v2.3
        header[4] = 0x00
        header[5] = 0x00 // Flags

        // Size as 4 syncsafe bytes
        val size = framesData.size
        header[6] = ((size shr 21) and 0x7F).toByte()
        header[7] = ((size shr 14) and 0x7F).toByte()
        header[8] = ((size shr 7) and 0x7F).toByte()
        header[9] = (size and 0x7F).toByte()

        val fullTag = ByteArrayOutputStream()
        fullTag.write(header)
        fullTag.write(framesData)
        return fullTag.toByteArray()
    }

    private fun writeTextFrame(out: ByteArrayOutputStream, frameId: String, text: String) {
        if (text.isEmpty()) return
        val frameData = ByteArrayOutputStream()
        // Encoding byte: 0x01 for UTF-16 with BOM
        frameData.write(0x01)
        val textBytes = text.toByteArray(StandardCharsets.UTF_16)
        frameData.write(textBytes)

        writeFrame(out, frameId, frameData.toByteArray())
    }

    private fun writeApicFrame(out: ByteArrayOutputStream, artBytes: ByteArray) {
        if (artBytes.isEmpty()) return
        val frameData = ByteArrayOutputStream()
        frameData.write(0x00) // ISO-8859-1 for MIME string

        // Detect mime type
        val mime = if (artBytes.size >= 8 && artBytes[0] == 0x89.toByte() && artBytes[1] == 0x50.toByte()) {
            "image/png"
        } else {
            "image/jpeg"
        }
        frameData.write(mime.toByteArray(StandardCharsets.ISO_8859_1))
        frameData.write(0x00) // Null terminator
        frameData.write(0x03) // Cover (front)
        frameData.write(0x00) // Description null terminator
        frameData.write(artBytes)

        writeFrame(out, "APIC", frameData.toByteArray())
    }

    private fun writeFrame(out: ByteArrayOutputStream, frameId: String, data: ByteArray) {
        // Frame ID (4 bytes)
        out.write(frameId.toByteArray(StandardCharsets.US_ASCII))

        // Frame Size (4 bytes, big endian)
        val size = data.size
        out.write((size shr 24) and 0xFF)
        out.write((size shr 16) and 0xFF)
        out.write((size shr 8) and 0xFF)
        out.write(size and 0xFF)

        // Flags (2 bytes)
        out.write(0x00)
        out.write(0x00)

        // Data
        out.write(data)
    }
}
