package org.mutag.core.tagger.scanner

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 本地音频文件内嵌封面图像二进制安全提取工具。
 */
object AudioCoverExtractor {

    /**
     * 从指定音频文件（支持 FLAC 与 MP3）解析提取内嵌封面图像字节数组。
     *
     * @param file 目标音频文件对象
     * @return 提取成功返回图像字节数组，无内嵌封面或解析失败返回 null
     */
    fun extractCoverBytes(file: File): ByteArray? {
        if (!file.exists() || !file.isFile) return null
        return when (file.extension.lowercase()) {
            "flac" -> extractFlacPicture(file)
            "mp3" -> extractMp3Picture(file)
            else -> null
        }
    }

    private fun extractFlacPicture(file: File): ByteArray? {
        return try {
            FileInputStream(file).use { fis ->
                val magic = ByteArray(4)
                if (fis.read(magic) != 4 || String(magic, Charsets.US_ASCII) != "fLaC") return null

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

                    if (blockType == 6) { // PICTURE 块
                        val data = ByteArray(length)
                        var read = 0
                        while (read < length) {
                            val r = fis.read(data, read, length - read)
                            if (r <= 0) break
                            read += r
                        }

                        val bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                        bb.int // picture type
                        val mimeLen = bb.int
                        bb.position(bb.position() + mimeLen)
                        val descLen = bb.int
                        bb.position(bb.position() + descLen)
                        bb.int // width
                        bb.int // height
                        bb.int // depth
                        bb.int // colors
                        val picLen = bb.int
                        if (picLen > 0 && picLen <= bb.remaining()) {
                            val picBytes = ByteArray(picLen)
                            bb.get(picBytes)
                            return picBytes
                        }
                        return null
                    } else {
                        seekSkip(fis, length.toLong())
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractMp3Picture(file: File): ByteArray? {
        return try {
            FileInputStream(file).use { fis ->
                val head = ByteArray(10)
                if (fis.read(head) == 10 && head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) {
                    val s0 = head[6].toInt() and 0x7F
                    val s1 = head[7].toInt() and 0x7F
                    val s2 = head[8].toInt() and 0x7F
                    val s3 = head[9].toInt() and 0x7F
                    val tagSize = (s0 shl 21) or (s1 shl 14) or (s2 shl 7) or s3

                    val tagBuffer = readFully(fis, tagSize)
                    val bb = ByteBuffer.wrap(tagBuffer).order(ByteOrder.BIG_ENDIAN)

                    while (bb.remaining() >= 10) {
                        val frameIdBytes = ByteArray(4)
                        bb.get(frameIdBytes)
                        if (frameIdBytes[0] == 0.toByte()) break

                        val frameId = String(frameIdBytes, Charsets.US_ASCII)
                        val frameLen = bb.int
                        bb.short // flags

                        if (frameLen <= 0 || frameLen > bb.remaining()) break

                        if (frameId == "APIC") {
                            val frameData = ByteArray(frameLen)
                            bb.get(frameData)

                            var mimeEnd = -1
                            for (pos in 1 until frameData.size) {
                                if (frameData[pos] == 0.toByte()) {
                                    mimeEnd = pos
                                    break
                                }
                            }
                            if (mimeEnd > 0) {
                                var descEnd = -1
                                for (pos in mimeEnd + 2 until frameData.size) {
                                    if (frameData[pos] == 0.toByte()) {
                                        descEnd = pos
                                        break
                                    }
                                }
                                if (descEnd > 0 && descEnd + 1 < frameData.size) {
                                    val picBytes = ByteArray(frameData.size - (descEnd + 1))
                                    System.arraycopy(frameData, descEnd + 1, picBytes, 0, picBytes.size)
                                    return picBytes
                                }
                            }
                            return null
                        } else {
                            bb.position(bb.position() + frameLen)
                        }
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readFully(fis: FileInputStream, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val data = ByteArray(length)
        var readLen = 0
        while (readLen < length) {
            val r = fis.read(data, readLen, length - readLen)
            if (r <= 0) break
            readLen += r
        }
        return if (readLen == length) data else data.copyOf(readLen)
    }

    private fun seekSkip(fis: FileInputStream, length: Long) {
        if (length <= 0L) return
        try {
            fis.channel.position(fis.channel.position() + length)
        } catch (_: Exception) {
            var rem = length
            while (rem > 0L) {
                val s = fis.skip(rem)
                if (s <= 0L) {
                    if (fis.read() == -1) break
                    rem -= 1
                } else {
                    rem -= s
                }
            }
        }
    }
}
