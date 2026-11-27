package org.mutag.provider.lyric

import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.mutag.model.LyricLine
import org.mutag.model.SyncedLyrics

/**
 * 逐字歌词（QQ 音乐 QRC / 网易云音乐 YRC）解析与增强型 LRC（Enhanced LRC）转换工具。
 */
object EnhancedLrcConverter {
    private val LINE_REGEX = Regex("""^\[(\d+),(\d+)\](.*)$""")
    private val QRC_WORD_REGEX = Regex("""(.*?)\((\d+),(\d+)\)""")
    private val YRC_WORD_REGEX = Regex("""\((\d+),(\d+),\d+\)([^\(\r\n]*)""")
    private val QRC_XML_CONTENT_REGEX = Regex("""LyricContent="([\s\S]*?)"""")

    private val json = Json { ignoreUnknownKeys = true }

    private data class WordTiming(val startMs: Long, val durationMs: Long, val text: String)

    private data class VerbatimLine(val startMs: Long, val endMs: Long, val plainText: String, val words: List<WordTiming>)

    /**
     * 解析 QQ 音乐 QRC（XML 或纯文本）并生成包含标准 LRC 与增强型 LRC 的 [SyncedLyrics]。
     *
     * @param qrcRaw 解密后的 QRC 原文
     * @param transLrc 可选的译文 LRC 文本
     * @return 解析成功返回 [SyncedLyrics]，若内容并非 QRC 逐字格式则返回 null
     */
    fun fromQrc(qrcRaw: String, transLrc: String?): SyncedLyrics? {
        if (qrcRaw.isBlank()) return null
        val content = extractQrcContent(qrcRaw)
        val parsedLines = parseQrcLines(content)
        if (parsedLines.none { it.words.isNotEmpty() }) return null
        return buildSyncedLyrics(parsedLines, transLrc)
    }

    /**
     * 解析网易云音乐 YRC 并生成包含标准 LRC 与增强型 LRC 的 [SyncedLyrics]。
     *
     * @param yrcRaw YRC 逐字歌词文本
     * @param transLrc 可选的译文 LRC 文本（优先 ytlrc，回退 tlyric）
     * @return 解析成功返回 [SyncedLyrics]，若内容并非 YRC 逐字格式则返回 null
     */
    fun fromYrc(yrcRaw: String, transLrc: String?): SyncedLyrics? {
        if (yrcRaw.isBlank()) return null
        val parsedLines = parseYrcLines(yrcRaw)
        if (parsedLines.none { it.words.isNotEmpty() }) return null
        return buildSyncedLyrics(parsedLines, transLrc)
    }

    private fun extractQrcContent(raw: String): String {
        val match = QRC_XML_CONTENT_REGEX.find(raw) ?: return raw
        return match.groupValues[1]
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun parseQrcLines(content: String): List<VerbatimLine> {
        val result = mutableListOf<VerbatimLine>()
        for (rawLine in content.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            val match = LINE_REGEX.matchEntire(trimmed) ?: continue

            val lineStart = match.groupValues[1].toLongOrNull() ?: continue
            val lineDur = match.groupValues[2].toLongOrNull() ?: 0L
            val body = match.groupValues[3]

            val words = mutableListOf<WordTiming>()
            val plainBuilder = StringBuilder()
            for (wMatch in QRC_WORD_REGEX.findAll(body)) {
                val wordText = wMatch.groupValues[1]
                val wStart = wMatch.groupValues[2].toLongOrNull() ?: lineStart
                val wDur = wMatch.groupValues[3].toLongOrNull() ?: 0L
                words.add(WordTiming(startMs = wStart, durationMs = wDur, text = wordText))
                plainBuilder.append(wordText)
            }

            if (words.isEmpty()) {
                val plain = body.trim()
                if (plain.isNotEmpty()) {
                    result.add(VerbatimLine(lineStart, lineStart + lineDur, plain, emptyList()))
                }
            } else {
                val endMs = maxOf(lineStart + lineDur, words.last().startMs + words.last().durationMs)
                result.add(VerbatimLine(lineStart, endMs, plainBuilder.toString(), words))
            }
        }
        return result.sortedBy { it.startMs }
    }

    private fun parseYrcLines(content: String): List<VerbatimLine> {
        val result = mutableListOf<VerbatimLine>()
        for (rawLine in content.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("{")) {
                parseYrcCreditLine(trimmed)?.let(result::add)
                continue
            }

            val match = LINE_REGEX.matchEntire(trimmed) ?: continue
            val lineStart = match.groupValues[1].toLongOrNull() ?: continue
            val lineDur = match.groupValues[2].toLongOrNull() ?: 0L
            val body = match.groupValues[3]

            val words = mutableListOf<WordTiming>()
            val plainBuilder = StringBuilder()
            for (wMatch in YRC_WORD_REGEX.findAll(body)) {
                val wStart = wMatch.groupValues[1].toLongOrNull() ?: lineStart
                val wDur = wMatch.groupValues[2].toLongOrNull() ?: 0L
                val wordText = wMatch.groupValues[3]
                words.add(WordTiming(startMs = wStart, durationMs = wDur, text = wordText))
                plainBuilder.append(wordText)
            }

            if (words.isEmpty()) {
                val plain = body.trim()
                if (plain.isNotEmpty()) {
                    result.add(VerbatimLine(lineStart, lineStart + lineDur, plain, emptyList()))
                }
            } else {
                val endMs = maxOf(lineStart + lineDur, words.last().startMs + words.last().durationMs)
                result.add(VerbatimLine(lineStart, endMs, plainBuilder.toString(), words))
            }
        }
        return result.sortedBy { it.startMs }
    }

    private fun parseYrcCreditLine(line: String): VerbatimLine? {
        val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return null
        val timestamp = obj["t"]?.jsonPrimitive?.longOrNull ?: return null
        val text = obj["c"]?.jsonArray
            ?.mapNotNull { item -> item.jsonObject["tx"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString("")
            ?.trim()
            .orEmpty()
        if (text.isEmpty()) return null
        return VerbatimLine(timestamp, timestamp, text, emptyList())
    }

    private fun buildSyncedLyrics(lines: List<VerbatimLine>, transLrc: String?): SyncedLyrics {
        val transMap = mutableMapOf<Long, String>()
        if (!transLrc.isNullOrBlank()) {
            for (tl in LyricMerger.parseLines(transLrc)) {
                if (tl.text.isNotBlank()) {
                    transMap[tl.timestampMs] = tl.text
                }
            }
        }

        val rawBuilder = StringBuilder()
        val mergedBuilder = StringBuilder()
        val enhancedBuilder = StringBuilder()
        val lyricLines = mutableListOf<LyricLine>()
        var hasTranslation = false

        for (line in lines) {
            val lineTag = "[${LyricMerger.formatTimestamp(line.startMs)}]"
            val plainLine = "$lineTag${line.plainText}"
            val enhancedLine = if (line.words.isNotEmpty()) {
                buildString {
                    append(lineTag)
                    for (w in line.words) {
                        append('<')
                        append(LyricMerger.formatTimestamp(w.startMs))
                        append('>')
                        append(w.text)
                    }
                    append('<')
                    append(LyricMerger.formatTimestamp(line.endMs))
                    append('>')
                }
            } else {
                plainLine
            }

            rawBuilder.appendLine(plainLine)
            mergedBuilder.appendLine(plainLine)
            enhancedBuilder.appendLine(enhancedLine)

            val matchedTrans = findMatchingTranslation(transMap, line.startMs)
            if (!matchedTrans.isNullOrBlank()) {
                hasTranslation = true
                val transLine = "$lineTag$matchedTrans"
                mergedBuilder.appendLine(transLine)
                enhancedBuilder.appendLine(transLine)
                lyricLines.add(LyricLine(timestampMs = line.startMs, text = line.plainText, transText = matchedTrans))
            } else {
                lyricLines.add(LyricLine(timestampMs = line.startMs, text = line.plainText))
            }
        }

        return SyncedLyrics(
            rawLyricText = rawBuilder.toString().trimEnd(),
            lines = lyricLines,
            hasTranslation = hasTranslation,
            mergedLrcText = mergedBuilder.toString().trimEnd(),
            enhancedLrcText = enhancedBuilder.toString().trimEnd(),
        )
    }

    private fun findMatchingTranslation(transMap: Map<Long, String>, timestampMs: Long): String? {
        if (transMap.isEmpty()) return null
        transMap[timestampMs]?.let { return it }
        return transMap.entries.firstOrNull { abs(it.key - timestampMs) <= 300 }?.value
    }
}
