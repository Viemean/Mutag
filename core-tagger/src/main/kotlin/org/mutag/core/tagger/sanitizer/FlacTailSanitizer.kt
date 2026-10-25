package org.mutag.core.tagger.sanitizer

import java.io.File
import java.io.RandomAccessFile

/**
 * FLAC 文件末尾冗余数据与非音频杂质截断清理工具。
 */
object FlacTailSanitizer {
    private const val MAX_SCAN_TAIL_BYTES = 65536
    private const val MIN_FLAC_FILE_SIZE = 42L

    private val CRC16_TABLE = IntArray(256) { i ->
        var crc = i shl 8
        repeat(8) {
            crc = if ((crc and 0x8000) != 0) {
                ((crc shl 1) xor 0x8005) and 0xFFFF
            } else {
                (crc shl 1) and 0xFFFF
            }
        }
        crc
    }

    /**
     * 扫描 FLAC 文件末尾非音频帧数据并执行物理截断。
     *
     * @param file 待清理的目标 FLAC 文件
     * @return 发现并成功截断末尾杂质返回 true，未发现杂质或操作失败返回 false
     */
    fun sanitize(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false
        val fileLength = file.length()
        if (fileLength < MIN_FLAC_FILE_SIZE) return false

        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0L)
                val magic = ByteArray(4)
                if (raf.read(magic) != 4 || String(magic, Charsets.US_ASCII) != "fLaC") {
                    return false
                }

                val tailSize = minOf(MAX_SCAN_TAIL_BYTES.toLong(), fileLength - 4L).toInt()
                val tailOffset = fileLength - tailSize
                raf.seek(tailOffset)
                val buffer = ByteArray(tailSize)
                raf.readFully(buffer)

                // 从文件末端逆向扫描末帧并核对 CRC-16
                for (syncIdx in tailSize - 2 downTo 0) {
                    val b0 = buffer[syncIdx].toInt() and 0xFF
                    val b1 = buffer[syncIdx + 1].toInt() and 0xFF
                    if (b0 == 0xFF && (b1 and 0xFC) == 0xF8) {
                        val maxFrameBytes = tailSize - syncIdx
                        if (maxFrameBytes >= 6) {
                            var runningCrc = 0
                            var validFrameLength = -1

                            for (offset in 0 until maxFrameBytes - 2) {
                                val byteVal = buffer[syncIdx + offset].toInt() and 0xFF
                                runningCrc = ((runningCrc shl 8) and 0xFFFF) xor CRC16_TABLE[(runningCrc ushr 8) xor byteVal]

                                val nextByte0 = buffer[syncIdx + offset + 1].toInt() and 0xFF
                                val nextByte1 = buffer[syncIdx + offset + 2].toInt() and 0xFF
                                val storedCrc = (nextByte0 shl 8) or nextByte1

                                if (runningCrc == storedCrc) {
                                    validFrameLength = offset + 3
                                }
                            }

                            if (validFrameLength > 0) {
                                val expectedEndOffset = tailOffset + syncIdx + validFrameLength
                                val extraBytes = fileLength - expectedEndOffset

                                if (extraBytes in 1..MAX_SCAN_TAIL_BYTES) {
                                    raf.setLength(expectedEndOffset)
                                    return true
                                } else if (extraBytes == 0L) {
                                    return false
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            return false
        }

        return false
    }
}
