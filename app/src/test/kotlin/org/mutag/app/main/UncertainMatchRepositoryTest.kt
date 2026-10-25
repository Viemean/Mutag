package org.mutag.app.main

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mutag.core.tagger.cache.InMemoryMetadataCache
import org.mutag.core.tagger.scanner.AudioMetadata
import org.mutag.model.TrackMetadata

/**
 * 存疑匹配项本地持久化与有效性校验单元测试套件。
 */
class UncertainMatchRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private fun createDummyAudio(file: File): AudioMetadata = AudioMetadata(
        filePath = file.absolutePath,
        fileName = file.name,
        fileSize = file.length(),
        lastModified = file.lastModified(),
        format = "MP3",
        title = "Test Song",
        artist = "Test Artist",
    )

    @Test
    fun saveEmptyListDeletesExistingFile() {
        val file = File(tempDir, "uncertain_matches.json")
        file.writeText("dummy content")
        assertTrue(file.exists())

        UncertainMatchRepository.save(tempDir, emptyList())
        assertFalse(file.exists())
    }

    @Test
    fun saveAndLoadValidMatches() {
        val audioFile = File(tempDir, "song1.mp3")
        audioFile.writeBytes(ByteArray(100))

        val dummyAudio = createDummyAudio(audioFile)
        val candidate = TrackMetadata(
            trackId = "1001",
            title = "Matched Song",
        )

        val items = listOf(
            UncertainMatchItem(
                localFile = dummyAudio,
                candidate = candidate,
                reason = "时长相差较大",
            ),
        )

        UncertainMatchRepository.save(tempDir, items)

        val cache = InMemoryMetadataCache()
        cache.put(dummyAudio)

        val loaded = UncertainMatchRepository.load(tempDir, cache)
        assertEquals(1, loaded.size)
        assertEquals("时长相差较大", loaded[0].reason)
        assertEquals("Matched Song", loaded[0].candidate?.title)
    }

    @Test
    fun loadFiltersOutDeletedOrModifiedFiles() {
        val audioFile1 = File(tempDir, "song1.mp3")
        audioFile1.writeBytes(ByteArray(100))

        val audioFile2 = File(tempDir, "song2.mp3")
        audioFile2.writeBytes(ByteArray(100))

        val dummy1 = createDummyAudio(audioFile1)
        val dummy2 = createDummyAudio(audioFile2)

        val items = listOf(
            UncertainMatchItem(localFile = dummy1, candidate = null, reason = "未匹配"),
            UncertainMatchItem(localFile = dummy2, candidate = null, reason = "时长不符"),
        )

        UncertainMatchRepository.save(tempDir, items)

        // 删除 song1，修改 song2 的时间戳
        audioFile1.delete()
        audioFile2.setLastModified(System.currentTimeMillis() + 5000L)

        val cache = InMemoryMetadataCache()
        cache.put(dummy1)
        cache.put(dummy2)

        val loaded = UncertainMatchRepository.load(tempDir, cache)
        assertTrue(loaded.isEmpty())

        // 校验失效项目已被自动刷新写回（文件被清空/删除）
        val reloaded = UncertainMatchRepository.load(tempDir, cache)
        assertTrue(reloaded.isEmpty())
    }

    @Test
    fun clearRemovesFile() {
        val file = File(tempDir, "uncertain_matches.json")
        file.writeText("dummy content")
        assertTrue(file.exists())

        UncertainMatchRepository.clear(tempDir)
        assertFalse(file.exists())
    }
}
