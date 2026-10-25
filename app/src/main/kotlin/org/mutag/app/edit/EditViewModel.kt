package org.mutag.app.edit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.mutag.app.MuTagApplication
import org.mutag.app.media.MediaScanHelper
import org.mutag.app.settings.AppSettingsManager
import org.mutag.app.ui.cover.CoverDownloadHelper
import org.mutag.app.ui.cover.CoverExportHelper
import org.mutag.app.ui.cover.EmbeddedCoverFetcher
import org.mutag.core.tagger.scanner.AudioHeaderParser
import org.mutag.core.tagger.writer.AudioMetadataWriter
import org.mutag.core.tagger.writer.MetadataPayload
import org.mutag.model.TrackMetadata
import org.mutag.provider.SearchQueryHelper
import org.mutag.provider.api.MusicMetadataProvider

/**
 * 音频标签单曲编辑页 ViewModel，处理标签读取、在线匹配比对、封面管理与文件保存。
 */
class EditViewModel : ViewModel() {

    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state.asStateFlow()

    private fun getProviderForCurrentSource(): MusicMetadataProvider =
        if (_state.value.matchSource == org.mutag.app.settings.MetadataSource.NETEASE) {
            MuTagApplication.instance.neteaseProvider
        } else {
            MuTagApplication.instance.qqMusicProvider
        }

    private val cache = MuTagApplication.instance.cache
    private val httpClient = OkHttpClient()
    private var coverDownloadJob: Job? = null
    private var searchJob: Job? = null
    private var preSliceJob: Job? = null
    private var lyricSearchJob: Job? = null
    private var cachedSliceResult: org.mutag.core.tagger.recognizer.AudioSliceResult? = null

    /**
     * 读取指定音频文件，解析元数据与内嵌封面至界面状态。
     *
     * @param filePath 目标音频文件绝对路径
     */
    fun loadFile(filePath: String) {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return

        viewModelScope.launch(Dispatchers.IO) {
            val metadata = AudioHeaderParser.parse(file)
            val coverBytes = EmbeddedCoverFetcher.extractCoverBytes(file)
            val coverDim = if (coverBytes != null) "${coverBytes.size / 1024} KB" else "无内嵌封面"

            _state.update {
                it.copy(
                    filePath = file.absolutePath,
                    fileName = file.name,
                    format = metadata.format,
                    specChip = metadata.specChip,
                    coverBytes = coverBytes,
                    coverDimensionsText = coverDim,
                    techSpec = metadata.techSpec,
                    title = metadata.title,
                    artist = metadata.artist,
                    album = metadata.album,
                    albumArtist = metadata.albumArtist,
                    trackNumber = if (metadata.trackNumber > 0) metadata.trackNumber.toString() else "",
                    trackTotal = if (metadata.trackTotal > 0) metadata.trackTotal.toString() else "",
                    discNumber = metadata.discNumber.toString(),
                    discTotal = metadata.discTotal.toString(),
                    publishDate = metadata.publishDate,
                    genre = metadata.genre,
                    language = metadata.language,
                    lyricist = metadata.lyricist,
                    composer = metadata.composer,
                    arranger = metadata.arranger,
                    producer = metadata.producer,
                    company = metadata.company,
                    comment = metadata.comment,
                    replayGainTrackGain = metadata.replayGainTrackGain?.let { g -> "%.2f".format(g) } ?: "",
                    replayGainTrackPeak = metadata.replayGainTrackPeak?.let { p -> "%.6f".format(p) } ?: "",
                    chorusStartMs = if (metadata.chorusStartMs > 0) metadata.chorusStartMs.toString() else "",
                    cloudTrackId = metadata.cloudTrackId,
                    cloudAlbumMid = metadata.cloudAlbumMid,
                    description = metadata.description,
                    lyrics = metadata.lyrics ?: "",
                    durationMs = metadata.durationMs,
                    matchSource = AppSettingsManager.settingsState.value.preferredSource,
                )
            }
        }
    }

    /**
     * 更新曲目标题。
     *
     * @param v 歌曲标题文本
     */
    fun updateTitle(v: String) = _state.update { it.copy(title = v) }

    /**
     * 更新艺术家名称。
     *
     * @param v 艺术家文本
     */
    fun updateArtist(v: String) = _state.update { it.copy(artist = v) }

    /**
     * 更新唱片专辑名称。
     *
     * @param v 专辑名称文本
     */
    fun updateAlbum(v: String) = _state.update { it.copy(album = v) }

    /**
     * 更新专辑主艺术家。
     *
     * @param v 专辑艺术家文本
     */
    fun updateAlbumArtist(v: String) = _state.update { it.copy(albumArtist = v) }

    /**
     * 更新当前音轨编号。
     *
     * @param v 音轨号文本
     */
    fun updateTrackNumber(v: String) = _state.update { it.copy(trackNumber = v) }

