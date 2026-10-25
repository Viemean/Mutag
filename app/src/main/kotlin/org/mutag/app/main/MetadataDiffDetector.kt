package org.mutag.app.main

import java.io.File
import okhttp3.OkHttpClient
import org.mutag.app.ui.cover.CoverDownloadHelper
import org.mutag.app.ui.cover.EmbeddedCoverFetcher
import org.mutag.core.tagger.scanner.AudioMetadata
import org.mutag.model.TrackMetadata

/**
 * 音频元数据与在线匹配结果等价性比对判决实体。
 *
 * @param isIdentical 全量是否完全一致（元数据与封面均无变动）
 * @param isMetadataIdentical 文本及编号等标签字段是否完全一致
 * @param isCoverIdentical 封面图片数据是否完全一致
 */
data class MetadataDiffResult(val isIdentical: Boolean, val isMetadataIdentical: Boolean, val isCoverIdentical: Boolean)

/**
 * 本地音频元数据与云端曲目数据等价性比对工具。
 */
object MetadataDiffDetector {

    /**
     * 比对本地音频元数据与云端候选元数据的等价性。
     *
     * @param local 本地音频元数据实体
     * @param cloud 云端检索或详情得出的曲目实体
     * @param httpClient 网络请求客户端（用于 HEAD 探测）
     * @param preferMaster 是否配置优先下载母带原图
     * @return 差异判定结果
     */
    fun evaluate(local: AudioMetadata, cloud: TrackMetadata, httpClient: OkHttpClient, preferMaster: Boolean): MetadataDiffResult {
        val metadataIdentical = isMetadataEquivalent(local, cloud)
        val coverIdentical = isCoverEquivalent(local, cloud, httpClient, preferMaster)
        return MetadataDiffResult(
            isIdentical = metadataIdentical && coverIdentical,
            isMetadataIdentical = metadataIdentical,
            isCoverIdentical = coverIdentical,
        )
    }

    /**
     * 判断关键文本与音轨编号等元数据是否等价。
     *
     * @param local 本地音频元数据
     * @param cloud 云端曲目元数据
     * @return 文本与编号等价返回 true，存在差异返回 false
     */
    fun isMetadataEquivalent(local: AudioMetadata, cloud: TrackMetadata): Boolean {
        if (local.title.trim() != cloud.title.trim()) return false

        if (cloud.artistName.isNotBlank() && local.artist.trim() != cloud.artistName.trim()) {
            return false
        }

        if (cloud.album.name.isNotBlank() && local.album.trim() != cloud.album.name.trim()) {
            return false
        }

        if (cloud.albumArtist.isNotBlank() && local.albumArtist.trim() != cloud.albumArtist.trim()) {
            return false
        }

        if (cloud.trackNumber > 0 && local.trackNumber != cloud.trackNumber) {
            return false
        }
        if (cloud.trackTotal > 0 && local.trackTotal != cloud.trackTotal) {
            return false
        }

        if (cloud.discNumber > 0 && local.discNumber != cloud.discNumber) {
            return false
        }
        if (cloud.discTotal > 0 && local.discTotal != cloud.discTotal) {
            return false
        }

        if (cloud.publishDate.isNotBlank()) {
            val localYear = local.publishDate.trim().take(4)
            val cloudYear = cloud.publishDate.trim().take(4)
            if (localYear.isNotBlank() && cloudYear.isNotBlank() && localYear != cloudYear) {
                return false
            }
        }

        if (cloud.genre.isNotBlank() && local.genre.trim() != cloud.genre.trim()) {
            return false
        }

        if (cloud.lyricist.isNotBlank() && local.lyricist.trim() != cloud.lyricist.trim()) {
            return false
        }
        if (cloud.composer.isNotBlank() && local.composer.trim() != cloud.composer.trim()) {
            return false
        }
        if (cloud.arranger.isNotBlank() && local.arranger.trim() != cloud.arranger.trim()) {
            return false
        }
        if (cloud.producer.isNotBlank() && local.producer.trim() != cloud.producer.trim()) {
            return false
        }
        if (cloud.company.isNotBlank() && local.company.trim() != cloud.company.trim()) {
            return false
        }

        if (cloud.chorusStartMs > 0 && local.chorusStartMs != cloud.chorusStartMs) {
            return false
        }

        val rg = cloud.replayGain
        if (rg != null) {
            val localGain = local.replayGainTrackGain
            val localPeak = local.replayGainTrackPeak
            if (localGain == null || kotlin.math.abs(localGain - rg.trackGainDb) > 0.01) {
                return false
            }
            if (localPeak == null || kotlin.math.abs(localPeak - rg.trackPeak) > 0.0001) {
                return false
            }
        }

        val cloudLyrics = cloud.lyrics?.rawLyricText
        if (!cloudLyrics.isNullOrBlank() && local.lyrics.isNullOrBlank()) {
            return false
        }

        return true
    }

    /**
     * 判断封面是否等价（两级检验：指纹快检与远端 HEAD 探测）。
     *
     * @param local 本地音频元数据
     * @param cloud 云端曲目元数据
     * @param httpClient 网络请求客户端
     * @param preferMaster 是否配置优先下载母带原图
     * @return 封面数据等价返回 true，否则返回 false
     */
    fun isCoverEquivalent(local: AudioMetadata, cloud: TrackMetadata, httpClient: OkHttpClient, preferMaster: Boolean): Boolean {
        val file = File(local.filePath)
        if (!file.exists()) return false

        val cloudHasCover = cloud.album.id.isNotBlank() || cloud.coverUrl.isNotBlank()
        if (!cloudHasCover && !local.hasCover) {
            return true
        }
        if (cloudHasCover != local.hasCover) {
            return false
        }

        val localFileSize = file.length()
        val localLastModified = file.lastModified()
        val isFileUnmodified = localFileSize == local.fileSize && localLastModified == local.lastModified

        val currentCoverSize = if (isFileUnmodified && local.coverSize > 0L) {
            local.coverSize
        } else {
            EmbeddedCoverFetcher.extractCoverBytes(file)?.size?.toLong() ?: 0L
        }

        if (currentCoverSize <= 0L) {
            return false
        }

        val targetUrl = CoverDownloadHelper.resolveTargetCoverUrl(cloud, preferMaster)
        if (targetUrl.isBlank()) {
            return false
        }

        val cloudContentLength = CoverDownloadHelper.probeCoverLength(httpClient, targetUrl)
        val isExactSameFile = cloudContentLength != null && cloudContentLength > 0L && cloudContentLength == currentCoverSize

        if (!isExactSameFile) {
            return false
        }

        // 封面长度一致且未超出安全规格上限时判定等价；超出安全阈值时判定不等价以触发就地压缩
        return currentCoverSize in 1..CoverDownloadHelper.MAX_COVER_IMAGE_SIZE
    }
}
