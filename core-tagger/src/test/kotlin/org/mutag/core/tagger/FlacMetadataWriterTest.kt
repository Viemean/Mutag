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
import org.mutag.core.tagger.writer.FlacMetadataWriter
import org.mutag.core.tagger.writer.MetadataPayload

class FlacMetadataWriterTest {

    private fun createMinimalFlac(file: File) {
        FileOutputStream(file).use { fos ->
            fos.write("fLaC".toByteArray(Charsets.US_ASCII))

            // STREAMINFO block (34 bytes), isLast = true
            val streamInfo = ByteArray(34)
            // 44100 Hz, 16 bits, 2 channels: sample rate 44100 = 0xAC44
            // byte 10: (0xAC44 shr 12) = 0x0A
            // byte 11: (0xAC44 shr 4) and 0xFF = 0xC4
            // byte 12: ((0xAC44 and 0x0F) shl 4) or ((2-1) shl 1) or ((16-1) shr 4) = (4 shl 4) or (1 shl 1) or 0 = 0x42
            // byte 13: ((16-1) and 0x0F) shl 4 = 0xF0
            streamInfo[10] = 0x0A.toByte()
            streamInfo[11] = 0xC4.toByte()
            streamInfo[12] = 0x42.toByte()
            streamInfo[13] = 0xF0.toByte()

            fos.write(0x80) // isLast = true, type = 0
            fos.write(0x00)
            fos.write(0x00)
            fos.write(34)
            fos.write(streamInfo)

            // Audio frames
            fos.write(ByteArray(2048) { 0x55.toByte() })
        }
    }

    @Test
    fun writeFlacMetadataUpdatesTagsCorrectly(@TempDir tempDir: Path) {
        val file = tempDir.resolve("test.flac").toFile()
        createMinimalFlac(file)

        val initialCover = ByteArray(100) { 0xAA.toByte() }
        val payload = MetadataPayload(
            title = "夜曲",
            artist = "周杰伦",
            album = "十一月的萧邦",
            albumArtist = "周杰伦",
            trackNumber = 1,
            trackTotal = 12,
            discNumber = 1,
            discTotal = 1,
            publishDate = "2005-11-01",
            genre = "流行",
            lyricist = "方文山",
            composer = "周杰伦",
            company = "阿尔发音乐",
            comment = "测试曲目",
            producer = "周杰伦",
            arranger = "林迈可",
            coverBytes = initialCover,
            coverMime = "image/jpeg",
            lyrics = "[00:01.00]一群嗜血的蚂蚁 被腐肉所吸引",
            cloudTrackId = "002V5j041q0V0H",
            cloudAlbumMid = "000MkMni19ClKG",
        )

        val writeSuccess = FlacMetadataWriter.write(file, payload)
        assertTrue(writeSuccess)

        val parsed = AudioHeaderParser.parse(file)
        assertEquals("夜曲", parsed.title)
        assertEquals("周杰伦", parsed.artist)
        assertEquals("十一月的萧邦", parsed.album)
        assertEquals("周杰伦", parsed.albumArtist)
        assertEquals(1, parsed.trackNumber)
        assertEquals(12, parsed.trackTotal)
        assertEquals("2005-11-01", parsed.publishDate)
        assertEquals("流行", parsed.genre)
        assertEquals("方文山", parsed.lyricist)
        assertEquals("周杰伦", parsed.composer)
        assertEquals("周杰伦", parsed.producer)
        assertEquals("林迈可", parsed.arranger)
        assertEquals("测试曲目", parsed.comment)
        assertEquals(true, parsed.hasCover)
        assertEquals(100L, parsed.coverSize)
        assertEquals("002V5j041q0V0H", parsed.cloudTrackId)
        assertEquals("000MkMni19ClKG", parsed.cloudAlbumMid)
        assertEquals(TagHealth.COMPLETE, parsed.tagHealth)

        // 校验提取出的真实封面二进制完整性
        val extractedCover = AudioCoverExtractor.extractCoverBytes(file)
        assertArrayEquals(initialCover, extractedCover)
    }

    @Test
    fun writeFlacMetadataReplacesExistingCoverCorrectly(@TempDir tempDir: Path) {
        val file = tempDir.resolve("replace_cover.flac").toFile()
        createMinimalFlac(file)

        val coverA = ByteArray(128) { 0x11.toByte() }
        val payloadA = MetadataPayload(
            title = "发如雪",
            artist = "周杰伦",
            album = "十一月的萧邦",
            coverBytes = coverA,
            coverMime = "image/jpeg",
        )
        assertTrue(FlacMetadataWriter.write(file, payloadA))
        assertArrayEquals(coverA, AudioCoverExtractor.extractCoverBytes(file))

        // 二次覆写替换为新封面
        val coverB = ByteArray(256) { 0x22.toByte() }
        val payloadB = payloadA.copy(coverBytes = coverB)
        assertTrue(FlacMetadataWriter.write(file, payloadB))

        val parsed = AudioHeaderParser.parse(file)
        assertEquals(256L, parsed.coverSize)
        assertArrayEquals(coverB, AudioCoverExtractor.extractCoverBytes(file))
    }

    @Test
    fun writeFlacMetadataNormalizesBlankCoverMime(@TempDir tempDir: Path) {
        val file = tempDir.resolve("blank_mime.flac").toFile()
        createMinimalFlac(file)

        // 构造带有 PNG 标识头的字节
        val pngCover = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()) + ByteArray(64)
        val payload = MetadataPayload(
            title = "枫",
            artist = "周杰伦",
            album = "十一月的萧邦",
            coverBytes = pngCover,
            coverMime = "", // 传入空 MIME
        )

        assertTrue(org.mutag.core.tagger.writer.AudioMetadataWriter.writeMetadata(file, payload))

        val parsed = AudioHeaderParser.parse(file)
        assertTrue(parsed.hasCover)
        assertEquals(pngCover.size.toLong(), parsed.coverSize)
        assertArrayEquals(pngCover, AudioCoverExtractor.extractCoverBytes(file))
    }
}