    /**
     * 更新总音轨数。
     *
     * @param v 总音轨数文本
     */
    fun updateTrackTotal(v: String) = _state.update { it.copy(trackTotal = v) }

    /**
     * 更新当前光盘编号。
     *
     * @param v 光盘号文本
     */
    fun updateDiscNumber(v: String) = _state.update { it.copy(discNumber = v) }

    /**
     * 更新总光盘数。
     *
     * @param v 总光盘数文本
     */
    fun updateDiscTotal(v: String) = _state.update { it.copy(discTotal = v) }

    /**
     * 更新发行日期。
     *
     * @param v 发行日期文本
     */
    fun updatePublishDate(v: String) = _state.update { it.copy(publishDate = v) }

    /**
     * 更新音乐流派风格。
     *
     * @param v 流派文本
     */
    fun updateGenre(v: String) = _state.update { it.copy(genre = v) }

    /**
     * 更新语种分类。
     *
     * @param v 语种文本
     */
    fun updateLanguage(v: String) = _state.update { it.copy(language = v) }

    /**
     * 更新副歌起始时间戳（毫秒）。
     *
     * @param v 副歌时间戳文本
     */
    fun updateChorusStartMs(v: String) = _state.update { it.copy(chorusStartMs = v) }

    /**
     * 更新填词作者。
     *
     * @param v 作词人文本
     */
    fun updateLyricist(v: String) = _state.update { it.copy(lyricist = v) }

    /**
     * 更新曲谱作者。
     *
     * @param v 作曲人文本
     */
    fun updateComposer(v: String) = _state.update { it.copy(composer = v) }

    /**
     * 更新编曲作者。
     *
     * @param v 编曲人文本
     */
    fun updateArranger(v: String) = _state.update { it.copy(arranger = v) }

    /**
     * 更新唱片出品公司。
     *
     * @param v 唱片公司文本
     */
    fun updateCompany(v: String) = _state.update { it.copy(company = v) }

    /**
     * 更新曲具备注说明。
     *
     * @param v 备注文本
     */
    fun updateComment(v: String) = _state.update { it.copy(comment = v) }

    /**
     * 更新音乐制作人。
     *
     * @param v 制作人文本
     */
    fun updateProducer(v: String) = _state.update { it.copy(producer = v) }

    /**
     * 更新 ReplayGain 音轨增益分贝数值。
     *
     * @param v 音轨增益文本
     */
    fun updateReplayGainTrackGain(v: String) = _state.update { it.copy(replayGainTrackGain = v) }

    /**
     * 更新 ReplayGain 音轨峰值数值。
     *
     * @param v 峰值振幅文本
     */
    fun updateReplayGainTrackPeak(v: String) = _state.update { it.copy(replayGainTrackPeak = v) }

    /**
     * 更新云端曲目唯一标识。
     *
     * @param v 云端 ID 文本
     */
    fun updateCloudTrackId(v: String) = _state.update { it.copy(cloudTrackId = v) }

    /**
     * 更新曲目或专辑介绍文本。
     *
     * @param v 介绍描述文本
     */
    fun updateDescription(v: String) = _state.update { it.copy(description = v) }

    /**
     * 更新歌词文本内容。
     *
     * @param v LRC 或纯文本歌词
     */
    fun updateLyrics(v: String) = _state.update { it.copy(lyrics = v) }

    /**
     * 切换高级元数据编辑卡片展开或折叠状态。
     */
    fun toggleAdvancedExpanded() = _state.update { it.copy(isAdvancedExpanded = !it.isAdvancedExpanded) }

    /**
     * 更新在线搜索框输入的检索词。
     *
     * @param query 搜索关键词
     */
    fun updateOnlineSearchQuery(query: String) = _state.update { it.copy(onlineSearchQuery = query) }

    /**
     * 切换当前匹配对话框选中的数据源，并自动重新触发在线检索。
     *
     * @param source 目标数据源
     */
    fun setMatchSource(source: org.mutag.app.settings.MetadataSource) {
        if (_state.value.matchSource == source) return
        _state.update { it.copy(matchSource = source) }
        searchOnline(_state.value.onlineSearchQuery.ifBlank { null })
    }

