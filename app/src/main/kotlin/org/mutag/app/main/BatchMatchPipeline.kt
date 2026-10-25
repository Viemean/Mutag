package org.mutag.app.main

import android.content.Context
import java.io.File
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import org.mutag.app.media.MediaScanHelper
import org.mutag.app.notification.MatchingNotificationHelper
import org.mutag.app.settings.AppSettingsManager
import org.mutag.app.ui.cover.CoverDownloadHelper
import org.mutag.app.ui.cover.EmbeddedCoverFetcher
import org.mutag.core.tagger.cache.MetadataCache
import org.mutag.core.tagger.recognizer.AudioSliceExtractor
import org.mutag.core.tagger.scanner.AudioHeaderParser
import org.mutag.core.tagger.scanner.AudioMetadata
import org.mutag.core.tagger.writer.AudioMetadataWriter
import org.mutag.core.tagger.writer.MetadataPayload
import org.mutag.model.TrackMetadata
import org.mutag.provider.MatchConfidence
import org.mutag.provider.SearchQueryHelper
import org.mutag.provider.api.MusicMetadataProvider

/**
 * 批量元数据在线匹配与标签自动写入调度流水线。
 */
object BatchMatchPipeline {

    /**
     * 执行批量曲目在线匹配、置信度裁决、标签写入与存疑归集流程。
     *
     * @param pendingTracks 待匹配的音频元数据列表
     * @param metadataProvider 首选在线元数据服务提供商
     * @param fallbackProvider 备用在线元数据服务提供商（若启用双源重试机制）
     * @param httpClient 网络请求客户端
     * @param cache 本地元数据缓存接口
     * @param context Android 上下文实例
     * @param onProgress 进度回调函数（当前进度浮点数、成功数、总数）
     * @return 需人工核验的低置信度存疑或异常条目列表
     */
    suspend fun execute(
        pendingTracks: List<AudioMetadata>,
        metadataProvider: MusicMetadataProvider,
        fallbackProvider: MusicMetadataProvider? = null,
        httpClient: OkHttpClient,
        cache: MetadataCache,
        context: Context,
        onProgress: suspend (progress: Float, successCount: Int, total: Int) -> Unit,
    ): List<UncertainMatchItem> {
        val total = pendingTracks.size
        var successCount = 0
        MatchingNotificationHelper.showProgress(context, 0, total, "准备匹配...")

        val concurrencyLimit = Semaphore(5)
        val uncertainList = mutableListOf<UncertainMatchItem>()

        try {
            for ((index, track) in pendingTracks.withIndex()) {
                val songTitle = track.title.ifBlank { track.fileName.substringBeforeLast(".") }
                MatchingNotificationHelper.showProgress(context, index, total, songTitle)

                var matchedTrack: TrackMetadata? = null
                var matchedProvider: MusicMetadataProvider = metadataProvider
                var isAcoustic = false

                try {
                    concurrencyLimit.withPermit {
                        val plan = SearchQueryHelper.buildPlan(
                            title = songTitle,
                            artist = track.artist,
                            album = track.album,
                            albumArtist = track.albumArtist,
                        )

                        val primaryRes = metadataProvider.search(plan.primaryQuery, page = 1, pageSize = 5).getOrNull() ?: emptyList()
                        val candidates = if (primaryRes.isNotEmpty()) {
                            primaryRes
                        } else {
                            val secondaryQuery = plan.secondaryQueries.firstOrNull()
                            if (!secondaryQuery.isNullOrBlank()) {
                                metadataProvider.search(secondaryQuery, page = 1, pageSize = 5).getOrNull() ?: emptyList()
                            } else {
                                emptyList()
                            }
                        }

                        val ranked = SearchQueryHelper.rankAndDeduplicate(
                            candidates = candidates,
                            targetDurationMs = track.durationMs,
                            targetTrackNumber = track.trackNumber,
                            targetAlbum = track.album,
                        )

                        matchedTrack = ranked.firstOrNull()

                        if (matchedTrack == null && fallbackProvider != null) {
                            val fallbackPrimary =
                                fallbackProvider.search(plan.primaryQuery, page = 1, pageSize = 5).getOrNull() ?: emptyList()
                            val fallbackCandidates = if (fallbackPrimary.isNotEmpty()) {
                                fallbackPrimary
                            } else {
                                val secondaryQuery = plan.secondaryQueries.firstOrNull()
                                if (!secondaryQuery.isNullOrBlank()) {
                                    fallbackProvider.search(secondaryQuery, page = 1, pageSize = 5).getOrNull() ?: emptyList()
                                } else {
                                    emptyList()
                                }
                            }
                            val fallbackRanked = SearchQueryHelper.rankAndDeduplicate(
                                candidates = fallbackCandidates,
                                targetDurationMs = track.durationMs,
                                targetTrackNumber = track.trackNumber,
                                targetAlbum = track.album,
                            )
                            val fallbackCandidate = fallbackRanked.firstOrNull()
                            if (fallbackCandidate != null) {
                                matchedTrack = fallbackCandidate
                                matchedProvider = fallbackProvider
                            }
                        }

                        if (matchedTrack == null) {
                            val audioFile = File(track.filePath)
                            val sliceResult = AudioSliceExtractor.extractSliceForSource(
                                audioFile = audioFile,
                                sourceId = metadataProvider.sourceId,
                                preferredStartMs = track.chorusStartMs,
                            )
                            if (sliceResult != null) {
                                val acousticRes = metadataProvider.recognizeByAcoustic(sliceResult.feature).getOrNull()
                                if (acousticRes != null) {
                                    matchedTrack = acousticRes
                                    matchedProvider = metadataProvider
                                    isAcoustic = true
                                } else if (fallbackProvider != null) {
                                    val fallbackSlice = AudioSliceExtractor.extractSliceForSource(
                                        audioFile = audioFile,
                                        sourceId = fallbackProvider.sourceId,
                                        preferredStartMs = track.chorusStartMs,
                                    )
                                    val fallbackAcoustic = fallbackSlice?.let {
                                        fallbackProvider.recognizeByAcoustic(it.feature).getOrNull()
                                    }
                                    if (fallbackAcoustic != null) {
                                        matchedTrack = fallbackAcoustic
                                        matchedProvider = fallbackProvider
                                        isAcoustic = true
                                    }
                                }
                            }
                        }
                    }

                    var evaluation = SearchQueryHelper.evaluateMatch(
                        targetDurationMs = track.durationMs,
                        targetTitle = track.title,
                        targetArtist = track.artist,
                        targetAlbum = track.album,
                        candidate = matchedTrack,
                        isAcoustic = isAcoustic,
                        targetAlbumArtist = track.albumArtist,
                    )

                    if (evaluation.confidence != MatchConfidence.HIGH && !isAcoustic) {
                        val audioFile = File(track.filePath)
                        val sliceResult = AudioSliceExtractor.extractSliceForSource(
                            audioFile = audioFile,
                            sourceId = metadataProvider.sourceId,
                            preferredStartMs = track.chorusStartMs,
                        )
                        if (sliceResult != null) {
                            val acousticRes = metadataProvider.recognizeByAcoustic(sliceResult.feature).getOrNull()
                            val (effectiveAcoustic, effectiveProvider) = if (acousticRes != null) {
                                Pair(acousticRes, metadataProvider)
                            } else if (fallbackProvider != null) {
                                val fallbackSlice = AudioSliceExtractor.extractSliceForSource(
                                    audioFile = audioFile,
                                    sourceId = fallbackProvider.sourceId,
                                    preferredStartMs = track.chorusStartMs,
                                )
                                val fallbackRes = fallbackSlice?.let { fallbackProvider.recognizeByAcoustic(it.feature).getOrNull() }
                                Pair(fallbackRes, fallbackProvider)
                            } else {
                                Pair(null, metadataProvider)
                            }

                            if (effectiveAcoustic != null) {
                                val acousticEval = SearchQueryHelper.evaluateMatch(
                                    targetDurationMs = track.durationMs,
                                    targetTitle = track.title,
                                    targetArtist = track.artist,
                                    targetAlbum = track.album,
                                    candidate = effectiveAcoustic,
                                    isAcoustic = true,
                                    targetAlbumArtist = track.albumArtist,
                                )
                                if (acousticEval.confidence == MatchConfidence.HIGH) {
                                    matchedTrack = effectiveAcoustic
                                    matchedProvider = effectiveProvider
                                    evaluation = acousticEval
                                    isAcoustic = true
                                }
                            }
                        }
                    }

                    if (evaluation.confidence == MatchConfidence.HIGH && matchedTrack != null) {
                        val file = File(track.filePath)
                        if (file.exists()) {
                            val detail = matchedProvider.getTrackDetail(matchedTrack.trackId).getOrDefault(matchedTrack)
                            val preferMaster = AppSettingsManager.settingsState.value.fetchOriginalMasterCover
                            val diff = MetadataDiffDetector.evaluate(track, detail, httpClient, preferMaster)

                            if (diff.isIdentical) {
                                if (track.cloudTrackId.isBlank() || track.cloudAlbumMid.isBlank() || track.coverSize <= 0L) {
                                    val updatedTrack = track.copy(
                                        cloudTrackId = detail.trackId,
                                        cloudAlbumMid = detail.album.id,
                                        coverSize = if (track.coverSize >
                                            0L
                                        ) {
                                            track.coverSize
                                        } else {
                                            (EmbeddedCoverFetcher.extractCoverBytes(file)?.size?.toLong() ?: 0L)
                                        },
                                    )
                                    cache.put(updatedTrack)
                                }
                                successCount++
                            } else {
                                val coverBytes = if (diff.isCoverIdentical) {
                                    EmbeddedCoverFetcher.extractCoverBytes(file)
                                } else {
                                    CoverDownloadHelper.obtainOrCompressCover(
                                        httpClient = httpClient,
                                        targetTrack = detail,
                                        localCoverBytes = EmbeddedCoverFetcher.extractCoverBytes(file),
                                        localCloudTrackId = track.cloudTrackId,
                                        localCloudAlbumMid = track.cloudAlbumMid,
                                    )
                                }

                                val payload = MetadataPayload(
                                    title = detail.title,
                                    artist = detail.artistName,
                                    album = detail.album.name,
                                    albumArtist = detail.albumArtist.ifBlank { track.albumArtist },
                                    trackNumber = detail.trackNumber,
                                    trackTotal = detail.trackTotal,
                                    discNumber = detail.discNumber,
                                    discTotal = detail.discTotal,
                                    publishDate = detail.publishDate,
                                    genre = detail.genre.ifBlank { track.genre },
                                    lyricist = detail.lyricist.ifBlank { track.lyricist },
                                    composer = detail.composer.ifBlank { track.composer },
                                    arranger = detail.arranger.ifBlank { track.arranger },
                                    producer = detail.producer.ifBlank { track.producer },
                                    company = detail.company.ifBlank { track.company },
                                    comment = detail.comment.ifBlank { track.comment },
                                    description = detail.description.ifBlank { track.description },
                                    cloudTrackId = detail.trackId,
                                    cloudAlbumMid = detail.album.id,
                                    replayGainTrackGain = detail.replayGain?.trackGainDb ?: track.replayGainTrackGain,
                                    replayGainTrackPeak = detail.replayGain?.trackPeak ?: track.replayGainTrackPeak,
                                    language = detail.language.ifBlank { track.language },
                                    chorusStartMs = if (detail.chorusStartMs > 0) detail.chorusStartMs else track.chorusStartMs,
                                    lyrics = detail.lyrics?.mergedLrcText ?: detail.lyrics?.rawLyricText ?: track.lyrics,
                                    coverBytes = coverBytes,
                                )

                                if (AudioMetadataWriter.writeMetadata(file, payload)) {
                                    val updated = AudioHeaderParser.parse(file)
                                    cache.put(updated)
                                    MediaScanHelper.scanFile(context, file.absolutePath)
                                    successCount++
                                }
                            }
                        }
                    } else {
                        uncertainList.add(
                            UncertainMatchItem(
                                localFile = track,
                                candidate = matchedTrack,
                                reason = evaluation.reason,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    uncertainList.add(
                        UncertainMatchItem(
                            localFile = track,
                            candidate = null,
                            reason = "处理异常: ${e.message ?: "未知"}",
                        ),
                    )
                }

                onProgress((index + 1).toFloat() / total, successCount, total)
            }
        } finally {
            MatchingNotificationHelper.cancelProgress(context)
        }

        if (uncertainList.isNotEmpty()) {
            val uncertainCount = uncertainList.count { it.candidate != null }
            val failedCount = uncertainList.count { it.candidate == null }
            MatchingNotificationHelper.showAlert(context, uncertainCount, failedCount)
        }

        return uncertainList
    }
}
