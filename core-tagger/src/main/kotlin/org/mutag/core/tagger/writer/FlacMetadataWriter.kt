package org.mutag.core.tagger.writer

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.mutag.core.tagger.sanitizer.FlacTailSanitizer
import org.mutag.core.tagger.storage.CrashProofFileOps

/**
 * FLAC 元数据块（VORBIS_COMMENT、PICTURE）写入器。
 */
object FlacMetadataWriter {

    /**
     * 将元数据载荷写入 FLAC 音频文件。
     *
     * @param file 目标 FLAC 文件
     * @param payload 待写入的音频元数据载荷
     * @return 写入成功返回 true，写入失败返回 false
     */
    fun write(file: File, payload: MetadataPayload): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 4L) return false

        return CrashProofFileOps.executeWithCrashProof(file, "flac") { tempFile ->
            FileInputStream(file).use { fis ->
                val magic = ByteArray(4)
                if (fis.read(magic) != 4 || String(magic, Charsets.US_ASCII) != "fLaC") {
                    return@executeWithCrashProof false
                }

                var streamInfoBlock: ByteArray? = null
                val otherBlocks = mutableListOf<ByteArray>()

                var isLast = false
                while (!isLast) {
                    val header = ByteArray(4)
                    if (fis.read(header) != 4) break
                    isLast = (header[0].toInt() and 0x80) != 0
                    val blockType = header[0].toInt() and 0x7F
                    val length =
                        ((header[1].toInt() and 0xFF) shl 16) or
                            ((header[2].toInt() and 0xFF) shl 8) or
                            (header[3].toInt() and 0xFF)

                    val data = ByteArray(length)
                    var readLen = 0
                    while (readLen < length) {
                        val r = fis.read(data, readLen, length - readLen)
                        if (r <= 0) break
                        readLen += r
                    }

                    when (blockType) {
                        0 -> streamInfoBlock = data

                        4, 6 -> {
                            // 过滤旧有的 VORBIS_COMMENT 与 PICTURE 数据块
                        }

                        else -> {
                            val fullBlock = ByteArray(4 + length)
                            System.arraycopy(header, 0, fullBlock, 0, 4)
                            fullBlock[0] = (fullBlock[0].toInt() and 0x7F).toByte()
                            System.arraycopy(data, 0, fullBlock, 4, length)
                            otherBlocks.add(fullBlock)
                        }
                    }
                }

                if (streamInfoBlock == null) {
                    return@executeWithCrashProof false
                }

                val vorbisBlockData = buildVorbisComment(payload)
                val pictureBlockData = payload.coverBytes?.let { buildPictureBlock(it, payload.coverMime) }

                val blocksToWrite = mutableListOf<ByteArray>()
                blocksToWrite.add(makeBlock(0, streamInfoBlock))
                blocksToWrite.addAll(otherBlocks)
                blocksToWrite.add(makeBlock(4, vorbisBlockData))
                if (pictureBlockData != null) {
                    blocksToWrite.add(makeBlock(6, pictureBlockData))
                }

                if (blocksToWrite.isNotEmpty()) {
                    val lastIdx = blocksToWrite.lastIndex
                    val lastBlock = blocksToWrite[lastIdx]
                    lastBlock[0] = (lastBlock[0].toInt() or 0x80).toByte()
                }

                FileOutputStream(tempFile).use { fos ->
                    fos.write("fLaC".toByteArray(Charsets.US_ASCII))
                    for (b in blocksToWrite) {
                        fos.write(b)
                    }

                    val buf = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (fis.read(buf).also { bytesRead = it } != -1) {
                        fos.write(buf, 0, bytesRead)
                    }
                }
            }

            FlacTailSanitizer.sanitize(tempFile)
            true
        }
    }

    private fun makeBlock(type: Int, data: ByteArray): ByteArray {
        val len = data.size
        val block = ByteArray(4 + len)
        block[0] = (type and 0x7F).toByte()
        block[1] = ((len shr 16) and 0xFF).toByte()
        block[2] = ((len shr 8) and 0xFF).toByte()
        block[3] = (len and 0xFF).toByte()
        System.arraycopy(data, 0, block, 4, len)
        return block
    }

    private fun buildVorbisComment(payload: MetadataPayload): ByteArray {
        val comments = mutableListOf<String>()
        if (payload.title.isNotBlank()) comments.add("TITLE=${payload.title}")
        if (payload.artist.isNotBlank()) comments.add("ARTIST=${payload.artist}")
        if (payload.album.isNotBlank()) comments.add("ALBUM=${payload.album}")
        if (payload.albumArtist.isNotBlank()) comments.add("ALBUMARTIST=${payload.albumArtist}")
        if (payload.trackNumber > 0) comments.add("TRACKNUMBER=${payload.trackNumber}")
        if (payload.trackTotal > 0) comments.add("TRACKTOTAL=${payload.trackTotal}")
        if (payload.discNumber > 0) comments.add("DISCNUMBER=${payload.discNumber}")
        if (payload.discTotal > 0) comments.add("DISCTOTAL=${payload.discTotal}")
        if (payload.publishDate.isNotBlank()) comments.add("DATE=${payload.publishDate}")
        if (payload.genre.isNotBlank()) comments.add("GENRE=${payload.genre}")
        if (payload.lyricist.isNotBlank()) comments.add("LYRICIST=${payload.lyricist}")
        if (payload.composer.isNotBlank()) comments.add("COMPOSER=${payload.composer}")
        if (payload.arranger.isNotBlank()) comments.add("ARRANGER=${payload.arranger}")
        if (payload.producer.isNotBlank()) comments.add("PRODUCER=${payload.producer}")
        if (payload.company.isNotBlank()) comments.add("ORGANIZATION=${payload.company}")
        if (payload.comment.isNotBlank()) comments.add("COMMENT=${payload.comment}")
        if (payload.replayGainTrackGain != null) comments.add("REPLAYGAIN_TRACK_GAIN=%.2f dB".format(payload.replayGainTrackGain))
        if (payload.replayGainTrackPeak != null) comments.add("REPLAYGAIN_TRACK_PEAK=%.6f".format(payload.replayGainTrackPeak))
        if (payload.language.isNotBlank()) comments.add("LANGUAGE=${payload.language}")
        if (payload.chorusStartMs > 0) comments.add("CHORUS_START=${payload.chorusStartMs}")
        if (payload.cloudTrackId.isNotBlank()) comments.add("QQMUSIC_SONGMID=${payload.cloudTrackId}")
        if (payload.cloudAlbumMid.isNotBlank()) comments.add("QQMUSIC_ALBUMMID=${payload.cloudAlbumMid}")
        if (payload.description.isNotBlank()) comments.add("DESCRIPTION=${payload.description}")
        if (!payload.lyrics.isNullOrBlank()) comments.add("LYRICS=${payload.lyrics}")
        comments.add("ENCODER=MuTag")

        val vendor = "MuTag FLAC Writer".toByteArray(Charsets.UTF_8)
        val baos = ByteArrayOutputStream()

        baos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(vendor.size).array())
        baos.write(vendor)

        baos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(comments.size).array())

        for (c in comments) {
            val cBytes = c.toByteArray(Charsets.UTF_8)
            baos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(cBytes.size).array())
            baos.write(cBytes)
        }

        return baos.toByteArray()
    }

    private fun buildPictureBlock(coverBytes: ByteArray, mimeType: String): ByteArray {
        val mimeBytes = mimeType.toByteArray(Charsets.US_ASCII)
        val descBytes = ByteArray(0)

        val baos = ByteArrayOutputStream()
        val bb = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)

        // PICTURE 块类型 3：正面封面
        bb.putInt(3)
        bb.putInt(mimeBytes.size)
        baos.write(bb.array(), 0, 8)
        baos.write(mimeBytes)

        bb.clear()
        bb.putInt(descBytes.size)
        bb.putInt(0) // width
        bb.putInt(0) // height
        bb.putInt(24) // bit depth
        bb.putInt(0) // indexed colors
        bb.putInt(coverBytes.size)
        baos.write(bb.array(), 0, 24)
        baos.write(coverBytes)

        return baos.toByteArray()
    }
}
