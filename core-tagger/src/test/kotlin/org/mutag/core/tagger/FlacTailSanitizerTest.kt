package org.mutag.core.tagger

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mutag.core.tagger.sanitizer.FlacTailSanitizer

class FlacTailSanitizerTest {

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

    private fun computeCrc16(bytes: ByteArray, offset: Int, length: Int): Int {
        var crc = 0
        for (i in offset until offset + length) {
            val byteVal = bytes[i].toInt() and 0xFF
            crc = ((crc shl 8) and 0xFFFF) xor CRC16_TABLE[(crc ushr 8) xor byteVal]
        }
        return crc
    }

    private fun createValidFlacFile(file: File, frameCount: Int = 10): Long {
        RandomAccessFile(file, "rw").use { raf ->
            raf.write("fLaC".toByteArray(Charsets.US_ASCII))

            // STREAMINFO block
            val streamInfo = ByteArray(34)
            raf.write(0x80) // isLast = true, type = 0 (STREAMINFO)
            raf.write(0x00)
            raf.write(0x00)
            raf.write(34)
            raf.write(streamInfo)

            // Padding to ensure > 4096 bytes
            val padding = ByteArray(4096)
            raf.write(padding)

            // Valid FLAC frames
            repeat(frameCount) {
                val frameHeaderAndData = ByteArray(120)
                frameHeaderAndData[0] = 0xFF.toByte()
                frameHeaderAndData[1] = 0xF8.toByte() // sync 14-bit
                for (j in 2 until 120) {
                    frameHeaderAndData[j] = (j and 0x7F).toByte()
                }

                val crc = computeCrc16(frameHeaderAndData, 0, frameHeaderAndData.size)
                raf.write(frameHeaderAndData)
                raf.write((crc shr 8) and 0xFF)
                raf.write(crc and 0xFF)
            }
            return raf.length()
        }
    }

    @Test
    fun sanitizeCleanFileReturnsFalse(@TempDir tempDir: Path) {
        val file = tempDir.resolve("clean.flac").toFile()
        val originalLen = createValidFlacFile(file)

        val result = FlacTailSanitizer.sanitize(file)
        assertFalse(result)
        assertEquals(originalLen, file.length())
    }

    @Test
    fun sanitizeCorruptedTailTruncatesGarbage(@TempDir tempDir: Path) {
        val file = tempDir.resolve("corrupted.flac").toFile()
        val originalLen = createValidFlacFile(file)

        // Append 128 bytes of trailing corrupted garbage
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(originalLen)
            raf.write(ByteArray(128) { 0x55.toByte() })
        }

        assertTrue(file.length() > originalLen)

        val result = FlacTailSanitizer.sanitize(file)
        assertTrue(result)
        assertEquals(originalLen, file.length())
    }

    @Test
    fun sanitizeIncompleteTrailingFrameTruncatesToPreviousValidFrame(@TempDir tempDir: Path) {
        val file = tempDir.resolve("incomplete_frame.flac").toFile()
        val originalLen = createValidFlacFile(file)

        // Append incomplete frame starting with 0xFF 0xF8 sync header but broken CRC/cut off
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(originalLen)
            raf.write(0xFF)
            raf.write(0xF8)
            raf.write(ByteArray(50) { 0x12.toByte() })
        }

        assertTrue(file.length() > originalLen)

        val result = FlacTailSanitizer.sanitize(file)
        assertTrue(result)
        assertEquals(originalLen, file.length())
    }
}
