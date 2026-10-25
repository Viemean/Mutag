package org.mutag.app.main

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.mutag.app.MuTagApplication
import org.mutag.app.main.rename.BatchRenameExecutor
import org.mutag.app.media.MediaScanHelper
import org.mutag.app.notification.MatchingNotificationHelper
import org.mutag.app.ui.cover.CoverDownloadHelper
import org.mutag.app.ui.cover.EmbeddedCoverFetcher
import org.mutag.core.tagger.scanner.AudioFileScanner
import org.mutag.core.tagger.scanner.AudioHeaderParser
import org.mutag.core.tagger.scanner.AudioMetadata
import org.mutag.core.tagger.scanner.TagHealth
import org.mutag.core.tagger.storage.StorageAccessManager
import org.mutag.core.tagger.writer.AudioMetadataWriter
import org.mutag.core.tagger.writer.MetadataPayload
import org.mutag.model.TrackMetadata
import org.mutag.provider.api.MusicMetadataProvider

/**
 * 主界面 ViewModel，负责文件系统浏览、媒体库扫描、多选操作、批量重命名与批量标签匹配调度。
 */
class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    private val cache = MuTagApplication.instance.cache
    private val metadataProvider: MusicMetadataProvider
        get() = MuTagApplication.instance.getActiveProvider()
    private val httpClient = OkHttpClient()

    init {
        val defaultDir = StorageAccessManager.getDefaultMusicDirectory()
        _state.update { it.copy(initialPath = defaultDir.absolutePath) }
        navigateToDirectory(defaultDir.absolutePath)
        scanSystemAudioLibrary(rescanFiles = false)
        restoreUncertainMatches()
        checkDailyUpdate()
    }

    /**
     * 从本地持久化存储中恢复未处理的存疑匹配项并刷新展示。
     */
    private fun restoreUncertainMatches() {
        viewModelScope.launch(Dispatchers.IO) {
            val restored = UncertainMatchRepository.load(MuTagApplication.instance, cache)
            if (restored.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(uncertainMatches = restored) }
                }
            }
        }
    }

    /**
     * 扫描系统媒体库音频曲目并重新聚合计算。
     *
     * @param rescanFiles 是否先行扫描物理存储目录同步 MediaStore 索引
     */
    fun scanSystemAudioLibrary(rescanFiles: Boolean = true) {
        val context = MuTagApplication.instance
        _state.update { it.copy(isSystemScanning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            if (rescanFiles) {
                MediaScanHelper.scanCommonMusicDirectories(context, _state.value.currentPath)
            }
            val songs = AudioFileScanner.scanSystemAudio(context, cache)
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        isSystemScanning = false,
                        systemTracks = songs,
                    )
                }
                applySorting()
                recalculateAggregations()
            }
        }
    }

    /**
     * 切换主界面视图分类 Tab。
     *
     * @param tab 目标分类 Tab
     */
    fun setTab(tab: MainTab) {
        _state.update { it.copy(currentTab = tab) }
    }

    /**
     * 更新曲库与文件过滤搜索词。
     *
     * @param query 搜索关键词
     */
    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    /**
     * 设置文件与曲目排序模式。
     *
     * @param mode 目标排序规则
     */
    fun setSortMode(mode: SortMode) {
        _state.update { it.copy(sortMode = mode, showSortBottomSheet = false) }
        applySorting()
    }

    /**
     * 展开排序选项底栏。
     */
    fun openSortBottomSheet() {
        _state.update { it.copy(showSortBottomSheet = true) }
    }

    /**
     * 关闭排序选项底栏。
     */
    fun dismissSortBottomSheet() {
        _state.update { it.copy(showSortBottomSheet = false) }
    }

    /**
     * 切换目录视图下显示原始文件名或解析后曲目标题。
     */
    fun toggleFileNameView() {
        _state.update { it.copy(showFileNameInFilesTab = !it.showFileNameInFilesTab) }
    }

    /**
     * 导航至指定文件夹目录并保存当前滚动位置快照。
     *
     * @param path 目标目录绝对路径
     * @param currentScrollIndex 当前离开界面的滚动项索引
     * @param currentScrollOffset 当前离开界面的滚动像素偏移
     */
    fun navigateToDirectory(path: String, currentScrollIndex: Int = 0, currentScrollOffset: Int = 0) {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return

        val crumbs = buildBreadcrumbs(dir)
        val currentState = _state.value
        val currentPath = currentState.currentPath

        val updatedHistory = if (currentPath.isNotBlank() && currentPath != dir.absolutePath) {
            val snapshot = DirectorySnapshot(
                path = currentPath,
                folders = currentState.folders,
                tracks = currentState.tracks,
                scrollIndex = currentScrollIndex,
                scrollOffset = currentScrollOffset,
            )
            (currentState.directoryHistory + snapshot).takeLast(3)
        } else {
            currentState.directoryHistory
        }

        _state.update {
            it.copy(
                currentPath = dir.absolutePath,
                breadcrumbs = crumbs,
                isLoading = true,
                directoryHistory = updatedHistory,
                pendingScrollRestore = null,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = AudioFileScanner.scanDirectory(dir, cache)
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        folders = result.folders,
                        tracks = result.tracks,
                    )
                }
                applySorting()
                recalculateAggregations()
            }
        }
    }

    private fun buildBreadcrumbs(targetDir: File): List<BreadcrumbItem> {
        val rootPath = targetDir.absolutePath
        val crumbs = mutableListOf<BreadcrumbItem>()
        val primaryStorage = Environment.getExternalStorageDirectory().absolutePath
        val canonicalPath = try {
            targetDir.canonicalPath
        } catch (_: Exception) {
            rootPath
        }

        val isUnderPrimaryStorage = rootPath.startsWith(primaryStorage) || canonicalPath.startsWith(primaryStorage)

        if (isUnderPrimaryStorage) {
            crumbs.add(BreadcrumbItem("内部存储", primaryStorage))
            val effectivePath = if (rootPath.startsWith(primaryStorage)) rootPath else canonicalPath
            val relativePath = effectivePath.removePrefix(primaryStorage).trim('/')
            if (relativePath.isNotEmpty()) {
                val parts = relativePath.split(File.separator).filter { it.isNotBlank() }
                var currentAccumulated = primaryStorage
                parts.forEach { part ->
                    currentAccumulated = "$currentAccumulated/$part"
                    crumbs.add(BreadcrumbItem(part, currentAccumulated))
                }
            }
        } else {
            crumbs.add(BreadcrumbItem("根目录", "/"))
            val parts = rootPath.split(File.separator).filter { it.isNotBlank() }
            var currentAccumulated = ""
            parts.forEach { part ->
                currentAccumulated = "$currentAccumulated/$part"
                crumbs.add(BreadcrumbItem(part, currentAccumulated))
            }
        }
        return crumbs
    }

    /**
     * 沿导航面包屑跳转至历史祖先目录。
     *
     * @param targetPath 目标祖先目录绝对路径
     * @param currentScrollIndex 当前滚动索引
     * @param currentScrollOffset 当前滚动像素偏移
     */
    fun navigateToBreadcrumb(targetPath: String, currentScrollIndex: Int = 0, currentScrollOffset: Int = 0) {
        val dir = File(targetPath)
        if (!dir.exists() || !dir.isDirectory) return
        val effectivePath = dir.absolutePath

        val history = _state.value.directoryHistory
        val matchIndex = history.indexOfLast { it.path == effectivePath }
        if (matchIndex != -1) {
            val snapshot = history[matchIndex]
            _state.update {
                it.copy(
                    currentPath = snapshot.path,
                    breadcrumbs = buildBreadcrumbs(File(snapshot.path)),
                    folders = snapshot.folders,
                    tracks = snapshot.tracks,
                    isLoading = false,
                    directoryHistory = history.take(matchIndex),
                    pendingScrollRestore = Pair(snapshot.scrollIndex, snapshot.scrollOffset),
                )
            }
            applySorting()
        } else {
            navigateToDirectory(effectivePath, currentScrollIndex, currentScrollOffset)
        }
    }

    /**
     * 检测当前目录是否支持向上返回上一级。
     *
     * @return 存在父级目录历史时返回 true
     */
    fun canNavigateUp(): Boolean {
        val currentState = _state.value
        if (currentState.currentTab != MainTab.FILES) return false
        if (currentState.breadcrumbs.size <= 1) return false
        val current = currentState.currentPath
        val initial = currentState.initialPath
        if (current.isBlank() || initial.isBlank()) return false
        val currentFile = File(current)
        val initialFile = File(initial)
        val isInitial = try {
            currentFile.canonicalPath == initialFile.canonicalPath
        } catch (_: Exception) {
            currentFile.absolutePath == initialFile.absolutePath
        }
        return !isInitial
    }

    /**
     * 返回上一级父目录并恢复父级滚动位置。
     */
    fun navigateUp() {
        val current = File(_state.value.currentPath)
        val parent = current.parentFile ?: return
        val parentPath = parent.absolutePath

        val history = _state.value.directoryHistory
        val lastSnapshot = history.lastOrNull()
        if (lastSnapshot != null && lastSnapshot.path == parentPath) {
            _state.update {
                it.copy(
                    currentPath = lastSnapshot.path,
                    breadcrumbs = buildBreadcrumbs(File(lastSnapshot.path)),
                    folders = lastSnapshot.folders,
                    tracks = lastSnapshot.tracks,
                    isLoading = false,
                    directoryHistory = history.dropLast(1),
                    pendingScrollRestore = Pair(lastSnapshot.scrollIndex, lastSnapshot.scrollOffset),
                )
            }
            applySorting()
        } else {
            navigateToDirectory(parentPath)
        }
    }

    /**
     * 消费并清除待恢复的列表滚动位置。
     */
    fun consumePendingScrollRestore() {
        _state.update { it.copy(pendingScrollRestore = null) }
    }

    /**
     * 刷新当前目录与系统媒体库数据。
     */
    fun refresh() {
        navigateToDirectory(_state.value.currentPath)
        scanSystemAudioLibrary(rescanFiles = false)
    }

    private fun applySorting() {
        val mode = _state.value.sortMode
        val currentTracks = _state.value.tracks.toMutableList()
        val currentSystemTracks = _state.value.systemTracks.toMutableList()

        val comparator: Comparator<AudioMetadata> = when (mode) {
            SortMode.NAME -> compareBy { it.title.ifBlank { it.fileName }.lowercase() }
            SortMode.DATE -> compareByDescending { it.lastModified }
            SortMode.SIZE -> compareByDescending { it.fileSize }
        }

        currentTracks.sortWith(comparator)
        currentSystemTracks.sortWith(comparator)

        _state.update {
            it.copy(
                tracks = currentTracks,
                systemTracks = currentSystemTracks,
            )
        }
        recalculateAggregations()
    }

    private fun recalculateAggregations() {
        val tracks = if (_state.value.systemTracks.isNotEmpty()) _state.value.systemTracks else _state.value.tracks
        val rawAlbums = tracks.groupBy { it.album.ifBlank { "未知专辑" } }
        val rawArtists = tracks.groupBy { it.artist.ifBlank { "未知艺术家" } }

        val mode = _state.value.sortMode
        val sortedAlbums = when (mode) {
            SortMode.NAME -> rawAlbums.entries.sortedBy { it.key.lowercase() }.associate { it.toPair() }

            SortMode.DATE -> rawAlbums.entries.sortedByDescending { (_, list) ->
                list.maxOfOrNull { it.lastModified } ?: 0L
            }.associate { it.toPair() }

            SortMode.SIZE -> rawAlbums.entries.sortedByDescending { (_, list) -> list.sumOf { it.fileSize } }.associate { it.toPair() }
        }

        val sortedArtists = when (mode) {
            SortMode.NAME -> rawArtists.entries.sortedBy { it.key.lowercase() }.associate { it.toPair() }

            SortMode.DATE -> rawArtists.entries.sortedByDescending { (_, list) ->
                list.maxOfOrNull { it.lastModified } ?: 0L
            }.associate { it.toPair() }

            SortMode.SIZE -> rawArtists.entries.sortedByDescending { (_, list) -> list.sumOf { it.fileSize } }.associate { it.toPair() }
        }

        _state.update {
            it.copy(
                albums = sortedAlbums,
                artists = sortedArtists,
            )
        }
    }

    /**
     * 启动多曲目批量在线匹配流水线。
     *
     * @param tracksToMatch 指定匹配的曲目列表，为 null 时默认匹配当前界面所有待匹配曲目
     */
    fun startBatchMatching(tracksToMatch: List<AudioMetadata>? = null) {
        val pendingTracks = tracksToMatch ?: if (_state.value.isSelectionMode) {
            _state.value.tracks.filter { it.filePath in _state.value.selectedTrackPaths }
        } else {
            _state.value.tracks.filter { it.tagHealth == TagHealth.NEEDS_MATCH }
        }
        if (pendingTracks.isEmpty()) return

        _state.update {
            it.copy(
                isSelectionMode = false,
                selectedTrackPaths = emptySet(),
                isWritingBatch = true,
                batchProgress = 0f,
                batchSuccessCount = 0,
                batchTotalCount = pendingTracks.size,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val uncertainList = try {
                BatchMatchPipeline.execute(
                    pendingTracks = pendingTracks,
                    metadataProvider = metadataProvider,
                    fallbackProvider = MuTagApplication.instance.getFallbackProvider(),
                    httpClient = httpClient,
                    cache = cache,
                    context = MuTagApplication.instance,
                    onProgress = { progress, successCount, total ->
                        withContext(Dispatchers.Main) {
                            _state.update {
                                it.copy(
                                    batchProgress = progress,
                                    batchSuccessCount = successCount,
                                    batchTotalCount = total,
                                )
                            }
                        }
                    },
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("MainViewModel", "Batch matching failed", e)
                emptyList()
            }

            withContext(Dispatchers.Main) {
                _state.update { s ->
                    val newPaths = uncertainList.map { it.localFile.filePath }.toSet()
                    val filteredOld = s.uncertainMatches.filter { it.localFile.filePath !in newPaths }
                    val combined = uncertainList + filteredOld
                    UncertainMatchRepository.save(MuTagApplication.instance, combined)
                    s.copy(
                        isWritingBatch = false,
                        uncertainMatches = combined,
                    )
                }
                refresh()
            }
        }
    }

    /**
     * 展开批量匹配存疑结果核验底栏。
     */
    fun openUncertainSheet() {
        _state.update { it.copy(showUncertainSheet = true) }
    }

    /**
     * 关闭批量匹配存疑结果核验底栏。
     */
    fun dismissUncertainSheet() {
        _state.update { it.copy(showUncertainSheet = false) }
    }

    private suspend fun writeUncertainItemPayload(item: UncertainMatchItem) {
        val candidate = item.candidate ?: return
        val file = File(item.localFile.filePath)
        if (!file.exists()) return

        val provider = MuTagApplication.instance.getProviderForTrack(candidate)
        val detail = provider.getTrackDetail(candidate.trackId).getOrDefault(candidate)
        val coverBytes = if (detail.coverUrl.isNotBlank() || detail.album.id.isNotBlank()) {
            CoverDownloadHelper.downloadCover(httpClient, detail)
        } else {
            EmbeddedCoverFetcher.extractCoverBytes(file)
        }

        val payload = MetadataPayload(
            title = detail.title,
            artist = detail.artistName,
            album = detail.album.name,
            albumArtist = detail.albumArtist.ifBlank { item.localFile.albumArtist },
            trackNumber = detail.trackNumber,
            trackTotal = detail.trackTotal,
            discNumber = detail.discNumber,
            discTotal = detail.discTotal,
            publishDate = detail.publishDate,
            genre = detail.genre.ifBlank { item.localFile.genre },
            lyricist = detail.lyricist.ifBlank { item.localFile.lyricist },
            composer = detail.composer.ifBlank { item.localFile.composer },
            arranger = detail.arranger.ifBlank { item.localFile.arranger },
            producer = detail.producer.ifBlank { item.localFile.producer },
            company = detail.company.ifBlank { item.localFile.company },
            comment = detail.comment.ifBlank { item.localFile.comment },
            description = detail.description.ifBlank { item.localFile.description },
            cloudTrackId = detail.trackId,
            cloudAlbumMid = detail.album.id,
            replayGainTrackGain = detail.replayGain?.trackGainDb ?: item.localFile.replayGainTrackGain,
            replayGainTrackPeak = detail.replayGain?.trackPeak ?: item.localFile.replayGainTrackPeak,
            language = detail.language.ifBlank { item.localFile.language },
            chorusStartMs = if (detail.chorusStartMs > 0) detail.chorusStartMs else item.localFile.chorusStartMs,
            lyrics = detail.lyrics?.mergedLrcText ?: detail.lyrics?.rawLyricText ?: item.localFile.lyrics,
            coverBytes = coverBytes,
        )

        if (AudioMetadataWriter.writeMetadata(file, payload)) {
            val updated = AudioHeaderParser.parse(file)
            cache.put(updated)
            MediaScanHelper.scanFile(MuTagApplication.instance, file.absolutePath)
        }
    }

    /**
     * 采纳并写入单条存疑匹配项的标签。
     *
     * @param item 存疑匹配项数据
     */
    fun applyUncertainMatch(item: UncertainMatchItem) {
        viewModelScope.launch(Dispatchers.IO) {
            writeUncertainItemPayload(item)
            withContext(Dispatchers.Main) {
                _state.update { s ->
                    val nextList = s.uncertainMatches.filter { it != item }
                    UncertainMatchRepository.save(MuTagApplication.instance, nextList)
                    s.copy(
                        uncertainMatches = nextList,
                        showUncertainSheet = nextList.isNotEmpty() && s.showUncertainSheet,
                    )
                }
                MatchingNotificationHelper.updateAlertOrCancel(MuTagApplication.instance, _state.value.uncertainMatches.size)
                refresh()
            }
        }
    }

    /**
     * 批量采纳并写入全部有候选的存疑匹配项标签。
     */
    fun applyAllUncertainMatches() {
        val itemsToApply = _state.value.uncertainMatches.filter { it.candidate != null }
        if (itemsToApply.isEmpty()) {
            clearAllUncertainMatches()
            return
        }

        _state.update { it.copy(showUncertainSheet = false, uncertainMatches = emptyList()) }
        UncertainMatchRepository.clear(MuTagApplication.instance)
        MatchingNotificationHelper.cancelAlert(MuTagApplication.instance)

        viewModelScope.launch(Dispatchers.IO) {
            itemsToApply.forEach { item ->
                writeUncertainItemPayload(item)
            }
            withContext(Dispatchers.Main) {
                refresh()
            }
        }
    }

    /**
     * 忽略并从列表中移除单条存疑匹配项。
     *
     * @param item 目标存疑项
     */
    fun dismissUncertainItem(item: UncertainMatchItem) {
        _state.update { s ->
            val nextList = s.uncertainMatches.filter { it != item }
            UncertainMatchRepository.save(MuTagApplication.instance, nextList)
            s.copy(
                uncertainMatches = nextList,
                showUncertainSheet = nextList.isNotEmpty() && s.showUncertainSheet,
            )
        }
        MatchingNotificationHelper.updateAlertOrCancel(MuTagApplication.instance, _state.value.uncertainMatches.size)
    }

    /**
     * 清空全部存疑匹配条目。
     */
    fun clearAllUncertainMatches() {
        _state.update { it.copy(uncertainMatches = emptyList(), showUncertainSheet = false) }
        UncertainMatchRepository.clear(MuTagApplication.instance)
        MatchingNotificationHelper.cancelAlert(MuTagApplication.instance)
    }

    /**
     * 当曲目在单曲编辑页面成功调整并保存后，移除其对应的存疑项并同步更新通知与本地持久化。
     *
     * @param filePath 已成功保存的音频文件绝对路径
     */
    fun onTrackManuallySaved(filePath: String) {
        _state.update { s ->
            val nextList = s.uncertainMatches.filter { it.localFile.filePath != filePath }
            UncertainMatchRepository.save(MuTagApplication.instance, nextList)
            s.copy(
                uncertainMatches = nextList,
                showUncertainSheet = nextList.isNotEmpty() && s.showUncertainSheet,
            )
        }
        MatchingNotificationHelper.updateAlertOrCancel(MuTagApplication.instance, _state.value.uncertainMatches.size)
        refresh()
    }

    /**
     * 针对单首曲目触发在线匹配。
     *
     * @param track 目标音频元数据
     */
    fun matchTrackOnline(track: AudioMetadata) {
        viewModelScope.launch(Dispatchers.IO) {
            val query = track.title.ifBlank { track.fileName.substringBeforeLast(".") }.replace("_", " ").replace("-", " ")
            val primaryRes = metadataProvider.search(query, page = 1, pageSize = 1).getOrNull()?.firstOrNull()
            val fallback = MuTagApplication.instance.getFallbackProvider()
            val (matched, provider) = if (primaryRes != null) {
                Pair(primaryRes, metadataProvider)
            } else if (fallback != null) {
                Pair(fallback.search(query, page = 1, pageSize = 1).getOrNull()?.firstOrNull(), fallback)
            } else {
                Pair(null, metadataProvider)
            }
            if (matched != null) {
                writeSingleTrackMatch(track, matched, provider)
            }
        }
    }

    /**
     * 针对单首曲目执行声学指纹识别匹配。
     *
     * @param track 目标音频元数据
     */
    fun matchTrackByAcoustic(track: AudioMetadata) {
        viewModelScope.launch(Dispatchers.IO) {
            val audioFile = File(track.filePath)
            val slice = org.mutag.core.tagger.recognizer.AudioSliceExtractor.extractSliceForSource(
                audioFile = audioFile,
                sourceId = metadataProvider.sourceId,
                preferredStartMs = track.chorusStartMs,
            ) ?: return@launch
            val primaryRes = metadataProvider.recognizeByAcoustic(slice.feature).getOrNull()
            val fallback = MuTagApplication.instance.getFallbackProvider()
            val (matched, provider) = if (primaryRes != null) {
                Pair(primaryRes, metadataProvider)
            } else if (fallback != null) {
                val fallbackSlice = org.mutag.core.tagger.recognizer.AudioSliceExtractor.extractSliceForSource(
                    audioFile = audioFile,
                    sourceId = fallback.sourceId,
                    preferredStartMs = track.chorusStartMs,
                )
                val fallbackRes = fallbackSlice?.let { fallback.recognizeByAcoustic(it.feature).getOrNull() }
                Pair(fallbackRes, fallback)
            } else {
                Pair(null, metadataProvider)
            }
            if (matched != null) {
                writeSingleTrackMatch(track, matched, provider)
            }
        }
    }

    /**
     * 针对指定文件夹下全部音频文件触发批量匹配。
     *
     * @param folderPath 目标文件夹绝对路径
     */
    fun matchFolderTags(folderPath: String) {
        val dir = File(folderPath)
        if (!dir.exists() || !dir.isDirectory) return
        navigateToDirectory(folderPath)
        startBatchMatching()
    }

    private suspend fun writeSingleTrackMatch(
        track: AudioMetadata,
        trackMeta: org.mutag.model.TrackMetadata,
        provider: MusicMetadataProvider = metadataProvider,
    ) {
        val file = File(track.filePath)
        if (!file.exists()) return

        val detail = provider.getTrackDetail(trackMeta.trackId).getOrDefault(trackMeta)
        var coverBytes: ByteArray? = null
        if (detail.coverUrl.isNotBlank() || detail.album.id.isNotBlank()) {
            coverBytes = CoverDownloadHelper.downloadCover(httpClient, detail)
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

        val success = AudioMetadataWriter.writeMetadata(file, payload)
        if (success) {
            val updated = AudioHeaderParser.parse(file)
            cache.put(updated)
            MediaScanHelper.scanFile(MuTagApplication.instance, file.absolutePath)
            withContext(Dispatchers.Main) {
                refresh()
            }
        }
    }

    /**
     * 关闭批量标签匹配候选确认弹窗。
     */
    fun dismissBatchDialog() {
        _state.update { it.copy(showBatchDialog = false) }
    }

    // 多选模式操作

    /**
     * 开启多选模式并选中指定文件。
     *
     * @param filePath 起始选中的音频文件绝对路径
     */
    fun startSelection(filePath: String) {
        _state.update { it.copy(isSelectionMode = true, selectedTrackPaths = setOf(filePath)) }
    }

    /**
     * 切换指定音频文件的多选选中状态。
     *
     * @param filePath 目标音频文件绝对路径
     */
    fun toggleTrackSelection(filePath: String) {
        _state.update { current ->
            val set = current.selectedTrackPaths.toMutableSet()
            if (set.contains(filePath)) {
                set.remove(filePath)
            } else {
                set.add(filePath)
            }
            current.copy(selectedTrackPaths = set, isSelectionMode = true)
        }
    }

    /**
     * 全选或取消全选指定列表内的全部曲目。
     *
     * @param allPaths 待操作的全部音频路径列表
     */
    fun selectAll(allPaths: List<String>) {
        _state.update { current ->
            val isAllSelected = allPaths.isNotEmpty() && allPaths.all { current.selectedTrackPaths.contains(it) }
            val newSet = if (isAllSelected) {
                current.selectedTrackPaths - allPaths.toSet()
            } else {
                current.selectedTrackPaths + allPaths
            }
            current.copy(selectedTrackPaths = newSet, isSelectionMode = true)
        }
    }

    /**
     * 反选当前列表中的曲目勾选状态。
     *
     * @param allPaths 当前列表的全部音频路径
     */
    fun invertSelection(allPaths: List<String>) {
        _state.update { current ->
            val set = current.selectedTrackPaths.toMutableSet()
            allPaths.forEach { path ->
                if (set.contains(path)) set.remove(path) else set.add(path)
            }
            current.copy(selectedTrackPaths = set, isSelectionMode = true)
        }
    }

    /**
     * 清空全部选中项并退出多选模式。
     */
    fun clearSelection() {
        _state.update { it.copy(selectedTrackPaths = emptySet(), isSelectionMode = false) }
    }

    // 批量匹配确认操作

    /**
     * 请求对已选中的曲目启动批量匹配，弹出确认对话框。
     */
    fun requestBatchMatchForSelected() {
        val selected = _state.value.selectedTrackPaths
        if (selected.isEmpty()) return
        val allTracks = (_state.value.tracks + _state.value.systemTracks + (_state.value.activeAggregateDetail?.tracks ?: emptyList())).distinctBy {
            it.filePath
        }
        val targets = allTracks.filter { selected.contains(it.filePath) }
        if (targets.isEmpty()) return
        _state.update { it.copy(showMatchConfirmDialog = true, pendingMatchTracks = targets) }
    }

    /**
     * 请求对指定曲目集合启动批量匹配。
     *
     * @param tracks 目标曲目集合
     */
    fun requestBatchMatchForAll(tracks: List<AudioMetadata>) {
        if (tracks.isEmpty()) return
        _state.update { it.copy(showMatchConfirmDialog = true, pendingMatchTracks = tracks) }
    }

    /**
     * 确认并开始批量匹配调度任务。
     */
    fun confirmStartBatchMatch() {
        val targets = _state.value.pendingMatchTracks
        _state.update { it.copy(showMatchConfirmDialog = false, pendingMatchTracks = emptyList()) }
        if (targets.isNotEmpty()) {
            startBatchMatching(targets)
        }
    }

    /**
     * 关闭批量匹配确认对话框。
     */
    fun dismissMatchConfirmDialog() {
        _state.update { it.copy(showMatchConfirmDialog = false, pendingMatchTracks = emptyList()) }
    }

    // 文件删除操作

    /**
     * 请求删除当前选中的音频文件，弹出二次确认弹窗。
     */
    fun requestDeleteSelected() {
        if (_state.value.selectedTrackPaths.isEmpty()) return
        _state.update { it.copy(showDeleteConfirmDialog = true) }
    }

    /**
     * 关闭删除确认对话框。
     */
    fun dismissDeleteConfirmDialog() {
        _state.update { it.copy(showDeleteConfirmDialog = false) }
    }

    /**
     * 确认物理删除已选中的音频文件并同步更新 MediaStore。
     *
     * @param context Android 上下文实例
     */
    fun confirmDeleteSelected(context: Context) {
        val selected = _state.value.selectedTrackPaths.toList()
        if (selected.isEmpty()) {
            _state.update { it.copy(showDeleteConfirmDialog = false) }
            return
        }

        _state.update { it.copy(showDeleteConfirmDialog = false, isDeleting = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val deletedPaths = mutableListOf<String>()
            selected.forEach { path ->
                val file = File(path)
                if (file.exists() && file.delete()) {
                    deletedPaths.add(path)
                }
            }

            if (deletedPaths.isNotEmpty()) {
                MediaScanHelper.scanFiles(context, deletedPaths)
            }

            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        isDeleting = false,
                        selectedTrackPaths = emptySet(),
                        isSelectionMode = false,
                    )
                }
                refresh()
                Toast.makeText(context, "已删除 ${deletedPaths.size} 首歌曲", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 聚合详情视图导航

    /**
     * 打开专辑或艺术家聚合详情视图。
     *
     * @param title 聚合项标题
     * @param subtitle 聚合项副标题
     * @param tracks 归属该聚合项的曲目列表
     */
    fun openAggregateDetail(title: String, subtitle: String, tracks: List<AudioMetadata>) {
        _state.update {
            it.copy(activeAggregateDetail = AggregateDetail(title = title, subtitle = subtitle, tracks = tracks))
        }
    }

    /**
     * 关闭聚合详情视图并返回主列表。
     */
    fun closeAggregateDetail() {
        _state.update { it.copy(activeAggregateDetail = null) }
    }

    /**
     * 打开批量重命名配置弹窗。
     *
     * @param tracks 待重命名的目标曲目列表
     */
    fun openRenameDialog(tracks: List<AudioMetadata>) {
        if (tracks.isEmpty()) return
        _state.update {
            it.copy(
                showRenameDialog = true,
                renameTargetTracks = tracks,
            )
        }
    }

    /**
     * 关闭批量重命名配置弹窗。
     */
    fun dismissRenameDialog() {
        _state.update {
            it.copy(
                showRenameDialog = false,
                renameTargetTracks = emptyList(),
            )
        }
    }

    /**
     * 执行选定曲目的批量重命名与系统媒体索引同步。
     *
     * @param pattern 重命名通配模式规则
     * @param renameAssociated 是否联动重命名关联附件
     * @param context Android 上下文实例
     */
    fun executeBatchRename(pattern: String, renameAssociated: Boolean, context: Context) {
        val targets = _state.value.renameTargetTracks
        if (targets.isEmpty()) {
            dismissRenameDialog()
            return
        }

        _state.update { it.copy(showRenameDialog = false, isRenaming = true) }

        viewModelScope.launch(Dispatchers.IO) {
            var renameFailed = false
            val renamedCount = try {
                BatchRenameExecutor.execute(
                    targets = targets,
                    pattern = pattern,
                    renameAssociated = renameAssociated,
                    context = context,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("MainViewModel", "Batch rename failed", e)
                renameFailed = true
                0
            }

            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        isRenaming = false,
                        renameTargetTracks = emptyList(),
                        selectedTrackPaths = emptySet(),
                        isSelectionMode = false,
                    )
                }
                refresh()
                val message = if (renameFailed) {
                    "批量重命名失败，请检查文件读写权限"
                } else {
                    "成功重命名 $renamedCount 个文件"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 每日静默检查一次远端应用更新。
     */
    fun checkDailyUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(2000L)
            val update = org.mutag.app.update.UpdateChecker.checkUpdate(
                context = MuTagApplication.instance,
                force = false,
            ).getOrNull()
            if (update != null) {
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(updateInfo = update) }
                }
            }
        }
    }

    /**
     * 手动检查更新并即时反馈结果。
     *
     * @param onFeedback 反馈回调消息
     */
    fun checkUpdateManually(onFeedback: (String) -> Unit) {
        if (_state.value.isCheckingUpdate) return
        _state.update { it.copy(isCheckingUpdate = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val res = org.mutag.app.update.UpdateChecker.checkUpdate(
                context = MuTagApplication.instance,
                force = true,
            )
            withContext(Dispatchers.Main) {
                _state.update { it.copy(isCheckingUpdate = false) }
                val info = res.getOrNull()
                if (info != null) {
                    _state.update { it.copy(updateInfo = info) }
                } else {
                    onFeedback("当前已是最新版本")
                }
            }
        }
    }

    /**
     * 关闭更新提示弹窗。
     */
    fun dismissUpdateDialog() {
        _state.update { it.copy(updateInfo = null) }
    }

    /**
     * 忽略当前发现的版本并不再主动提示。
     *
     * @param version 目标版本号
     */
    fun ignoreUpdateVersion(version: String) {
        org.mutag.app.update.UpdateChecker.ignoreVersion(MuTagApplication.instance, version)
        _state.update { it.copy(updateInfo = null) }
    }

    /**
     * 打开浏览器跳转至 APK 下载或发行页。
     *
     * @param url 下载直链或网页
     */
    fun downloadUpdate(url: String) {
        org.mutag.app.update.UpdateChecker.openDownloadUrl(MuTagApplication.instance, url)
        _state.update { it.copy(updateInfo = null) }
    }

    /**
     * 用于调试或 Intent 触发的指定更新弹窗显示。
     *
     * @param info 更新数据模型
     */
    fun showMockUpdate(info: org.mutag.app.update.AppUpdateInfo) {
        _state.update { it.copy(updateInfo = info) }
    }
}
