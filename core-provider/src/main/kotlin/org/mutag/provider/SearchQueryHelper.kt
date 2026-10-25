package org.mutag.provider

import kotlin.math.abs
import org.mutag.model.TrackMetadata

/**
 * 音乐元数据检索词清洗、多路检索规划与候选消歧排序工具。
 */
object SearchQueryHelper {

    private val CLUTTER_REGEX = Regex(
        """[\[(][^\])]*?(?:C\d+|M3[\s-]*\d+|FLAC|Hi-?Res|Lossless|24bit|\d+kHz|16bit|EAC|APE|WAV|MP3|Disc\s*\d+|CD\s*\d+|Bonus\s*Track|Explicit)[^\])]*?[\])]""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 清洗文本中的版本杂质、音频格式标签与多余括号标记。
     *
     * @param text 输入文本
     * @return 过滤清洗后的纯净文本
     */
    fun sanitize(text: String): String {
        if (text.isBlank()) return ""
        val withoutClutter = text.replace(CLUTTER_REGEX, " ")
        return withoutClutter
            .replace('_', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * 针对单曲构建的多路检索规划方案。
     *
     * @param primaryQuery 主路搜索词
     * @param secondaryQueries 备选回退搜索词列表
     * @param quickChips 供界面展示的快捷检索标签对列表
     */
    data class SearchPlan(val primaryQuery: String, val secondaryQueries: List<String>, val quickChips: List<Pair<String, String>>)

    /**
     * 基于已有音频元数据规划主选与备选搜索词。
     *
     * @param title 歌曲标题
     * @param artist 艺术家
     * @param album 专辑名
     * @param albumArtist 专辑艺术家
     * @return 多路检索规划对象
     */
    fun buildPlan(title: String, artist: String, album: String, albumArtist: String): SearchPlan {
        val cleanTitle = sanitize(title)
        val cleanArtist = sanitize(artist)
        val cleanAlbum = sanitize(album)
        val cleanAlbumArtist = sanitize(albumArtist)

        val chips = mutableListOf<Pair<String, String>>()
        val secondary = mutableListOf<String>()

        val titleArtist = listOf(cleanTitle, cleanArtist).filter { it.isNotBlank() }.joinToString(" ")
        if (titleArtist.isNotBlank()) {
            chips.add("歌曲 + 歌手" to titleArtist)
        }

        if (cleanAlbum.isNotBlank() && !cleanAlbum.equals(cleanTitle, ignoreCase = true)) {
            val titleAlbum = listOf(cleanTitle, cleanAlbum).filter { it.isNotBlank() }.joinToString(" ")
            chips.add("歌曲 + 专辑" to titleAlbum)
            secondary.add(titleAlbum)
        }

        if (cleanAlbumArtist.isNotBlank() && !cleanAlbumArtist.equals(cleanArtist, ignoreCase = true)) {
            val titleAlbumArtist = listOf(cleanTitle, cleanAlbumArtist).filter { it.isNotBlank() }.joinToString(" ")
            chips.add("歌曲 + 专辑艺术家" to titleAlbumArtist)
            secondary.add(titleAlbumArtist)
        }

        if (cleanTitle.isNotBlank() && cleanTitle != titleArtist) {
            chips.add("仅歌曲" to cleanTitle)
        }

        // 剥离开头音轨序号生成备选词（如 "01. 歌曲名" -> "歌曲名"）
        val titleWithoutTrack = cleanTitle.replace(Regex("""^\d{1,3}[\s._-]+"""), "").trim()
        if (titleWithoutTrack.isNotBlank() && titleWithoutTrack != cleanTitle) {
            val secTitleArtist = listOf(titleWithoutTrack, cleanArtist).filter { it.isNotBlank() }.joinToString(" ")
            if (secTitleArtist.isNotBlank()) secondary.add(secTitleArtist)
            secondary.add(titleWithoutTrack)
        }

        val primary = titleArtist.ifBlank { cleanTitle }

        return SearchPlan(
            primaryQuery = primary,
            secondaryQueries = secondary,
            quickChips = chips,
        )
    }

    /**
     * 依据时长吻合度、音轨号及专辑名对搜索候选进行加权排序并去除重复项。
     *
     * @param candidates 在线搜索返回的候选曲目列表
     * @param targetDurationMs 目标本地音频时长（毫秒）
     * @param targetTrackNumber 目标本地音轨编号
     * @param targetAlbum 目标本地专辑名称
     * @return 加权排序并去重后的候选列表
     */
    fun rankAndDeduplicate(
        candidates: List<TrackMetadata>,
        targetDurationMs: Long = 0L,
        targetTrackNumber: Int = 0,
        targetAlbum: String = "",
    ): List<TrackMetadata> {
        val cleanTargetAlbum = sanitize(targetAlbum).lowercase()

        val scoredList = candidates.mapIndexed { index, track ->
            var score = 100 - index

            if (targetDurationMs > 0 && track.durationMs > 0) {
                val diff = abs(track.durationMs - targetDurationMs)
                when {
                    diff <= 3000L -> score += 60
                    diff <= 8000L -> score += 25
                    diff > 35000L -> score -= 30
                }
            }

            if (targetTrackNumber > 0 && track.trackNumber > 0) {
                if (track.trackNumber == targetTrackNumber) {
                    score += 40
                }
            }

            if (cleanTargetAlbum.isNotBlank() && track.album.name.isNotBlank()) {
                val candAlbum = sanitize(track.album.name).lowercase()
                if (candAlbum == cleanTargetAlbum) {
                    score += 50
                } else if (candAlbum.contains(cleanTargetAlbum) || cleanTargetAlbum.contains(candAlbum)) {
                    score += 30
                }
            }

            track to score
        }

        val bestByTrackId = mutableMapOf<String, Pair<TrackMetadata, Int>>()
        for ((track, score) in scoredList) {
            val existing = bestByTrackId[track.trackId]
            if (existing == null || score > existing.second) {
                bestByTrackId[track.trackId] = track to score
            }
        }

        return bestByTrackId.values
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * 评估候选匹配结果的置信度等级并输出判定原因。
     *
     * @param targetDurationMs 目标本地音频时长
     * @param targetTitle 目标歌曲标题
     * @param targetArtist 目标艺术家
     * @param targetAlbum 目标专辑名称
     * @param candidate 在线匹配候选
     * @param isAcoustic 是否通过声学指纹直接识别
     * @param targetAlbumArtist 目标专辑艺术家
     * @return 置信度评估结论实体
     */
    fun evaluateMatch(
        targetDurationMs: Long,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String,
        candidate: TrackMetadata?,
        isAcoustic: Boolean = false,
        targetAlbumArtist: String = "",
    ): EvaluatedMatch {
        if (candidate == null) {
            return EvaluatedMatch(null, MatchConfidence.FAILED, "未搜索到匹配曲目")
        }

        if (isAcoustic) {
            val durationDiff = if (targetDurationMs > 0 && candidate.durationMs > 0) {
                abs(targetDurationMs - candidate.durationMs)
            } else {
                -1L
            }

            if (durationDiff > 5000L) {
                val sec = durationDiff / 1000
                return EvaluatedMatch(candidate, MatchConfidence.UNCERTAIN, "声学匹配时长差异较大 (${sec}秒)，疑似不同版本")
            }

            // 1. 检测本地标题/文件名是否含有翻唱信息
            val coverKeywords = listOf("cover", "covered", "翻唱", "歌ってみた")
            val targetHasCover = coverKeywords.any { targetTitle.contains(it, ignoreCase = true) }
            val candHasCover = coverKeywords.any { candidate.title.contains(it, ignoreCase = true) }

            if (targetHasCover && !candHasCover) {
                return EvaluatedMatch(
                    candidate,
                    MatchConfidence.UNCERTAIN,
                    "匹配疑似不符: 本地包含翻唱标记 / 云端为「${candidate.artistName}」",
                )
            }

            val cleanTargetTitle = sanitize(targetTitle).lowercase()
            val cleanCandTitle = sanitize(candidate.title).lowercase()
            val cleanTargetArtist = sanitize(targetArtist).lowercase()
            val cleanCandArtist = sanitize(candidate.artistName).lowercase()
            val cleanTargetAlbum = sanitize(targetAlbum).lowercase()
            val cleanCandAlbum = sanitize(candidate.album.name).lowercase()
            val cleanTargetAlbumArtist = sanitize(targetAlbumArtist).lowercase()

            val hasLocalMeta = cleanTargetTitle.isNotBlank() ||
                cleanTargetArtist.isNotBlank() ||
                cleanTargetAlbum.isNotBlank() ||
                cleanTargetAlbumArtist.isNotBlank()

            // 2. 本地无标签裸文件时，声学指纹且时长在 5s 内直接采纳
            if (!hasLocalMeta) {
                return EvaluatedMatch(candidate, MatchConfidence.HIGH, "识别结果吻合 (无本地标签)")
            }

            // 3. 标题重合检测
            val titleMatch = cleanTargetTitle.isNotBlank() && cleanCandTitle.isNotBlank() && (
                cleanCandTitle.contains(cleanTargetTitle) || cleanTargetTitle.contains(cleanCandTitle)
                )

            // 4. 专辑重合检测
            val albumMatch = cleanTargetAlbum.isNotBlank() && cleanCandAlbum.isNotBlank() && (
                cleanCandAlbum.contains(cleanTargetAlbum) || cleanTargetAlbum.contains(cleanCandAlbum)
                )

            // 5. 歌手/专辑艺术家重合检测
            fun artistMatches(target: String): Boolean {
                if (target.isBlank() || target == "various artists" || cleanCandArtist.isBlank()) return false
                return cleanCandArtist.contains(target) ||
                    target.contains(cleanCandArtist) ||
                    target.split("/", "&", ",", ";", "、").any { sub ->
                        val s = sub.trim()
                        s.isNotBlank() && cleanCandArtist.contains(s)
                    }
            }
            val artistMatch = artistMatches(cleanTargetArtist)
            val albumArtistMatch = artistMatches(cleanTargetAlbumArtist)

            // 6. 只要 标题、专辑、歌手、专辑艺术家 中任意一个吻合，直接判为 HIGH
            if (titleMatch || albumMatch || artistMatch || albumArtistMatch) {
                return EvaluatedMatch(candidate, MatchConfidence.HIGH, "识别吻合 (多维度校验通过)")
            }

            // 7. 本地有明确元数据但与云端全部无交集
            return EvaluatedMatch(
                candidate,
                MatchConfidence.UNCERTAIN,
                "识别歌手不符: 本地「$targetArtist」/ 云端「${candidate.artistName}」",
            )
        }

        val durationDiff = if (targetDurationMs > 0 && candidate.durationMs > 0) {
            abs(targetDurationMs - candidate.durationMs)
        } else {
            -1L
        }

        if (durationDiff > 8000L) {
            val sec = durationDiff / 1000
            return EvaluatedMatch(candidate, MatchConfidence.UNCERTAIN, "时长相差较大 (${sec}秒)，疑似不同版本")
        }

        val cleanTargetArtist = sanitize(targetArtist).lowercase()
        val cleanCandArtist = sanitize(candidate.artistName).lowercase()
        if (cleanTargetArtist.isNotBlank() && cleanCandArtist.isNotBlank() && cleanTargetArtist != "various artists") {
            val artistsOverlap = cleanCandArtist.contains(cleanTargetArtist) ||
                cleanTargetArtist.contains(cleanCandArtist) ||
                cleanTargetArtist.split("/", "&", ",", ";", "、").any { sub ->
                    val s = sub.trim()
                    s.isNotBlank() && cleanCandArtist.contains(s)
                }

            if (!artistsOverlap) {
                return EvaluatedMatch(
                    candidate,
                    MatchConfidence.UNCERTAIN,
                    "歌手不符: 本地「$targetArtist」/ 云端「${candidate.artistName}」",
                )
            }
        }

        if (durationDiff in 0..3500L) {
            return EvaluatedMatch(candidate, MatchConfidence.HIGH, "匹配吻合 (时长差 ${durationDiff / 1000}s)")
        }

        if (durationDiff in 3501L..6000L) {
            val cleanTargetAlbum = sanitize(targetAlbum).lowercase()
            val cleanCandAlbum = sanitize(candidate.album.name).lowercase()
            val albumMatch =
                cleanTargetAlbum.isNotBlank() && (cleanCandAlbum.contains(cleanTargetAlbum) || cleanTargetAlbum.contains(cleanCandAlbum))
            return if (albumMatch || cleanTargetArtist.isNotBlank()) {
                EvaluatedMatch(candidate, MatchConfidence.HIGH, "基本吻合 (时长差 ${durationDiff / 1000}s)")
            } else {
                EvaluatedMatch(candidate, MatchConfidence.UNCERTAIN, "时长相差 ${durationDiff / 1000}s，请核对版本")
            }
        }

        return EvaluatedMatch(candidate, MatchConfidence.HIGH, "基本吻合")
    }
}

/**
 * 在线曲目匹配置信度评级枚举。
 */
enum class MatchConfidence {
    HIGH,
    UNCERTAIN,
    FAILED,
}

/**
 * 候选匹配评估判定结果实体。
 *
 * @param candidate 匹配获得的云端候选曲目
 * @param confidence 置信度判定级别
 * @param reason 判定原由说明
 */
data class EvaluatedMatch(val candidate: TrackMetadata?, val confidence: MatchConfidence, val reason: String = "")
