package org.mutag.provider.lyric

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LyricMergerTest {

    @Test
    fun parseTimestampMsCorrectlyConvertsMinutesSecondsAndFractions() {
        assertEquals(0L, LyricMerger.parseTimestampMs("[00:00.00]"))
        assertEquals(72340L, LyricMerger.parseTimestampMs("[01:12.34]"))
        assertEquals(72345L, LyricMerger.parseTimestampMs("[01:12.345]"))
    }

    @Test
    fun parseLinesExtractsAllTimestampedLines() {
        val lrc = """
            [ti:晴天]
            [ar:周杰伦]
            [00:15.50]故事的小黄花
            [00:18.20]从出生那年就飘着
        """.trimIndent()

        val lines = LyricMerger.parseLines(lrc)
        assertEquals(2, lines.size)
        assertEquals(15500L, lines[0].timestampMs)
        assertEquals("故事的小黄花", lines[0].text)
        assertEquals(18200L, lines[1].timestampMs)
        assertEquals("从出生那年就飘着", lines[1].text)
    }

    @Test
    fun mergeBilingualLyricsAlignsTranslation() {
        val rawLrc = """
            [00:01.00]Hello world
            [00:05.00]Goodbye world
        """.trimIndent()

        val transLrc = """
            [00:01.00]你好世界
            [00:05.00]再见世界
        """.trimIndent()

        val merged = LyricMerger.merge(rawLrc, transLrc)
        assertTrue(merged.hasTranslation)
        assertEquals(2, merged.lines.size)
        assertEquals("你好世界", merged.lines[0].transText)
        assertEquals("再见世界", merged.lines[1].transText)
        assertTrue(merged.mergedLrcText.contains("Hello world"))
        assertTrue(merged.mergedLrcText.contains("你好世界"))
    }

    @Test
    fun mergeWithoutTranslationPreservesRaw() {
        val rawLrc = "[00:01.00]Just plain lyric"
        val merged = LyricMerger.merge(rawLrc, null)
        assertFalse(merged.hasTranslation)
        assertEquals(1, merged.lines.size)
        assertEquals("", merged.lines[0].transText)
    }
}
