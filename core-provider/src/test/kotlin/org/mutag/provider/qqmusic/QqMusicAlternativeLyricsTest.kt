package org.mutag.provider.qqmusic

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QqMusicAlternativeLyricsTest {

    @Test
    fun testAlternativeLyricResolutionForCoverSongWithoutDirectLyrics() = runBlocking {
        val provider = QqMusicMetadataProvider()
        val trackId = "001jP3Y112F0x3" // ヰ世界情緒 - メテオ
        val result = provider.getLyrics(
            trackId = trackId,
            songIdLong = 0L,
            title = "メテオ",
            artist = "ヰ世界情緒",
            durationSec = 527,
        )

        assertTrue(result.isSuccess)
        val synced = result.getOrThrow()
        assertFalse(synced.lines.isEmpty())
        assertTrue(synced.hasTranslation)
        assertTrue(synced.lines.any { it.text.contains("星") || it.text.contains("メテオ") || it.text.contains("夜") })
    }
}
