package org.mutag.core.tagger.scanner

import java.io.File
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AudioCoverExtractorTest {

    @Test
    fun extractNonExistentFileReturnsNull() {
        val file = File("/dummy/non_existent.flac")
        assertNull(AudioCoverExtractor.extractCoverBytes(file))
    }

    @Test
    fun extractEmptyFileReturnsNull(@TempDir tempDir: Path) {
        val file = tempDir.resolve("empty.mp3").toFile()
        file.createNewFile()
        assertNull(AudioCoverExtractor.extractCoverBytes(file))
    }
}
