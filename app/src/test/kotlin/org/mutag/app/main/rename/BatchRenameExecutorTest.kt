package org.mutag.app.main.rename

import java.io.File
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mutag.core.tagger.scanner.AudioMetadata

class BatchRenameExecutorTest {

    @Test
    fun executeRenamesAudioAndAssociatedFiles(@TempDir tempDir: Path) {
        val audioFile = tempDir.resolve("track_old.mp3").toFile()
        audioFile.writeText("dummy audio content")

        val lrcFile = tempDir.resolve("track_old.lrc").toFile()
        lrcFile.writeText("[00:01.00]Lyric line")

        val coverFile = tempDir.resolve("track_old.jpg").toFile()
        coverFile.writeText("dummy image data")

        val track = AudioMetadata(
            filePath = audioFile.absolutePath,
            fileName = audioFile.name,
            fileSize = audioFile.length(),
            lastModified = audioFile.lastModified(),
            format = "MP3",
            title = "夜曲",
            artist = "周杰伦",
        )

        val renamedCount = BatchRenameExecutor.execute(
            targets = listOf(track),
            pattern = "@2 - @1",
            renameAssociated = true,
        )

        assertEquals(1, renamedCount)

        val newAudio = File(tempDir.toFile(), "周杰伦 - 夜曲.mp3")
        val newLrc = File(tempDir.toFile(), "周杰伦 - 夜曲.lrc")
        val newCover = File(tempDir.toFile(), "周杰伦 - 夜曲.jpg")

        assertTrue(newAudio.exists())
        assertTrue(newLrc.exists())
        assertTrue(newCover.exists())

        assertFalse(audioFile.exists())
        assertFalse(lrcFile.exists())
        assertFalse(coverFile.exists())
    }

    @Test
    fun executeRenamesOnlyAudioWhenAssociatedFalse(@TempDir tempDir: Path) {
        val audioFile = tempDir.resolve("track_old2.flac").toFile()
        audioFile.writeText("dummy audio content")

        val lrcFile = tempDir.resolve("track_old2.lrc").toFile()
        lrcFile.writeText("[00:01.00]Lyric line")

        val track = AudioMetadata(
            filePath = audioFile.absolutePath,
            fileName = audioFile.name,
            fileSize = audioFile.length(),
            lastModified = audioFile.lastModified(),
            format = "FLAC",
            title = "青花瓷",
            artist = "周杰伦",
        )

        val renamedCount = BatchRenameExecutor.execute(
            targets = listOf(track),
            pattern = "@2 - @1",
            renameAssociated = false,
        )

        assertEquals(1, renamedCount)

        val newAudio = File(tempDir.toFile(), "周杰伦 - 青花瓷.flac")
        assertTrue(newAudio.exists())
        assertFalse(audioFile.exists())

        // LRC should NOT be renamed
        assertTrue(lrcFile.exists())
    }

    @Test
    fun executeHandlesNonExistentFileGracefully() {
        val track = AudioMetadata(
            filePath = "/non_existent/path/dummy.mp3",
            fileName = "dummy.mp3",
            fileSize = 0L,
            lastModified = 0L,
            format = "MP3",
            title = "Test",
            artist = "Artist",
        )

        val renamedCount = BatchRenameExecutor.execute(
            targets = listOf(track),
            pattern = "@2 - @1",
            renameAssociated = true,
        )

        assertEquals(0, renamedCount)
    }
}
