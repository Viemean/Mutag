package org.mutag.model

import kotlinx.serialization.Serializable

/**
 * 曲目元数据领域模型。
 *
 * @param trackId 曲目唯一标识符
 * @param title 歌曲名称
 * @param artists 歌手实体列表
 * @param album 所属专辑实体
 * @param albumArtist 专辑艺术家
 * @param durationMs 音频时长（毫秒）
 * @param trackNumber 音轨编号
 * @param trackTotal 总音轨数
 * @param discNumber 碟号
 * @param discTotal 总碟数
 * @param publishDate 发行日期 (YYYY-MM-DD)
 * @param genre 流派风格
 * @param lyricist 作词人
 * @param composer 作曲人
 * @param arranger 编曲人
 * @param producer 制作人
 * @param company 唱片公司
 * @param comment 备注信息
 * @param description 歌曲描述
 * @param language 语种
 * @param chorusStartMs 副歌起始时间戳（毫秒）
 * @param replayGain ReplayGain 增益元数据
 * @param coverUrl 封面图片下载链接
 * @param lyrics 同步歌词实体
 * @param sourceId 音源唯一标识符（如 qqmusic, netease）
 */
@Serializable
data class TrackMetadata(
    val trackId: String,
    val title: String,
    val artists: List<ArtistMetadata> = emptyList(),
    val album: AlbumMetadata = AlbumMetadata(id = "", name = ""),
    val albumArtist: String = "",
    val durationMs: Long = 0L,
    val trackNumber: Int = 0,
    val trackTotal: Int = 0,
    val discNumber: Int = 1,
    val discTotal: Int = 1,
    val publishDate: String = "",
    val genre: String = "",
    val lyricist: String = "",
    val composer: String = "",
    val arranger: String = "",
    val producer: String = "",
    val company: String = "",
    val comment: String = "",
    val description: String = "",
    val language: String = "",
    val chorusStartMs: Long = 0L,
    val replayGain: ReplayGainInfo? = null,
    val coverUrl: String = "",
    val lyrics: SyncedLyrics? = null,
    val sourceId: String = "",
) {
    val artistName: String
        get() = if (artists.isEmpty()) "" else artists.joinToString(" / ") { it.name }
}

/**
 * 歌手元数据。
 *
 * @param id 歌手唯一 ID
 * @param name 歌手名称
 * @param avatarUrl 歌手头像图片链接
 */
@Serializable
data class ArtistMetadata(val id: String, val name: String, val avatarUrl: String = "")

/**
 * 专辑元数据。
 *
 * @param id 专辑唯一 ID
 * @param name 专辑名称
 * @param coverUrl 专辑封面图片链接
 * @param publishDate 专辑发行日期
 * @param trackCount 包含歌曲数量
 * @param description 专辑介绍描述
 * @param company 发行唱片公司
 */
@Serializable
data class AlbumMetadata(
    val id: String,
    val name: String,
    val coverUrl: String = "",
    val publishDate: String = "",
    val trackCount: Int = 0,
    val description: String = "",
    val company: String = "",
)

/**
 * ReplayGain 音频回放增益数据。
 *
 * @param trackGainDb 目标音轨增益数值（单位：dB）
 * @param trackPeak 采样峰值幅度
 * @param loudnessRange 响度范围 (LRA)
 */
@Serializable
data class ReplayGainInfo(val trackGainDb: Double, val trackPeak: Double, val loudnessRange: Double = 0.0)

/**
 * 同步时间轴歌词。
 *
 * @param rawLyricText 原始 LRC 文本内容
 * @param lines 解析后的各单行歌词列表
 * @param hasTranslation 是否包含翻译歌词
 * @param mergedLrcText 双语对齐合并后的 LRC 文本
 */
@Serializable
data class SyncedLyrics(
    val rawLyricText: String,
    val lines: List<LyricLine> = emptyList(),
    val hasTranslation: Boolean = false,
    val mergedLrcText: String = "",
)

/**
 * 单行同步时间轴歌词。
 *
 * @param timestampMs 行对应时间戳（毫秒）
 * @param text 原文歌词文本
 * @param transText 译文歌词文本
 */
@Serializable
data class LyricLine(val timestampMs: Long, val text: String, val transText: String = "")

/**
 * 声学指纹特征算法类型定义。
 */
object AcousticFeatureType {
    /** QQ 音乐私有特征 */
    const val QQ_MUSIC = 0

    /** 网易云音乐特征 */
    const val NETEASE = 1
}

/**
 * 声学指纹特征数据模型。
 *
 * @param data 提取的特征字节序列
 * @param duration 特征切片时长（秒）
 * @param featureType 特征算法类型编号
 * @param confidence 特征置信度
 */
@Serializable
data class AcousticFeature(val data: ByteArray, val duration: Float, val featureType: Int = 0, val confidence: Float = 0.0f) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AcousticFeature) return false
        return data.contentEquals(other.data) &&
            duration == other.duration &&
            featureType == other.featureType &&
            confidence == other.confidence
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + featureType
        result = 31 * result + confidence.hashCode()
        return result
    }
}