    /**
     * 发起在线曲目元数据检索并生成候选列表与快捷检索词胶囊。
     *
     * @param customQuery 可选的自定义检索词，为 null 时根据当前元数据自动推导
     */
    fun searchOnline(customQuery: String? = null) {
        val s = _state.value
        val plan = SearchQueryHelper.buildPlan(
            title = s.title,
            artist = s.artist,
            album = s.album,
            albumArtist = s.albumArtist,
        )

        val query = (customQuery ?: plan.primaryQuery).trim()
        if (query.isBlank()) return

        _state.update {
            it.copy(
                isSearchingOnline = true,
                showMatchDialog = true,
                onlineSearchQuery = query,
                quickSearchChips = plan.quickChips,
                searchCandidates = emptyList(),
            )
        }

        preSliceJob?.cancel()
        val curFilePath = s.filePath
        preSliceJob = viewModelScope.launch(Dispatchers.Default) {
            val f = File(curFilePath)
            if (f.exists() && f.isFile) {
                val targetType = if (_state.value.matchSource == org.mutag.app.settings.MetadataSource.NETEASE) {
                    org.mutag.model.AcousticFeatureType.NETEASE
                } else {
                    org.mutag.model.AcousticFeatureType.QQ_MUSIC
                }
                cachedSliceResult = org.mutag.core.tagger.recognizer.AudioSliceExtractor.extractSliceWithTime(
                    audioFile = f,
                    preferredStartMs = s.chorusStartMs.toLongOrNull() ?: 0L,
                    featureType = targetType,
                )
            }
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val provider = getProviderForCurrentSource()
            val list = if (customQuery == null) {
                val primaryResults = provider.search(query, page = 1, pageSize = 15).getOrDefault(emptyList())
                val candidates = if (primaryResults.isNotEmpty()) {
                    primaryResults
                } else {
                    val secondaryQuery = plan.secondaryQueries.firstOrNull()
                    if (!secondaryQuery.isNullOrBlank()) {
                        provider.search(secondaryQuery, page = 1, pageSize = 15).getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                }

                SearchQueryHelper.rankAndDeduplicate(
                    candidates = candidates,
                    targetDurationMs = s.durationMs,
                    targetTrackNumber = s.trackNumber.toIntOrNull() ?: 0,
                    targetAlbum = s.album,
                )
            } else {
                val result = provider.search(query, page = 1, pageSize = 20)
                SearchQueryHelper.rankAndDeduplicate(
                    candidates = result.getOrDefault(emptyList()),
                    targetDurationMs = s.durationMs,
                    targetTrackNumber = s.trackNumber.toIntOrNull() ?: 0,
                    targetAlbum = s.album,
                )
            }

            _state.update {
                it.copy(
                    isSearchingOnline = false,
                    searchCandidates = list,
                    selectedCandidate = list.firstOrNull(),
                    candidateDiffFields = CANDIDATE_METADATA_FIELDS.associate { (k, _) -> k to true },
                )
            }
        }
    }

    /**
     * 选中在线候选曲目并拉取详情，初始化差异字段比对勾选状态。
     *
     * @param candidate 选中的候选曲目元数据
     */
    fun selectCandidate(candidate: TrackMetadata) {
        _state.update { it.copy(selectedCandidate = candidate) }
    }

    /**
     * 切换指定字段的候选差异应用勾选状态。
     *
     * @param fieldKey 字段唯一标识键
     */
    fun toggleDiffField(fieldKey: String) {
        _state.update {
            val current = it.candidateDiffFields.toMutableMap()
            current[fieldKey] = !(current[fieldKey] ?: true)
            it.copy(candidateDiffFields = current)
        }
    }

    /**
     * 全选候选差异比对字段。
     */
    fun selectAllDiffFields() {
        _state.update {
            it.copy(candidateDiffFields = CANDIDATE_METADATA_FIELDS.associate { (k, _) -> k to true })
        }
    }

    /**
     * 反选候选差异比对字段。
     */
    fun invertDiffFields() {
        _state.update {
            val current = it.candidateDiffFields
            it.copy(candidateDiffFields = CANDIDATE_METADATA_FIELDS.associate { (k, _) -> k to !(current[k] ?: true) })
        }
    }

    /**
     * 清空全部候选差异比对字段勾选。
     */
    fun clearDiffFields() {
        _state.update {
            it.copy(candidateDiffFields = CANDIDATE_METADATA_FIELDS.associate { (k, _) -> k to false })
        }
    }

    /**
     * 重置差异比对字段为默认勾选状态。
     */
    fun resetDefaultDiffFields() {
        _state.update {
            it.copy(candidateDiffFields = CANDIDATE_METADATA_FIELDS.associate { (k, _) -> k to true })
        }
    }

    /**
     * 将当前选中候选曲目中已勾选的字段内容合并写入编辑状态缓存。
     */
    fun applyCandidate() {
        val candidate = _state.value.selectedCandidate ?: return
        val fields = _state.value.candidateDiffFields

        _state.update { it.copy(showMatchDialog = false, isCoverDownloading = fields["cover"] == true) }
        coverDownloadJob?.cancel()

        coverDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            val provider = MuTagApplication.instance.getProviderForTrack(candidate)
            val detailResult = provider.getTrackDetail(candidate.trackId)
            val enriched = detailResult.getOrDefault(candidate)
            val lyricsObj = enriched.lyrics

            val shouldDownloadCover = fields["cover"] == true &&
                (enriched.coverUrl.isNotBlank() || enriched.album.id.isNotBlank())

            var downloadedCover: ByteArray? = null
            var coverMime: String? = null

            if (shouldDownloadCover) {
                try {
                    val downloaded = CoverDownloadHelper.downloadCover(
                        httpClient = httpClient,
                        trackMetadata = enriched,
                        provider = provider,
                    )
                    if (downloaded != null && downloaded.isNotEmpty()) {
                        downloadedCover = downloaded
                        coverMime = if (downloaded.size >= 8 && downloaded[0] == 0x89.toByte() && downloaded[1] == 0x50.toByte()) {
                            "image/png"
                        } else {
                            "image/jpeg"
                        }
                    } else {
                        _state.update { it.copy(userNoticeMessage = "封面处理失败，已保留文本信息") }
                    }
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        _state.update { it.copy(userNoticeMessage = "封面处理失败，已保留文本信息") }
                    }
                }
            }

            _state.update {
                it.copy(
                    isCoverDownloading = false,
                    coverBytes = downloadedCover ?: it.coverBytes,
                    coverMime = coverMime ?: it.coverMime,
                    coverDimensionsText = downloadedCover?.let { b -> "${b.size / 1024} KB" } ?: it.coverDimensionsText,
                    title = if (fields["title"] == true && enriched.title.isNotBlank()) enriched.title else it.title,
                    artist = if (fields["artist"] == true && enriched.artistName.isNotBlank()) enriched.artistName else it.artist,
                    album = if (fields["album"] == true && enriched.album.name.isNotBlank()) enriched.album.name else it.album,
                    albumArtist = if (fields["albumArtist"] == true &&
                        enriched.albumArtist.isNotBlank()
                    ) {
                        enriched.albumArtist
                    } else {
                        it.albumArtist
                    },
                    publishDate = if (fields["date"] == true && enriched.publishDate.isNotBlank()) enriched.publishDate else it.publishDate,
                    trackNumber = if (fields["trackNumber"] == true &&
                        enriched.trackNumber > 0
                    ) {
                        enriched.trackNumber.toString()
                    } else {
                        it.trackNumber
                    },
                    trackTotal = if (fields["trackTotal"] == true &&
                        enriched.trackTotal > 0
                    ) {
                        enriched.trackTotal.toString()
                    } else {
                        it.trackTotal
                    },
                    discNumber = if (fields["discNumber"] == true &&
                        enriched.discNumber > 0
                    ) {
                        enriched.discNumber.toString()
                    } else {
                        it.discNumber
                    },
                    discTotal = if (fields["discTotal"] == true && enriched.discTotal > 0) enriched.discTotal.toString() else it.discTotal,
                    genre = if (fields["genre"] == true && enriched.genre.isNotBlank()) enriched.genre else it.genre,
                    lyricist = if (fields["lyricist"] == true && enriched.lyricist.isNotBlank()) enriched.lyricist else it.lyricist,
                    composer = if (fields["composer"] == true && enriched.composer.isNotBlank()) enriched.composer else it.composer,
                    arranger = if (fields["arranger"] == true && enriched.arranger.isNotBlank()) enriched.arranger else it.arranger,
                    producer = if (fields["producer"] == true && enriched.producer.isNotBlank()) enriched.producer else it.producer,
                    company = if (fields["company"] == true && enriched.company.isNotBlank()) enriched.company else it.company,
                    comment = if (fields["comment"] == true && enriched.comment.isNotBlank()) enriched.comment else it.comment,
                    language = if (fields["language"] == true && enriched.language.isNotBlank()) enriched.language else it.language,
                    chorusStartMs = if (fields["chorusStartMs"] == true &&
                        enriched.chorusStartMs > 0
                    ) {
                        enriched.chorusStartMs.toString()
                    } else {
                        it.chorusStartMs
                    },
                    description = if (fields["description"] == true &&
                        enriched.album.description.isNotBlank()
                    ) {
                        enriched.album.description
                    } else {
                        it.description
                    },
                    cloudTrackId = if (fields["cloudTrackId"] == true &&
                        enriched.trackId.isNotBlank()
                    ) {
                        enriched.trackId
                    } else {
                        it.cloudTrackId
                    },
                    cloudAlbumMid = if (fields["cloudTrackId"] == true &&
                        enriched.album.id.isNotBlank()
                    ) {
                        enriched.album.id
                    } else {
                        it.cloudAlbumMid
                    },
                    replayGainTrackGain = enriched.replayGain?.let { "%.2f".format(it.trackGainDb) } ?: it.replayGainTrackGain,
                    replayGainTrackPeak = enriched.replayGain?.let { "%.6f".format(it.trackPeak) } ?: it.replayGainTrackPeak,
                    lyrics = if (fields["lyrics"] == true && lyricsObj != null) {
                        if (lyricsObj.mergedLrcText.isNotBlank()) lyricsObj.mergedLrcText else lyricsObj.rawLyricText
                    } else {
                        it.lyrics
                    },
                    syncedLyrics = if (fields["lyrics"] == true && lyricsObj != null) lyricsObj else it.syncedLyrics,
                )
            }
        }
    }

    /**
     * 提取音频切片声学指纹并向服务端发起指纹识别匹配。
     */
    fun recognizeByAcoustic() {
        val curFilePath = _state.value.filePath
        _state.update {
            it.copy(
                isSearchingOnline = true,
                showMatchDialog = true,
            )
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val f = File(curFilePath)
            if (!f.exists() || !f.isFile) {
                _state.update { it.copy(isSearchingOnline = false) }
                return@launch
            }

            val primaryProvider = getProviderForCurrentSource()
            val primaryType = if (primaryProvider.sourceId == "netease") {
                org.mutag.model.AcousticFeatureType.NETEASE
            } else {
                org.mutag.model.AcousticFeatureType.QQ_MUSIC
            }

            preSliceJob?.join()
            val slice = if (cachedSliceResult?.feature?.featureType == primaryType) {
                cachedSliceResult
            } else {
                org.mutag.core.tagger.recognizer.AudioSliceExtractor.extractSliceWithTime(
                    audioFile = f,
                    preferredStartMs = _state.value.chorusStartMs.toLongOrNull() ?: 0L,
                    featureType = primaryType,
                ).also { cachedSliceResult = it }
            }

            if (slice == null) {
                _state.update { it.copy(isSearchingOnline = false) }
                return@launch
            }

            val fallbackProvider = if (AppSettingsManager.settingsState.value.dualSourceFallback) {
                if (_state.value.matchSource == org.mutag.app.settings.MetadataSource.NETEASE) {
                    MuTagApplication.instance.qqMusicProvider
                } else {
                    MuTagApplication.instance.neteaseProvider
                }
            } else {
                null
            }

            var acousticRes = primaryProvider.recognizeByAcoustic(slice.feature).getOrNull()
            if (acousticRes == null && fallbackProvider != null) {
                val fallbackType = if (fallbackProvider.sourceId == "netease") {
                    org.mutag.model.AcousticFeatureType.NETEASE
                } else {
                    org.mutag.model.AcousticFeatureType.QQ_MUSIC
                }
                val fallbackSlice = org.mutag.core.tagger.recognizer.AudioSliceExtractor.extractSliceWithTime(
                    audioFile = f,
                    preferredStartMs = _state.value.chorusStartMs.toLongOrNull() ?: 0L,
                    featureType = fallbackType,
                )
                if (fallbackSlice != null) {
                    acousticRes = fallbackProvider.recognizeByAcoustic(fallbackSlice.feature).getOrNull()
                }
            }
            val list = if (acousticRes != null) listOf(acousticRes) else emptyList()

            _state.update {
                it.copy(
                    isSearchingOnline = false,
                    onlineSearchQuery = acousticRes?.let { res -> "${res.title} ${res.artistName}" } ?: it.onlineSearchQuery,
                    searchCandidates = list,
                    selectedCandidate = list.firstOrNull(),
                    candidateDiffFields = CANDIDATE_METADATA_FIELDS.associate { (k, _) -> k to true },
                )
            }
        }
    }

    /**
     * 关闭在线匹配弹窗。
     */
    fun dismissMatchDialog() {
        searchJob?.cancel()
        preSliceJob?.cancel()
        cachedSliceResult = null
        _state.update { it.copy(showMatchDialog = false) }
    }

    /**
     * 将界面编辑的全部元数据与封面载荷写入目标音频文件。
     *
     * @param onSuccess 写入并刷新成功后的回调函数
     */
    fun saveChanges(onSuccess: () -> Unit) {
        val initialFile = File(_state.value.filePath)
        if (!initialFile.exists()) return

        _state.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch(Dispatchers.IO) {
            if (coverDownloadJob?.isActive == true) {
                withTimeoutOrNull(5000L) {
                    coverDownloadJob?.join()
                }
            }

            val s = _state.value
            val file = File(s.filePath)
            if (!file.exists()) {
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(isSaving = false) }
                }
                return@launch
            }

            val payload = MetadataPayload(
                title = s.title,
                artist = s.artist,
                album = s.album,
                albumArtist = s.albumArtist,
                trackNumber = s.trackNumber.toIntOrNull() ?: 0,
                trackTotal = s.trackTotal.toIntOrNull() ?: 0,
                discNumber = s.discNumber.toIntOrNull() ?: 1,
                discTotal = s.discTotal.toIntOrNull() ?: 1,
                publishDate = s.publishDate,
                genre = s.genre,
                lyricist = s.lyricist,
                composer = s.composer,
                arranger = s.arranger,
                producer = s.producer,
                company = s.company,
                comment = s.comment,
                language = s.language,
                chorusStartMs = s.chorusStartMs.toLongOrNull() ?: 0L,
                replayGainTrackGain = s.replayGainTrackGain.removeSuffix("dB").removeSuffix("db").removeSuffix("DB").trim().toDoubleOrNull(),
                replayGainTrackPeak = s.replayGainTrackPeak.trim().toDoubleOrNull(),
                cloudTrackId = s.cloudTrackId,
                cloudAlbumMid = s.cloudAlbumMid,
                description = s.description,
                lyrics = s.lyrics.ifBlank { null },
                coverBytes = s.coverBytes,
                coverMime = s.coverMime.ifBlank { "image/jpeg" },
            )

            val success = AudioMetadataWriter.writeMetadata(file, payload)

            if (success) {
                // 重新解析音频文件并更新缓存
                val updated = AudioHeaderParser.parse(file)
                cache.put(updated)
                MediaScanHelper.scanFile(MuTagApplication.instance, file.absolutePath)

                withContext(Dispatchers.Main) {
                    _state.update { it.copy(isSaving = false, saveSuccess = true) }
                    onSuccess()
                }
            } else {
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(isSaving = false, saveSuccess = false, errorMessage = "写入音频文件失败，请检查文件权限与存储空间") }
                }
            }
        }
    }

    /**
     * 打开封面操作菜单底栏。
     */
    fun openCoverActionSheet() {
        _state.update { it.copy(showCoverActionSheet = true) }
    }

    /**
     * 关闭封面操作菜单底栏。
     */
    fun dismissCoverActionSheet() {
        _state.update { it.copy(showCoverActionSheet = false) }
    }

    /**
     * 打开全屏封面大图查看器。
     */
    fun openFullCoverViewer() {
        _state.update { it.copy(showCoverActionSheet = false, showFullCoverViewer = true) }
    }

    /**
     * 关闭全屏封面大图查看器。
     */
    fun dismissFullCoverViewer() {
        _state.update { it.copy(showFullCoverViewer = false) }
    }

    /**
     * 打开音频技术规格参数弹窗。
     */
    fun openTechSpecDialog() {
        _state.update { it.copy(showCoverActionSheet = false, showTechSpecDialog = true) }
    }

    /**
     * 关闭音频技术规格参数弹窗。
     */
    fun dismissTechSpecDialog() {
        _state.update { it.copy(showTechSpecDialog = false) }
    }

    /**
     * 请求保存内嵌封面到相册，弹出确认对话框。
     */
    fun requestSaveCover() {
        _state.update { it.copy(showCoverActionSheet = false, showSaveCoverConfirm = true) }
    }

    /**
     * 关闭保存封面确认对话框。
     */
    fun dismissSaveCoverConfirm() {
        _state.update { it.copy(showSaveCoverConfirm = false) }
    }

    /**
     * 确认将当前内嵌封面图片导出保存到系统相册。
     *
     * @param context Android 上下文实例
     */
    fun confirmSaveCover(context: Context) {
        val bytes = _state.value.coverBytes
        if (bytes == null || bytes.isEmpty()) {
            _state.update { it.copy(showSaveCoverConfirm = false, userNoticeMessage = "当前音频无可用封面") }
            return
        }
        val title = _state.value.title
        val album = _state.value.album
        val result = CoverExportHelper.saveCoverToGallery(context, bytes, title, album)
        _state.update {
            it.copy(
                showSaveCoverConfirm = false,
                userNoticeMessage = result.fold(
                    onSuccess = { name -> "封面已保存至相册：$name" },
                    onFailure = { err -> "保存失败: ${err.message}" },
                ),
            )
        }
    }

    /**
     * 替换当前编辑中的内嵌封面图片字节流。
     *
     * @param rawBytes 待替换的图像字节数组
     */
    fun replaceCover(rawBytes: ByteArray) {
        val maxSizeBytes = CoverDownloadHelper.MAX_COVER_IMAGE_SIZE
        val maxDimension = CoverDownloadHelper.MAX_COVER_DIMENSION
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
        val needsCompress = rawBytes.size > maxSizeBytes || maxOf(opts.outWidth, opts.outHeight) > maxDimension
        val safeBytes = if (needsCompress) {
            CoverDownloadHelper.compressCoverSafely(rawBytes)
        } else {
            rawBytes
        }
        _state.update {
            it.copy(
                showCoverActionSheet = false,
                coverBytes = safeBytes,
                coverDimensionsText = "${safeBytes.size / 1024} KB",
                userNoticeMessage = if (needsCompress) "图片已保真优化至 3000px / 2.5MB 内" else null,
            )
        }
    }

    /**
     * 移除当前编辑中的内嵌封面。
     */
    fun removeCover() {
        _state.update {
            it.copy(
                showCoverActionSheet = false,
                coverBytes = null,
                coverDimensionsText = "无内嵌封面",
            )
        }
    }

    /**
     * 设置用户通知提示文本。
     *
     * @param message 提示文本内容
     */
    fun showUserNotice(message: String) {
        _state.update { it.copy(userNoticeMessage = message) }
    }

    /**
     * 清理界面用户通知提示文本。
     */
    fun clearUserNotice() {
        _state.update { it.copy(userNoticeMessage = null) }
    }

    /**
     * 打开歌词独立搜索弹窗，并默认根据当前曲目标题与歌手发起双源检索。
     */
    fun openLyricSearchDialog() {
        val s = _state.value
        val defaultQuery = if (s.title.isNotBlank()) {
            if (s.artist.isNotBlank()) "${s.title} ${s.artist}" else s.title
        } else {
            s.fileName.substringBeforeLast('.')
        }.trim()

        _state.update {
            it.copy(
                showLyricSearchDialog = true,
                lyricSearchQuery = defaultQuery,
            )
        }
        if (defaultQuery.isNotBlank()) {
            searchLyrics(defaultQuery)
        }
    }

    /**
     * 关闭歌词独立搜索弹窗。
     */
    fun closeLyricSearchDialog() {
        lyricSearchJob?.cancel()
        _state.update { it.copy(showLyricSearchDialog = false) }
    }

    /**
     * 并发使用 QQ 音乐和网易云搜索歌词候选曲目。
     *
     * @param query 搜索关键词
     */
    fun searchLyrics(query: String? = null) {
        val q = (query ?: _state.value.lyricSearchQuery).trim()
        if (q.isBlank()) return

        _state.update {
            it.copy(
                isSearchingLyrics = true,
                lyricSearchQuery = q,
                lyricCandidates = emptyList(),
            )
        }

        lyricSearchJob?.cancel()
        lyricSearchJob = viewModelScope.launch(Dispatchers.IO) {
            val qqDeferred = async {
                MuTagApplication.instance.qqMusicProvider.search(q, page = 1, pageSize = 15).getOrDefault(emptyList())
            }
            val neDeferred = async {
                MuTagApplication.instance.neteaseProvider.search(q, page = 1, pageSize = 15).getOrDefault(emptyList())
            }

            val qqResults = qqDeferred.await()
            val neResults = neDeferred.await()

            val targetTitle = _state.value.title.ifBlank { q }
            val targetArtist = _state.value.artist
            val targetDurationMs = _state.value.durationMs

            // 依据时长吻合度与标签一致性排序
            val sortedQq = qqResults.sortedByDescending { scoreLyricCandidate(it, targetTitle, targetArtist, targetDurationMs) }
            val sortedNe = neResults.sortedByDescending { scoreLyricCandidate(it, targetTitle, targetArtist, targetDurationMs) }

            // QQ 音乐与网易云 1:1 交替排列
            val candidates = mutableListOf<LyricCandidate>()
            val maxLen = maxOf(sortedQq.size, sortedNe.size)
            for (i in 0 until maxLen) {
                if (i < sortedQq.size) {
                    val t = sortedQq[i]
                    candidates.add(
                        LyricCandidate(
                            id = "qqmusic_${t.trackId}_$i",
                            trackId = t.trackId,
                            sourceId = "qqmusic",
                            songIdLong = t.trackId.toLongOrNull() ?: 0L,
                            title = t.title,
                            artist = t.artistName,
                            album = t.album.name,
                            durationMs = t.durationMs,
                        ),
                    )
                }
                if (i < sortedNe.size) {
                    val t = sortedNe[i]
                    candidates.add(
                        LyricCandidate(
                            id = "netease_${t.trackId}_$i",
                            trackId = t.trackId,
                            sourceId = "netease",
                            songIdLong = t.trackId.toLongOrNull() ?: 0L,
                            title = t.title,
                            artist = t.artistName,
                            album = t.album.name,
                            durationMs = t.durationMs,
                        ),
                    )
                }
            }

            _state.update {
                it.copy(
                    isSearchingLyrics = false,
                    lyricCandidates = candidates,
                    hasFilteredPureMusic = false,
                )
            }
        }
    }

    private fun scoreLyricCandidate(track: TrackMetadata, targetTitle: String, targetArtist: String, targetDurationMs: Long): Double {
        var score = 0.0

        val cleanTargetTitle = targetTitle.replace(Regex("""\s*[\(\[（【].*?[\)\]）】]"""), "").trim().lowercase()
        val cleanTrackTitle = track.title.replace(Regex("""\s*[\(\[（【].*?[\)\]）】]"""), "").trim().lowercase()
        val rawTargetTitle = targetTitle.trim().lowercase()
        val rawTrackTitle = track.title.trim().lowercase()

        if (rawTrackTitle == rawTargetTitle && rawTrackTitle.isNotBlank()) {
            score += 50.0
        } else if (cleanTrackTitle == cleanTargetTitle && cleanTrackTitle.isNotBlank()) {
            score += 42.0
        } else if (cleanTrackTitle.contains(cleanTargetTitle) || cleanTargetTitle.contains(cleanTrackTitle)) {
            score += 25.0
        }

        val cleanTargetArtist = targetArtist.trim().lowercase()
        val cleanTrackArtist = track.artistName.trim().lowercase()
        if (cleanTargetArtist.isNotBlank()) {
            if (cleanTrackArtist == cleanTargetArtist) {
                score += 25.0
            } else if (cleanTrackArtist.contains(cleanTargetArtist) || cleanTargetArtist.contains(cleanTrackArtist)) {
                score += 15.0
            }
        }

        if (targetDurationMs > 0 && track.durationMs > 0) {
            val diffMs = kotlin.math.abs(track.durationMs - targetDurationMs)
            when {
                diffMs <= 3_000L -> score += 30.0
                diffMs <= 8_000L -> score += 20.0
                diffMs <= 15_000L -> score += 10.0
                diffMs <= 25_000L -> score += 0.0
                else -> score -= 15.0
            }
        }

        return score
    }

    /**
     * 展开或收起某条候选曲目的歌词预览，按需异步拉取并过滤纯音乐。
     *
     * @param candidate 目标候选
     */
    fun toggleLyricPreview(candidate: LyricCandidate) {
        val currentList = _state.value.lyricCandidates
        val target = currentList.find { it.id == candidate.id } ?: return

        if (target.isExpanded) {
            _state.update {
                it.copy(
                    lyricCandidates = currentList.map { c ->
                        if (c.id == target.id) c.copy(isExpanded = false) else c
                    },
                )
            }
            return
        }

        if (target.lyrics != null) {
            _state.update {
                it.copy(
                    lyricCandidates = currentList.map { c ->
                        if (c.id == target.id) c.copy(isExpanded = true) else c
                    },
                )
            }
            return
        }

        _state.update {
            it.copy(
                lyricCandidates = currentList.map { c ->
                    if (c.id == target.id) c.copy(isExpanded = true, isLoadingLyrics = true) else c
                },
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val provider = if (target.sourceId == "netease") {
                MuTagApplication.instance.neteaseProvider
            } else {
                MuTagApplication.instance.qqMusicProvider
            }

            val lyricsRes = provider.getLyrics(
                trackId = target.trackId,
                songIdLong = target.songIdLong,
                title = target.title,
                artist = target.artist,
                durationSec = (target.durationMs / 1000).toInt(),
            ).getOrNull()

            val lrcText = lyricsRes?.mergedLrcText.orEmpty()
            val isPure = org.mutag.provider.lyric.PureMusicDetector.isPureMusic(lrcText)

            if (isPure) {
                // 纯音乐直接从候选列表中剔除，不予显示
                _state.update { state ->
                    val remaining = state.lyricCandidates.filterNot { it.id == target.id }
                    state.copy(
                        lyricCandidates = remaining,
                        hasFilteredPureMusic = true,
                        userNoticeMessage = "已自动排除纯音乐候选：${target.title}",
                    )
                }
                return@launch
            }

            _state.update { state ->
                state.copy(
                    lyricCandidates = state.lyricCandidates.map { c ->
                        if (c.id == target.id) {
                            c.copy(
                                lyrics = lrcText,
                                isPureMusic = false,
                                isLoadingLyrics = false,
                                isExpanded = true,
                            )
                        } else {
                            c
                        }
                    },
                )
            }
        }
    }

    /**
     * 切换歌词搜索结果中的音源显隐过滤状态。
     *
     * @param sourceId "qqmusic" 或 "netease"
     */
    fun toggleLyricSourceFilter(sourceId: String) {
        val s = _state.value
        if (sourceId == "qqmusic") {
            if (s.showQqLyrics && !s.showNeteaseLyrics) return
            _state.update { it.copy(showQqLyrics = !it.showQqLyrics) }
        } else if (sourceId == "netease") {
            if (s.showNeteaseLyrics && !s.showQqLyrics) return
            _state.update { it.copy(showNeteaseLyrics = !it.showNeteaseLyrics) }
        }
    }

    /**
     * 选用指定候选的歌词并填入当前编辑状态中。
     *
     * @param candidate 目标候选
     */
    fun applyLyricCandidate(candidate: LyricCandidate) {
        viewModelScope.launch(Dispatchers.IO) {
            val lrcText = if (candidate.lyrics != null) {
                candidate.lyrics
            } else {
                val provider = if (candidate.sourceId == "netease") {
                    MuTagApplication.instance.neteaseProvider
                } else {
                    MuTagApplication.instance.qqMusicProvider
                }
                provider.getLyrics(
                    trackId = candidate.trackId,
                    songIdLong = candidate.songIdLong,
                    title = candidate.title,
                    artist = candidate.artist,
                    durationSec = (candidate.durationMs / 1000).toInt(),
                ).getOrNull()?.mergedLrcText.orEmpty()
            }

            val isPure = org.mutag.provider.lyric.PureMusicDetector.isPureMusic(lrcText)
            if (isPure) {
                _state.update {
                    it.copy(
                        userNoticeMessage = "该候选为纯音乐，无有效歌词",
                    )
                }
                return@launch
            }

            val sourceName = if (candidate.sourceId == "netease") "网易云音乐" else "QQ音乐"
            withContext(Dispatchers.Main) {
                updateLyrics(lrcText)
                _state.update {
                    it.copy(
                        showLyricSearchDialog = false,
                        userNoticeMessage = "已应用来自 $sourceName 的歌词",
                    )
                }
            }
        }
    }
}
