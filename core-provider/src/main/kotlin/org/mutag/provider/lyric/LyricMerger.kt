package org.mutag.provider.lyric

import java.util.regex.Pattern
import org.mutag.model.LyricLine
import org.mutag.model.SyncedLyrics

/**
 * 原文歌词与翻译歌词逐行时间戳对齐合并工具。
 */
object LyricMerger {
    private val TIME_TAG_REGEX = Pattern.compile("""\[(\d{1,2}):(\d{2})(?:\.(\d{2,3}))?\]""")

    /**
     * 解析 LRC 时间标签为毫秒时间戳。
     *
     * @param timeTag 方括号时间戳标签字符串
     * @return 毫秒时间戳，解析失败返回 null
     */
    fun parseTimestampMs(timeTag: String): Long? {
        val matcher = TIME_TAG_REGEX.matcher(timeTag)
        if (!matcher.matches()) return null
        val min = matcher.group(1)?.toLongOrNull() ?: 0L
        val sec = matcher.group(2)?.toLongOrNull() ?: 0L
        val fracStr = matcher.group(3) ?: "0"
        val fracMs = when (fracStr.length) {
            1 -> fracStr.toLong() * 100
            2 -> fracStr.toLong() * 10
            3 -> fracStr.toLong()
            else -> fracStr.take(3).toLong()
        }
        return min * 60_000L + sec * 1_000L + fracMs
    }

    /**
     * 将 LRC 歌词文本切分解析为时间轴排序的歌词单行列表。
     *
     * @param lrc 原始 LRC 文本
     * @return 结构化单行歌词数据列表
     */
    fun parseLines(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        if (lrc.isBlank()) return lines

        for (rawLine in lrc.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            val matcher = TIME_TAG_REGEX.matcher(trimmed)
            val timestamps = mutableListOf<Long>()
            var lastIndex = 0

            while (matcher.find()) {
                val ts = parseTimestampMs(matcher.group())
                if (ts != null) {
                    timestamps.add(ts)
                }
                lastIndex = matcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val text = trimmed.substring(lastIndex).trim()
                for (ts in timestamps) {
                    lines.add(LyricLine(timestampMs = ts, text = text))
                }
            }
        }

        return lines.sortedBy { it.timestampMs }
    }

    /**
     * 将毫秒时间戳格式化为分秒毫秒标准时间标签字符串。
     *
     * @param ms 毫秒数值
     * @return 格式化后的分秒字符串
     */
    fun formatTimestamp(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val hundredths = (ms % 1000) / 10
        return "%02d:%02d.%02d".format(min, sec, hundredths)
    }

    /**
     * 将原文歌词与翻译歌词按时间戳双行对齐合并。
     *
     * @param rawLrc 原文歌词文本
     * @param transLrc 可选的译文歌词文本
     * @return 包含单行列表与双行合并文本的同步歌词对象
     */
    fun merge(rawLrc: String, transLrc: String?): SyncedLyrics {
        val rawLines = parseLines(rawLrc)
        val transLines = transLrc?.let { parseLines(it) } ?: emptyList()

        if (transLines.isEmpty()) {
            return SyncedLyrics(
                rawLyricText = rawLrc,
                lines = rawLines,
                hasTranslation = false,
                mergedLrcText = rawLrc,
            )
        }

        val transMap = mutableMapOf<Long, String>()
        for (tl in transLines) {
            if (tl.text.isNotBlank()) {
                transMap[tl.timestampMs] = tl.text
            }
        }

        val mergedLines = mutableListOf<LyricLine>()
        val mergedTextBuilder = StringBuilder()

        for (rl in rawLines) {
            var matchedTrans = transMap[rl.timestampMs]
            if (matchedTrans == null) {
                // Fuzzy match within 300ms
                val candidate = transMap.entries.firstOrNull { Math.abs(it.key - rl.timestampMs) <= 300 }
                if (candidate != null) {
                    matchedTrans = candidate.value
                }
            }

            val finalTrans = matchedTrans ?: ""
            mergedLines.add(rl.copy(transText = finalTrans))

            val timeTag = "[${formatTimestamp(rl.timestampMs)}]"
            mergedTextBuilder.append(timeTag).append(rl.text).append("\n")
            if (finalTrans.isNotBlank()) {
                mergedTextBuilder.append(timeTag).append(finalTrans).append("\n")
            }
        }

        return SyncedLyrics(
            rawLyricText = rawLrc,
            lines = mergedLines,
            hasTranslation = transLines.any { it.text.isNotBlank() },
            mergedLrcText = mergedTextBuilder.toString().trimEnd(),
        )
    }
}
