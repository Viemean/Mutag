package org.mutag.core.tagger.scanner

/**
 * 音频标签元数据健康完整度状态。
 */
enum class TagHealth {
    /** 标题、艺术家、专辑及封面均已完备 */
    COMPLETE,

    /** 关键字段或封面缺失，待在线匹配填充 */
    NEEDS_MATCH,
}

/**
 * 本地音频文件综合元数据实体。
 *
 * @param filePath 音频文件绝对路径
 * @param fileName 文件名
 * @param fileSize 文件大小（字节）
 * @param lastModified 最后修改时间戳
 * @param format 音频编码格式（如 FLAC, MP3）
 * @param title 歌曲名称
 * @param artist 艺术家
 * @param album 所属专辑
 * @param albumArtist 专辑艺术家
 * @param trackNumber 音轨编号
 * @param trackTotal 总音轨数
 * @param discNumber 碟号
 * @param discTotal 总碟数
 * @param publishDate 发行年份/日期
 * @param genre 音乐流派
 * @param lyricist 作词人
 * @param composer 作曲人
 * @param arranger 编曲人
 * @param producer 制作人
 * @param company 唱片公司
 * @param comment 备注
 * @param replayGainTrackGain 音轨回放增益
 * @param replayGainTrackPeak 音轨回放峰值
 * @param cloudTrackId 云端音源对应 Track ID / Song MID
 * @param description 描述说明
 * @param durationMs 音频播放时长（毫秒）
 * @param specChip 规格标签显示文本（如 FLAC 24bit/96k）
 * @param hasCover 是否内嵌专辑封面
 * @param lyrics 内嵌歌词文本
 * @param language 语种
 * @param chorusStartMs 副歌高潮点时间戳（毫秒）
 * @param techSpec 底层音频硬件规格对象
 * @param tagHealth 标签健康度评估结果
 */
data class AudioMetadata(
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long,
    val format: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
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
    val replayGainTrackGain: Double? = null,
    val replayGainTrackPeak: Double? = null,
    val cloudTrackId: String = "",
    val cloudAlbumMid: String = "",
    val description: String = "",
    val durationMs: Long = 0L,
    val specChip: String = "",
    val hasCover: Boolean = false,
    val coverSize: Long = 0L,
    val lyrics: String? = null,
    val language: String = "",
    val chorusStartMs: Long = 0L,
    val techSpec: AudioTechSpec? = null,
    val tagHealth: TagHealth = TagHealth.NEEDS_MATCH,
) {
    companion object {
        /**
         * 评估音频标签完整性。
         *
         * @param title 歌曲标题
         * @param artist 艺术家
         * @param album 专辑名
         * @param hasCover 是否具备封面
         * @return 评估得出的健康度状态
         */
        fun evaluateHealth(title: String, artist: String, album: String, hasCover: Boolean): TagHealth =
            if (title.isNotBlank() && artist.isNotBlank() && album.isNotBlank() && hasCover) {
                TagHealth.COMPLETE
            } else {
                TagHealth.NEEDS_MATCH
            }
    }
}

/**
 * 本地文件目录项实体。
 *
 * @param path 目录绝对路径
 * @param name 目录显示名称
 * @param trackCount 包含有效音频曲目总数
 * @param subfolderCount 包含直接子目录数量
 * @param coverTrack 用于呈现封面指示的代表曲目元数据
 */
data class FolderItem(val path: String, val name: String, val subFolderCount: Int, val audioFileCount: Int, val pendingMatchCount: Int) {
    val isAllTagged: Boolean
        get() = audioFileCount > 0 && pendingMatchCount == 0
}

/**
 * 目录扫描聚合结果容器。
 *
 * @param currentPath 当前扫描目录绝对路径
 * @param folders 子文件夹条目列表
 * @param tracks 音频文件元数据列表
 */
data class DirectoryScanResult(
    val currentPath: String,
    val folders: List<FolderItem> = emptyList(),
    val tracks: List<AudioMetadata> = emptyList(),
)
