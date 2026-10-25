package org.mutag.provider.api

import org.mutag.model.AcousticFeature
import org.mutag.model.AlbumMetadata
import org.mutag.model.SyncedLyrics
import org.mutag.model.TrackMetadata

/**
 * 在线音乐元数据提供方统一接口。
 */
interface MusicMetadataProvider {
    /** 音源唯一标识符 */
    val sourceId: String

    /** 音源显示名称 */
    val displayName: String

    /**
     * 关键字检索匹配曲目列表。
     *
     * @param query 搜索关键词
     * @param page 分页页码，从 1 开始
     * @param pageSize 每页条数限制
     * @return 匹配的曲目元数据列表结果
     */
    suspend fun search(query: String, page: Int = 1, pageSize: Int = 30): Result<List<TrackMetadata>>

    /**
     * 获取指定单曲的完整元数据详情。
     *
     * @param trackId 曲目唯一 ID
     * @return 包含全字段的曲目元数据结果
     */
    suspend fun getTrackDetail(trackId: String): Result<TrackMetadata>

    /**
     * 获取指定专辑的元数据详情。
     *
     * @param albumId 专辑唯一 ID
     * @return 专辑元数据结果
     */
    suspend fun getAlbumDetail(albumId: String): Result<AlbumMetadata>

    /**
     * 依据曲目 ID 获取同步歌词及翻译。
     *
     * @param trackId 曲目唯一 ID
     * @param songIdLong 备用整型数字 ID
     * @param title 可选歌曲名称，用于无歌词时执行同名备选降级
     * @param artist 可选歌手名称
     * @param durationSec 可选音频时长（秒），用于校验同名备选候选的时长吻合度
     * @return 同步歌词实体结果
     */
    suspend fun getLyrics(
        trackId: String,
        songIdLong: Long = 0L,
        title: String = "",
        artist: String = "",
        durationSec: Int = 0,
    ): Result<SyncedLyrics>

    /**
     * 依据音频切片声学指纹特征在线识别曲目。
     *
     * @param feature 声学指纹特征数据
     * @return 识别匹配成功的曲目元数据结果，无匹配时为 null
     */
    suspend fun recognizeByAcoustic(feature: AcousticFeature): Result<TrackMetadata?>

    /**
     * 根据目标分辨率规格解析曲目的封面下载直链。
     *
     * @param track 曲目元数据
     * @param resolution 目标分辨率规格
     * @return 封面直链地址
     */
    fun resolveCoverUrl(track: TrackMetadata, resolution: CoverResolution): String = track.coverUrl
}

/**
 * 封面图片请求分辨率规格。
 */
enum class CoverResolution {
    /** 缩略图（约 200~500px，用于列表或浮层卡片快速预览） */
    THUMBNAIL,

    /** 中等分辨率（约 500~800px） */
    MEDIUM,

    /** 高清分辨率（约 1200px 左右标准封面） */
    LARGE,

    /** 原始母带最高精度封面 */
    ORIGINAL_MASTER,
}
