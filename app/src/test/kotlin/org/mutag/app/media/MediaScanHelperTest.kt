package org.mutag.app.media

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * 媒体库扫描辅助工具单元测试套件。
 */
class MediaScanHelperTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun collectAudioFilesFiltersNonAudioAndRecordings() {
        val song1 = File(tempDir, "song1.mp3").apply { writeBytes(ByteArray(10)) }
        val song2 = File(tempDir, "song2.flac").apply { writeBytes(ByteArray(10)) }
        val doc = File(tempDir, "lyrics.txt").apply { writeText("dummy") }
        assertTrue(doc.exists())

        val subDir = File(tempDir, "album").apply { mkdirs() }
        val song3 = File(subDir, "song3.mp3").apply { writeBytes(ByteArray(10)) }

        val recDir = File(tempDir, "Recordings").apply { mkdirs() }
        File(recDir, "call_record.mp3").apply { writeBytes(ByteArray(10)) }

        val outList = mutableListOf<String>()
        val collectMethod = MediaScanHelper::class.java.getDeclaredMethod(
            "collectAudioFilesRecursively",
            File::class.java,
            MutableList::class.java,
        )
        collectMethod.isAccessible = true
        collectMethod.invoke(MediaScanHelper, tempDir, outList)

        assertEquals(3, outList.size)
        assertTrue(outList.contains(song1.absolutePath))
        assertTrue(outList.contains(song2.absolutePath))
        assertTrue(outList.contains(song3.absolutePath))
    }
}
