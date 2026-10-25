package org.mutag.provider.lyric

/**
 * 纯音乐与无填词歌词文本识别检测器。
 */
object PureMusicDetector {

    private val PURE_MUSIC_KEYWORDS = listOf(
        "纯音乐",
        "没有填词",
        "请欣赏",
        "instrumental",
        "无歌词",
    )

    private val CREDIT_PREFIX_REGEX = Regex(
        """^(?:作\s*词|作\s*曲|编\s*曲|监\s*制|制作人?|混\s*音|录\s*音|母带|吉他|贝斯|钢琴|提琴|鼓手|和声|弦乐|企划|统筹|发行|OP|SP|arranger|producer|composer|lyricist|by|ar|ti|al|offset)\s*[:：]""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 判断给定的 LRC 歌词文本是否属于纯音乐或无有效填词的占位歌词。
     *
     * @param lrcText LRC 格式歌词文本
     * @return true 表示属于纯音乐或无填词占位，false 表示包含有效歌词内容
     */
    fun isPureMusic(lrcText: String?): Boolean {
        if (lrcText.isNullOrBlank()) return true

        val lines = LyricMerger.parseLines(lrcText)
        if (lines.isEmpty()) {
            val clean = lrcText.replace(Regex("""\[.*?\]"""), "").trim()
            if (clean.isBlank()) return true
            return PURE_MUSIC_KEYWORDS.any { clean.contains(it, ignoreCase = true) }
        }

        val validLines = lines.map { it.text.trim() }.filter { it.isNotBlank() }
        if (validLines.isEmpty()) return true

        // 过滤制作人与职能信息署名行
        val bodyLines = validLines.filterNot { CREDIT_PREFIX_REGEX.containsMatchIn(it) }

        // 去除署名后若无实际正文行，判定为纯音乐
        if (bodyLines.isEmpty()) return true

        // 正文行若全部包含纯音乐关键词，判定为纯音乐
        val nonPureLines = bodyLines.filterNot { line ->
            PURE_MUSIC_KEYWORDS.any { kw -> line.contains(kw, ignoreCase = true) }
        }

        return nonPureLines.isEmpty()
    }
}
