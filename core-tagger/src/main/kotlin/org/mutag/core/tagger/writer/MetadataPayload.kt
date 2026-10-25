package org.mutag.core.tagger.writer

/**
 * 准备写入音频文件的完整标签载荷模型。
 *
 * @param title 歌曲标题
 * @param artist 艺术家
 * @param album 专辑名称
 * @param albumArtist 专辑艺术家
 * @param trackNumber 音轨编号
 * @param trackTotal 总音轨数
 * @param discNumber 碟号
 * @param discTotal 总碟数
 * @param publishDate 发行日期
 * @param genre 流派风格
 * @param lyricist 作词人
 * @param composer 作曲人
 * @param arranger 编曲人
 * @param producer 制作人
 * @param company 唱片公司
 * @param comment 备注
 * @param description 描述
 * @param cloudTrackId 云端 Track ID / MID
 * @param replayGainTrackGain 音轨增益
 * @param replayGainTrackPeak 音轨峰值
 * @param language 语种
 * @param chorusStartMs 副歌时间戳（毫秒）
 * @param lyrics 完整歌词文本
 * @param coverBytes 专辑封面图片二进制数据
 * @param coverMime 封面图片 MIME 类型
 */
data class MetadataPayload(
    val title: String,
    val artist: String,
    val album: String,
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
    val language: String = "",
    val chorusStartMs: Long = 0L,
    val cloudTrackId: String = "",
    val cloudAlbumMid: String = "",
    val description: String = "",
    val lyrics: String? = null,
    val coverBytes: ByteArray? = null,
    val coverMime: String = "image/jpeg",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MetadataPayload
        return title == other.title &&
            artist == other.artist &&
            album == other.album &&
            albumArtist == other.albumArtist &&
            trackNumber == other.trackNumber &&
            trackTotal == other.trackTotal &&
            discNumber == other.discNumber &&
            discTotal == other.discTotal &&
            publishDate == other.publishDate &&
            genre == other.genre &&
            lyricist == other.lyricist &&
            composer == other.composer &&
            arranger == other.arranger &&
            producer == other.producer &&
            company == other.company &&
            comment == other.comment &&
            replayGainTrackGain == other.replayGainTrackGain &&
            replayGainTrackPeak == other.replayGainTrackPeak &&
            cloudTrackId == other.cloudTrackId &&
            cloudAlbumMid == other.cloudAlbumMid &&
            description == other.description &&
            lyrics == other.lyrics &&
            (
                coverBytes == null && other.coverBytes == null ||
                    (coverBytes != null && other.coverBytes != null && coverBytes.contentEquals(other.coverBytes))
                ) &&
            coverMime == other.coverMime
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + (coverBytes?.contentHashCode() ?: 0)
        return result
    }
}
