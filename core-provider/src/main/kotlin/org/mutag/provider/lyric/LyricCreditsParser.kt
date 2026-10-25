package org.mutag.provider.lyric

/**
 * 歌词文本制作人员署名信息容器。
 *
 * @param lyricist 作词人
 * @param composer 作曲人
 * @param arranger 编曲人
 * @param producer 制作人
 */
data class LyricCredits(val lyricist: String = "", val composer: String = "", val arranger: String = "", val producer: String = "")

/**
 * 歌词头部非正文制作人员署名标签正则解析工具。
 */
object LyricCreditsParser {
    private val TIME_TAG_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{2,3}))?\]""")

    private enum class CreditType {
        LYRICIST,
        COMPOSER,
        BOTH_LYRICIST_COMPOSER,
        ARRANGER,
        PRODUCER,
        IGNORED_METADATA,
        UNKNOWN,
    }

    private val SENTENCE_END_PUNCTUATION = charArrayOf('。', '！', '!', '？', '?', '…')

    private val INVALID_PLACEHOLDERS = setOf(
        "无",
        "暂无",
        "none",
        "unknown",
        "//",
        "-",
        "nil",
        "null",
    )

    /**
     * 解析 LRC 歌词文本中的作词、作曲、编曲与制作人署名信息。
     *
     * @param lrcText 原始歌词文本
     * @return 解析提取的署名元数据对象
     */
    fun parse(lrcText: String): LyricCredits {
        if (lrcText.isBlank()) return LyricCredits()

        var lyricist = ""
        var composer = ""
        var arranger = ""
        var producer = ""

        var substantiveLyricLinesCount = 0

        for (rawLine in lrcText.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line == "//") continue

            val timeMatcher = TIME_TAG_REGEX.find(line)
            val timestampMs = timeMatcher?.let { match ->
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                min * 60_000L + sec * 1_000L
            }

            // 时间轴超过 60 秒时转入正文歌词阶段
            if (timestampMs != null && timestampMs > 60_000L) {
                break
            }

            val content = line.replace(TIME_TAG_REGEX, "").trim()
            if (content.isEmpty() || content == "//") continue

            // 解析标准 LRC 头部标签
            if (content.startsWith("[") && content.endsWith("]")) {
                continue
            }

            val colonIndex = content.indexOfAny(charArrayOf(':', '：'))
            if (colonIndex > 0) {
                val rawKey = content.substring(0, colonIndex).trim()
                var rawValue = content.substring(colonIndex + 1).trim()
                if (rawValue.endsWith("//")) {
                    rawValue = rawValue.removeSuffix("//").trim()
                }

                val creditType = normalizeKey(rawKey)
                if (creditType != CreditType.UNKNOWN) {
                    if (isValidValue(rawValue)) {
                        when (creditType) {
                            CreditType.LYRICIST -> {
                                if (lyricist.isBlank()) lyricist = rawValue
                            }

                            CreditType.COMPOSER -> {
                                if (composer.isBlank()) composer = rawValue
                            }

                            CreditType.BOTH_LYRICIST_COMPOSER -> {
                                if (lyricist.isBlank()) lyricist = rawValue
                                if (composer.isBlank()) composer = rawValue
                            }

                            CreditType.ARRANGER -> {
                                if (arranger.isBlank()) arranger = rawValue
                            }

                            CreditType.PRODUCER -> {
                                if (producer.isBlank()) producer = rawValue
                            }

                            CreditType.IGNORED_METADATA,
                            CreditType.UNKNOWN,
                            -> {
                            }
                        }
                    }
                    continue
                }
            }

            // Song Title - Artist header line
            if (content.contains(" - ") && substantiveLyricLinesCount == 0) {
                continue
            }

            substantiveLyricLinesCount++
            if (substantiveLyricLinesCount >= 2) {
                break
            }
        }

        return LyricCredits(
            lyricist = lyricist,
            composer = composer,
            arranger = arranger,
            producer = producer,
        )
    }

    private fun normalizeKey(rawKey: String): CreditType {
        val k = rawKey.replace(" ", "").lowercase()
        return when {
            k in setOf("词曲", "作词/作曲", "作曲/作词", "词/曲", "曲/词", "words&music", "wordsandmusic", "written&composedby") ->
                CreditType.BOTH_LYRICIST_COMPOSER

            k in
                setOf("作词", "作詞", "词", "詞", "填词", "填詞", "lyrics", "lyricist", "lyricsby", "words", "wordsby", "writtenby", "written-by", "text") ->
                CreditType.LYRICIST

            k in setOf("作曲", "曲", "谱曲", "譜曲", "composer", "music", "musicby", "composedby", "tune") ->
                CreditType.COMPOSER

            k in setOf("编曲", "編曲", "arranger", "arrangement", "arrangedby") ->
                CreditType.ARRANGER

            k in setOf("制作人", "製作人", "音乐制作", "音乐制作人", "监制", "監製", "producer", "producedby", "executiveproducer") ->
                CreditType.PRODUCER

            k in
                setOf("歌手", "演唱", "原唱", "录音", "混音", "母带", "吉他", "贝斯", "鼓", "键盘", "弦乐", "合音", "和声", "发行", "出品", "企划", "统筹", "op", "sp", "vocal", "vocals", "artist", "mixing", "mastering", "guitar", "bass", "drums", "piano") ->
                CreditType.IGNORED_METADATA

            else -> CreditType.UNKNOWN
        }
    }

    private fun isValidValue(value: String): Boolean {
        if (value.length !in 1..200) return false
        val lower = value.lowercase()
        if (lower in INVALID_PLACEHOLDERS) return false
        if (value.contains("...")) return false
        if (value.any { it in SENTENCE_END_PUNCTUATION }) return false
        return true
    }
}
