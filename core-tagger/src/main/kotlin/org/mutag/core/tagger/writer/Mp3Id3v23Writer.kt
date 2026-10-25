package org.mutag.core.tagger.writer

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.mutag.core.tagger.storage.CrashProofFileOps

/**
 * MP3 ID3v2.3 标签帧写入器。
 */
object Mp3Id3v23Writer {

    /**
     * 将元数据载荷写入 MP3 音频文件 ID3v2.3 头部。
     *
     * @param file 目标 MP3 文件
     * @param payload 待写入的音频元数据载荷
     * @return 写入成功返回 true，写入失败返回 false
     */
    fun write(file: File, payload: MetadataPayload): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 4L) return false

        return CrashProofFileOps.executeWithCrashProof(file, "mp3") { tempFile ->
            FileInputStream(file).use { fis ->
                val head = ByteArray(10)
                if (fis.read(head) == 10) {
                    if (head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) {
                        val s0 = head[6].toInt() and 0x7F
                        val s1 = head[7].toInt() and 0x7F
                        val s2 = head[8].toInt() and 0x7F
                        val s3 = head[9].toInt() and 0x7F
                        val tagBodySize = (s0 shl 21) or (s1 shl 14) or (s2 shl 7) or s3
                        fis.channel.position(10L + tagBodySize.toLong())
                    } else {
                        fis.channel.position(0L)
                    }
                } else {
                    fis.channel.position(0L)
                }

                val newId3Block = buildId3v23Tag(payload)

                FileOutputStream(tempFile).use { fos ->
                    fos.write(newId3Block)

                    val buf = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (fis.read(buf).also { bytesRead = it } != -1) {
                        fos.write(buf, 0, bytesRead)
                    }
                }
            }
            true
        }
    }

    private fun buildId3v23Tag(payload: MetadataPayload): ByteArray {
        val framesBaos = ByteArrayOutputStream()

        if (payload.title.isNotBlank()) framesBaos.write(buildTextFrame("TIT2", payload.title))
        if (payload.artist.isNotBlank()) framesBaos.write(buildTextFrame("TPE1", payload.artist))
        if (payload.album.isNotBlank()) framesBaos.write(buildTextFrame("TALB", payload.album))
        if (payload.albumArtist.isNotBlank()) framesBaos.write(buildTextFrame("TPE2", payload.albumArtist))

        if (payload.trackNumber > 0) {
            val trck = if (payload.trackTotal > 0) "${payload.trackNumber}/${payload.trackTotal}" else "${payload.trackNumber}"
            framesBaos.write(buildTextFrame("TRCK", trck))
        }

        if (payload.discNumber > 0) {
            val tpos = if (payload.discTotal > 0) "${payload.discNumber}/${payload.discTotal}" else "${payload.discNumber}"
            framesBaos.write(buildTextFrame("TPOS", tpos))
        }

        if (payload.publishDate.isNotBlank()) {
            framesBaos.write(buildTextFrame("TYER", payload.publishDate.take(4)))
            framesBaos.write(buildTextFrame("TDRC", payload.publishDate))
        }

        if (payload.genre.isNotBlank()) framesBaos.write(buildTextFrame("TCON", payload.genre))
        if (payload.lyricist.isNotBlank()) framesBaos.write(buildTextFrame("TEXT", payload.lyricist))
        if (payload.composer.isNotBlank()) framesBaos.write(buildTextFrame("TCOM", payload.composer))
        if (payload.company.isNotBlank()) framesBaos.write(buildTextFrame("TPUB", payload.company))

        if (payload.arranger.isNotBlank()) {
            framesBaos.write(buildUserTextFrame("ARRANGER", payload.arranger))
        }

        if (payload.producer.isNotBlank()) {
            framesBaos.write(buildUserTextFrame("PRODUCER", payload.producer))
        }

        if (payload.replayGainTrackGain != null) {
            framesBaos.write(buildUserTextFrame("REPLAYGAIN_TRACK_GAIN", "%.2f dB".format(payload.replayGainTrackGain)))
        }

        if (payload.replayGainTrackPeak != null) {
            framesBaos.write(buildUserTextFrame("REPLAYGAIN_TRACK_PEAK", "%.6f".format(payload.replayGainTrackPeak)))
        }

        if (payload.language.isNotBlank()) {
            framesBaos.write(buildTextFrame("TLAN", payload.language))
        }

        if (payload.chorusStartMs > 0) {
            framesBaos.write(buildUserTextFrame("CHORUS_START", payload.chorusStartMs.toString()))
        }

        if (payload.cloudTrackId.isNotBlank()) {
            framesBaos.write(buildUserTextFrame("QQMUSIC_SONGMID", payload.cloudTrackId))
        }

        if (payload.cloudAlbumMid.isNotBlank()) {
            framesBaos.write(buildUserTextFrame("QQMUSIC_ALBUMMID", payload.cloudAlbumMid))
        }

        if (payload.comment.isNotBlank()) {
            framesBaos.write(buildCommentFrame(payload.comment))
        }

        if (!payload.lyrics.isNullOrBlank()) {
            framesBaos.write(buildLyricsFrame(payload.lyrics))
        }

        if (payload.coverBytes != null && payload.coverBytes.isNotEmpty()) {
            framesBaos.write(buildPictureFrame(payload.coverBytes, payload.coverMime))
        }

        val frameBytes = framesBaos.toByteArray()
        val tagSize = frameBytes.size

        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 0x03 // ID3v2.3
        header[4] = 0x00
        header[5] = 0x00 // flags
        // 组装 7 位同步安全标签体大小
        header[6] = ((tagSize shr 21) and 0x7F).toByte()
        header[7] = ((tagSize shr 14) and 0x7F).toByte()
        header[8] = ((tagSize shr 7) and 0x7F).toByte()
        header[9] = (tagSize and 0x7F).toByte()

        val fullTag = ByteArray(10 + tagSize)
        System.arraycopy(header, 0, fullTag, 0, 10)
        System.arraycopy(frameBytes, 0, fullTag, 10, tagSize)
        return fullTag
    }

    private fun buildTextFrame(frameId: String, text: String): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val dataLen = 1 + textBytes.size // 1 byte encoding (0x03 = UTF-8)

        val baos = ByteArrayOutputStream()
        baos.write(frameId.toByteArray(Charsets.US_ASCII))
        baos.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(dataLen).array())
        baos.write(byteArrayOf(0x00, 0x00)) // flags
        baos.write(0x03) // UTF-8
        baos.write(textBytes)
        return baos.toByteArray()
    }

    private fun buildUserTextFrame(description: String, text: String): ByteArray {
        val descBytes = description.toByteArray(Charsets.UTF_8)
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val dataLen = 1 + descBytes.size + 1 + textBytes.size

        val baos = ByteArrayOutputStream()
        baos.write("TXXX".toByteArray(Charsets.US_ASCII))
        baos.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(dataLen).array())
        baos.write(byteArrayOf(0x00, 0x00))
        baos.write(0x03) // UTF-8
        baos.write(descBytes)
        baos.write(0x00) // Null terminator
        baos.write(textBytes)
        return baos.toByteArray()
    }

    private fun buildCommentFrame(commentText: String): ByteArray {
        val textBytes = commentText.toByteArray(Charsets.UTF_8)
        val dataLen = 1 + 3 + 1 + textBytes.size

        val baos = ByteArrayOutputStream()
        baos.write("COMM".toByteArray(Charsets.US_ASCII))
        baos.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(dataLen).array())
        baos.write(byteArrayOf(0x00, 0x00))
        baos.write(0x03) // UTF-8
        baos.write("chi".toByteArray(Charsets.US_ASCII))
        baos.write(0x00) // Short description null terminator
        baos.write(textBytes)
        return baos.toByteArray()
    }

    private fun buildLyricsFrame(lyricsText: String): ByteArray {
        val textBytes = lyricsText.toByteArray(Charsets.UTF_8)
        val dataLen = 1 + 3 + 1 + textBytes.size

        val baos = ByteArrayOutputStream()
        baos.write("USLT".toByteArray(Charsets.US_ASCII))
        baos.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(dataLen).array())
        baos.write(byteArrayOf(0x00, 0x00))
        baos.write(0x03) // UTF-8
        baos.write("chi".toByteArray(Charsets.US_ASCII))
        baos.write(0x00)
        baos.write(textBytes)
        return baos.toByteArray()
    }

    private fun buildPictureFrame(coverBytes: ByteArray, mimeType: String): ByteArray {
        val mimeBytes = mimeType.toByteArray(Charsets.US_ASCII)
        val dataLen = 1 + mimeBytes.size + 1 + 1 + 1 + coverBytes.size

        val baos = ByteArrayOutputStream()
        baos.write("APIC".toByteArray(Charsets.US_ASCII))
        baos.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(dataLen).array())
        baos.write(byteArrayOf(0x00, 0x00))
        baos.write(0x00) // ISO-8859-1 for MIME
        baos.write(mimeBytes)
        baos.write(0x00)
        baos.write(0x03) // Front cover
        baos.write(0x00) // Description null terminator
        baos.write(coverBytes)
        return baos.toByteArray()
    }
}
