package org.mutag.provider.lyric

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LyricCreditsParserTest {

    @Test
    fun parseUserJapaneseCaseExtractsHarukatomiyuki() {
        val lrc = """
            [00:00.99]17才 (17岁) - ハルカトミユキ
            [00:00.99]//
            [00:15.65]词：ハルカ
            [00:15.65]//
            [00:22.36]曲：ミユキ
            [00:22.36]//
            [00:29.54]たとえば
            [00:29.54]比如说
            [00:30.51]今日までの僕が壊された夜
        """.trimIndent()

        val credits = LyricCreditsParser.parse(lrc)
        assertEquals("ハルカ", credits.lyricist)
        assertEquals("ミユキ", credits.composer)
    }

    @Test
    fun parseChineseStandardAndCombinedCredits() {
        val lrc1 = """
            [00:01.00]晴天
            [00:02.00]作词：周杰伦
            [00:03.00]作曲：周杰伦
            [00:04.00]编曲：周杰伦
            [00:05.00]制作人：周杰伦
            [00:10.00]故事的小黄花
        """.trimIndent()

        val credits1 = LyricCreditsParser.parse(lrc1)
        assertEquals("周杰伦", credits1.lyricist)
        assertEquals("周杰伦", credits1.composer)
        assertEquals("周杰伦", credits1.arranger)
        assertEquals("周杰伦", credits1.producer)

        val lrc2 = """
            [00:01.00]词曲：李荣浩
            [00:02.00]编曲：李荣浩
            [00:10.00]要是能重来
        """.trimIndent()

        val credits2 = LyricCreditsParser.parse(lrc2)
        assertEquals("李荣浩", credits2.lyricist)
        assertEquals("李荣浩", credits2.composer)
        assertEquals("李荣浩", credits2.arranger)
    }

    @Test
    fun parseEnglishCreditsFormat() {
        val lrc = """
            [00:01.00]Imagine - John Lennon
            [00:02.00]Lyrics by: John Lennon
            [00:03.00]Music by: John Lennon
            [00:04.00]Arranged by: Phil Spector
            [00:05.00]Produced by: Phil Spector
            [00:10.00]Imagine there's no heaven
            [00:15.00]It's easy if you try
        """.trimIndent()

        val credits = LyricCreditsParser.parse(lrc)
        assertEquals("John Lennon", credits.lyricist)
        assertEquals("John Lennon", credits.composer)
        assertEquals("Phil Spector", credits.arranger)
        assertEquals("Phil Spector", credits.producer)
    }

    @Test
    fun doesNotFalsePositiveOnLyricsSentences() {
        val lrc = """
            [00:01.00]词不达意
            [00:02.00]曲终人散
            [00:03.00]她说：我们分手吧。
            [00:04.00]天空飘起小雨
        """.trimIndent()

        val credits = LyricCreditsParser.parse(lrc)
        assertEquals("", credits.lyricist)
        assertEquals("", credits.composer)
        assertEquals("", credits.arranger)
    }

    @Test
    fun ignoresEmptyPlaceholders() {
        val lrc = """
            [00:01.00]作词：无
            [00:02.00]作曲：None
            [00:03.00]纯音乐，请欣赏
        """.trimIndent()

        val credits = LyricCreditsParser.parse(lrc)
        assertEquals("", credits.lyricist)
        assertEquals("", credits.composer)
    }

    @Test
    fun parseImagineDragonsDemonsMultipleAuthors() {
        val lrc = """
            [00:00.00]Demons - Imagine Dragons (梦龙)
            [00:00.00]//
            [00:00.14]Lyrics by：Dan Reynolds/Wayne Sermon/Ben McKee/Alex Da Kid/Josh Mosser
            [00:00.14]//
            [00:00.29]Composed by：Josh Mosser/Alex Da Kid/Dan Reynolds/Wayne Sermon/Ben McKee
            [00:00.29]//
            [00:00.43]Arranged by：Alex da Kid
            [00:00.43]//
            [00:00.58]When the days are cold
            [00:00.58]当冰冷降临世间
        """.trimIndent()

        val credits = LyricCreditsParser.parse(lrc)
        assertEquals("Dan Reynolds/Wayne Sermon/Ben McKee/Alex Da Kid/Josh Mosser", credits.lyricist)
        assertEquals("Josh Mosser/Alex Da Kid/Dan Reynolds/Wayne Sermon/Ben McKee", credits.composer)
        assertEquals("Alex da Kid", credits.arranger)
    }
}
