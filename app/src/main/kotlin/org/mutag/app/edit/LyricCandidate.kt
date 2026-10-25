package org.mutag.app.edit

/**
 * 歌词搜索结果候选实体。
 *
 * @param id 唯一项标识符
 * @param trackId 音源平台的曲目 ID / MID
 * @param sourceId 音源平台标识（"qqmusic" 或 "netease"）
 * @param songIdLong 整型备用曲目 ID
 * @param title 歌曲名称
 * @param artist 艺术家名称
 * @param album 所属专辑名称
 * @param durationMs 音频总时长（毫秒）
 * @param lyrics 异步拉取后的完整 LRC 歌词文本，未拉取时为 null
 * @param isPureMusic 是否为纯音乐或无有效填词
 * @param isLoadingLyrics 歌词是否正在异步加载中
 * @param isExpanded 歌词预览区域是否展开
 */
data class LyricCandidate(
    val id: String,
    val trackId: String,
    val sourceId: String,
    val songIdLong: Long = 0L,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val lyrics: String? = null,
    val isPureMusic: Boolean = false,
    val isLoadingLyrics: Boolean = false,
    val isExpanded: Boolean = false,
)
