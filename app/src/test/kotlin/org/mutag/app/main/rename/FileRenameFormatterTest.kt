package org.mutag.app.main.rename

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mutag.core.tagger.scanner.AudioMetadata

class FileRenameFormatterTest {

    private val sampleTrack = AudioMetadata(
        filePath = "/dummy/Music/sample_raw.mp3",
        fileName = "sample_raw.mp3",
        fileSize = 1024L,
        lastModified = 1000L,
        format = "MP3",
        title = "Demons",
        artist = "Imagine Dragons",
        album = "Night Visions",
        albumArtist = "Imagine Dragons",
        trackNumber = 4,
        trackTotal = 12,
        discNumber = 1,
        discTotal = 1,
        publishDate = "2012-09-04",
        comment = "Hits",
    )

    @Test
    fun formatPresetArtistHyphenTitle() {
        val result = FileRenameFormatter.formatNewFileName(sampleTrack, "@2 - @1")
        assertEquals("Imagine Dragons - Demons.mp3", result)
    }

    @Test
    fun formatPresetTitleHyphenArtist() {
        val result = FileRenameFormatter.formatNewFileName(sampleTrack, "@1 - @2")
        assertEquals("Demons - Imagine Dragons.mp3", result)
    }

    @Test
    fun formatPresetTrackNoDotTitle() {
        val result = FileRenameFormatter.formatNewFileName(sampleTrack, "@5. @1")
        assertEquals("04. Demons.mp3", result)
    }

    @Test
    fun formatPresetTrackNoDotArtistHyphenTitle() {
        val result = FileRenameFormatter.formatNewFileName(sampleTrack, "@5. @2 - @1")
        assertEquals("04. Imagine Dragons - Demons.mp3", result)
    }

    @Test
    fun formatPresetDiscAndTrackDotTitle() {
        val result = FileRenameFormatter.formatNewFileName(sampleTrack, "@4@5. @1")
        assertEquals("104. Demons.mp3", result)
    }

    @Test
    fun formatPresetDiscAndTrackDotArtistHyphenTitle() {
        val result = FileRenameFormatter.formatNewFileName(sampleTrack, "@4@5. @2 - @1")
        assertEquals("104. Imagine Dragons - Demons.mp3", result)
    }

    @Test
    fun formatReplacesIllegalCharacters() {
        val dirtyTrack = sampleTrack.copy(
            title = "What/If: True? *Yes*",
        )
        val result = FileRenameFormatter.formatNewFileName(dirtyTrack, "@1")
        assertEquals("What_If_ True_ _Yes_.mp3", result)
    }

    @Test
    fun formatFallbacksToOriginalBaseNameWhenResultBlank() {
        val emptyTrack = sampleTrack.copy(title = "", artist = "")
        val result = FileRenameFormatter.formatNewFileName(emptyTrack, "@1@2")
        assertEquals("sample_raw.mp3", result)
    }
}
