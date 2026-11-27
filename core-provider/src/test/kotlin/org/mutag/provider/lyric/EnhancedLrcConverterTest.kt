package org.mutag.provider.lyric

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mutag.provider.qqmusic.QrcDecoder

class EnhancedLrcConverterTest {
    @Test
    fun `fromQrc parses XML wrapped QRC and generates enhanced LRC with translation`() {
        val qrcXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <QrcInfos>
                <LyricInfo LyricCount="1">
                    <Lyric_1 LyricType="1" LyricContent="[1000,1500]晴(1000,500)天(1500,1000)&#10;[3000,1000]Hello &amp; World(3000,1000)"/>
                </LyricInfo>
            </QrcInfos>
        """.trimIndent()
        val trans = "[00:01.00]Sunny Day"

        val synced = EnhancedLrcConverter.fromQrc(qrcXml.replace("&#10;", "\n"), trans)
        assertNotNull(synced)
        requireNotNull(synced)

        assertTrue(synced.hasTranslation)
        assertEquals("[00:01.00]晴天\n[00:03.00]Hello & World", synced.rawLyricText)
        assertEquals("[00:01.00]<00:01.00>晴<00:01.50>天<00:02.50>\n[00:01.00]Sunny Day\n[00:03.00]<00:03.00>Hello & World<00:04.00>", synced.enhancedLrcText)
        assertEquals(synced.enhancedLrcText, synced.preferredText(useEnhanced = true))
        assertEquals(synced.mergedLrcText, synced.preferredText(useEnhanced = false))
    }

    @Test
    fun `fromYrc parses credit JSON and word timings into enhanced LRC`() {
        val yrc = """
            {"t":0,"c":[{"tx":"作词: "},{"tx":"一二三"}]}
            [27500,1000](27500,390,0)適(27890,160,0)当(28050,450,0)に
        """.trimIndent()
        val ytlrc = "[00:27.50]随便"

        val synced = EnhancedLrcConverter.fromYrc(yrc, ytlrc)
        assertNotNull(synced)
        requireNotNull(synced)

        assertTrue(synced.hasTranslation)
        assertEquals("[00:00.00]作词: 一二三\n[00:27.50]適当に", synced.rawLyricText)
        assertEquals(
            "[00:00.00]作词: 一二三\n[00:27.50]<00:27.50>適<00:27.89>当<00:28.05>に<00:28.50>\n[00:27.50]随便",
            synced.enhancedLrcText,
        )
    }

    @Test
    fun `fromQrc and fromYrc return null for plain LRC`() {
        val plainLrc = "[00:01.00]普通歌词"
        assertNull(EnhancedLrcConverter.fromQrc(plainLrc, null))
        assertNull(EnhancedLrcConverter.fromYrc(plainLrc, null))
    }

    @Test
    fun `QrcDecoder returns empty string for invalid input`() {
        assertEquals("", QrcDecoder.decodeQrc(null))
        assertEquals("", QrcDecoder.decodeQrc(""))
        assertEquals("", QrcDecoder.decodeQrc("ABC"))
    }
}
