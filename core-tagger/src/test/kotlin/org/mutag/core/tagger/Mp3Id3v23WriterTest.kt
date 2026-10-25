package org.mutag.core.tagger

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mutag.core.tagger.scanner.AudioCoverExtractor
import org.mutag.core.tagger.scanner.AudioHeaderParser
import org.mutag.core.tagger.scanner.TagHealth
import org.mutag.core.tagger.writer.MetadataPayload
import org.mutag.core.tagger.writer.Mp3Id3v23Writer

class Mp3Id3v23WriterTest {

    private fun createMinimalMp3(file: File) {
        FileOutputStream(file).use { fos ->
            // Minimal MPEG frame sync 0xFF 0xFB (MPEG-1 Layer 3, no CRC)
            val audioData = ByteArray(1024)
            audioData[0] = 0xFF.toByte()
            audioData[1] = 0xFB.toByte()
            audioData[2] = 0x90.toByte() // 128 kbps, 44100 Hz
            audioData[3] = 0x00.toByte()
            fos.write(audioData)
        }
    }

    @Test
    fun writeMp3MetadataUpdatesTagsCorrectly(@TempDir tempDir: Path) {
        val file = tempDir.resolve("test.mp3").toFile()
        createMinimalMp3(file)

        val initialCover = ByteArray(80) { 0xBB.toByte() }
        val payload = MetadataPayload(
            title = "晴天",
            artist = "周杰伦",
            album = "叶惠美",
            albumArtist = "周杰伦",
            trackNumber = 3,
            trackTotal = 11,
            publishDate = "2003-07-31",
            genre = "流行",
            lyricist = "周杰伦",
            composer = "周杰伦",
            company = "阿尔发音乐",
            comment = "经典旋律",
            producer = "周杰伦",
            arranger = "周杰伦",
            coverBytes = initialCover,
            coverMime = "image/jpeg",
            lyrics = "[00:00.00]晴天 - 周杰伦",
            cloudTrackId = "003aWWhM0xDfrD",
            cloudAlbumMid = "0002SpQn3yHh47",
        )

        val writeSuccess = Mp3Id3v23Writer.write(file, payload)
        assertTrue(writeSuccess)

        val parsed = AudioHeaderParser.parse(file)
        assertEquals("晴天", parsed.title)
        assertEquals("周杰伦", parsed.artist)
        assertEquals("叶惠美", parsed.album)
        assertEquals("周杰伦", parsed.albumArtist)
        assertEquals(3, parsed.trackNumber)
        assertEquals(11, parsed.trackTotal)
        assertEquals("2003-07-31", parsed.publishDate)
        assertEquals("流行", parsed.genre)
        assertEquals("周杰伦", parsed.lyricist)
        assertEquals("周杰伦", parsed.producer)
        assertEquals("周杰伦", parsed.arranger)
        assertEquals("经典旋律", parsed.comment)
        assertEquals(true, parsed.hasCover)
        assertEquals(80L, parsed.coverSize)
        assertEquals("003aWWhM0xDfrD", parsed.cloudTrackId)
        assertEquals("0002SpQn3yHh47", parsed.cloudAlbumMid)
        assertEquals(TagHealth.COMPLETE, parsed.tagHealth)

        // 校验提取出的真实封面二进制完整性
        val extractedCover = AudioCoverExtractor.extractCoverBytes(file)
        assertArrayEquals(initialCover, extractedCover)
    }

    @Test
    fun writeMp3MetadataReplacesExistingCoverCorrectly(@TempDir tempDir: Path) {
        val file = tempDir.resolve("replace_cover.mp3").toFile()
        createMinimalMp3(file)

        val coverA = ByteArray(120) { 0x33.toByte() }
        val payloadA = MetadataPayload(
            title = "东风破",
            artist = "周杰伦",
            album = "叶惠美",
            coverBytes = coverA,
            coverMime = "image/jpeg",
        )
        assertTrue(Mp3Id3v23Writer.write(file, payloadA))
        assertArrayEquals(coverA, AudioCoverExtractor.extractCoverBytes(file))

        // 二次覆写替换为新封面
        val coverB = ByteArray(240) { 0x44.toByte() }
        val payloadB = payloadA.copy(coverBytes = coverB)
        assertTrue(Mp3Id3v23Writer.write(file, payloadB))

        val parsed = AudioHeaderParser.parse(file)
        assertEquals(240L, parsed.coverSize)
        assertArrayEquals(coverB, AudioCoverExtractor.extractCoverBytes(file))
    }
}
