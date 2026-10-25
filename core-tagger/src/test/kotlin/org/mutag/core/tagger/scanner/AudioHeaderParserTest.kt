package org.mutag.core.tagger.scanner

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AudioHeaderParserTest {

    @Test
    fun parseNonExistentFileReturnsFallbackMetadata() {
        val nonExistentFile = File("/dummy/non_existent_track.mp3")
        val metadata = AudioHeaderParser.parse(nonExistentFile)

        assertEquals("MP3", metadata.format)
        assertEquals("non_existent_track", metadata.title)
        assertEquals(TagHealth.NEEDS_MATCH, metadata.tagHealth)
        assertFalse(metadata.hasCover)
    }

    @Test
    fun parseEmptyFileReturnsFallbackMetadataWithoutException(@TempDir tempDir: Path) {
        val emptyFile = tempDir.resolve("empty.flac").toFile()
        emptyFile.createNewFile()

        val metadata = AudioHeaderParser.parse(emptyFile)
        assertEquals("FLAC", metadata.format)
        assertEquals("empty", metadata.title)
        assertEquals(0L, metadata.fileSize)
        assertEquals(TagHealth.NEEDS_MATCH, metadata.tagHealth)
    }

    @Test
    fun parseCorruptedBinaryFileReturnsFallbackMetadata(@TempDir tempDir: Path) {
        val corruptFile = tempDir.resolve("corrupted.mp3").toFile()
        FileOutputStream(corruptFile).use { fos ->
            fos.write(ByteArray(128) { 0x77.toByte() })
        }

        val metadata = AudioHeaderParser.parse(corruptFile)
        assertEquals("MP3", metadata.format)
        assertEquals("corrupted", metadata.title)
        assertEquals(128L, metadata.fileSize)
    }

    @Test
    fun audioParserUtilsFormattingFunctions() {
        assertEquals("未知", AudioParserUtils.formatDuration(0L))
        assertEquals("未知", AudioParserUtils.formatDuration(-1000L))
        assertEquals("03:25", AudioParserUtils.formatDuration(205000L))
        assertEquals("00:05", AudioParserUtils.formatDuration(5000L))

        assertEquals("0 B", AudioParserUtils.formatFileSize(0L))
        assertEquals("0 B", AudioParserUtils.formatFileSize(-10L))
        val oneMb = AudioParserUtils.formatFileSize(1024 * 1024)
        assertTrue(oneMb.startsWith("1.00 MB"))

        assertEquals(Charsets.ISO_8859_1, AudioParserUtils.charsetForEncoding(0))
        assertEquals(Charsets.UTF_16, AudioParserUtils.charsetForEncoding(1))
        assertEquals(Charsets.UTF_16BE, AudioParserUtils.charsetForEncoding(2))
        assertEquals(Charsets.UTF_8, AudioParserUtils.charsetForEncoding(3))
        assertEquals(Charsets.UTF_8, AudioParserUtils.charsetForEncoding(99))
    }
}
