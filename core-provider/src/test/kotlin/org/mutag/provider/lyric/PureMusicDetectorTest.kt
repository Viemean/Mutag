package org.mutag.provider.lyric

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PureMusicDetectorTest {

    @Test
    fun detectsEmptyOrBlankLyricsAsPureMusic() {
        assertTrue(PureMusicDetector.isPureMusic(null))
        assertTrue(PureMusicDetector.isPureMusic(""))
        assertTrue(PureMusicDetector.isPureMusic("   \n  \t"))
    }

    @Test
    fun detectsNeteasePureMusicFormat() {
        val neteaseLrc = "[00:00.00] 纯音乐，请欣赏\n"
        assertTrue(PureMusicDetector.isPureMusic(neteaseLrc))

        val neteaseOld = "[99:00.00]纯音乐\n"
        assertTrue(PureMusicDetector.isPureMusic(neteaseOld))

        val neteaseWithComposer = """
            [00:00.00] 作曲 : Jay Chou
            [00:05.00]纯音乐，请欣赏
        """.trimIndent()
        assertTrue(PureMusicDetector.isPureMusic(neteaseWithComposer))

        val onlyCredits = """
            [00:00.00] 作词 : 方文山
            [00:01.00] 作曲 : 周杰伦
            [00:02.00] 编曲 : 洪敬尧
        """.trimIndent()
        assertTrue(PureMusicDetector.isPureMusic(onlyCredits))
    }

    @Test
    fun detectsQqMusicPureMusicFormat() {
        val qqLrc = """
            [ti:纯音乐]
            [ar:]
            [al:]
            [by:]
            [offset:0]
            [00:00.00]此歌曲为没有填词的纯音乐，请您欣赏
        """.trimIndent()
        assertTrue(PureMusicDetector.isPureMusic(qqLrc))
    }

    @Test
    fun recognizesNormalLyricsAsNotPureMusic() {
        val normalLrc = """
            [00:00.00] 作词 : 方文山
            [00:01.00] 作曲 : 周杰伦
            [00:04.00]一盏离愁孤单伫立在窗口
            [00:09.00]我在门后假装你还没走
        """.trimIndent()
        assertFalse(PureMusicDetector.isPureMusic(normalLrc))
    }
}
