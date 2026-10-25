package org.mutag.app.main

import org.mutag.core.tagger.scanner.AudioMetadata
import org.mutag.core.tagger.scanner.FolderItem
import org.mutag.model.TrackMetadata

/**
 * 主界面顶部视图分类 Tab 枚举。
 *
 * @param title Tab 显示标题文本
 */
enum class MainTab(val title: String) {
    FILES("文件目录"),
    LIBRARY("曲库"),
    ALBUMS("专辑"),
    ARTISTS("艺术家"),
}

/**
 * 文件列表排序规则枚举。
 *
 * @param title 排序模式显示文本
 */
enum class SortMode(val title: String) {
    NAME("按文件名 (A - Z)"),
    DATE("按修改日期 (最新优先)"),
    SIZE("按文件大小 (从大到小)"),
}

/**
 * 目录导航面包屑项数据结构。
 *
 * @param title 节点显示标题
 * @param path 目标目录绝对路径
 */
data class BreadcrumbItem(val title: String, val path: String)

/**
 * 批量匹配候选比对项数据结构。
 *
 * @param localFile 本地音频文件元数据
 * @param matchedTrack 在线匹配获得的云端曲目元数据
 * @param isAcousticRecognized 是否经由声学指纹识别获取
 * @param isSelected 勾选写入状态
 */
data class BatchMatchCandidate(
    val localFile: AudioMetadata,
    val matchedTrack: TrackMetadata?,
    val isAcousticRecognized: Boolean = false,
    var isSelected: Boolean = true,
)

/**
 * 专辑与艺术家聚合详情视图数据实体。
 *
 * @param title 详情页主标题文本
 * @param subtitle 详情页副标题文本
 * @param tracks 该聚合项包含的音频曲目列表
 */
data class AggregateDetail(val title: String, val subtitle: String, val tracks: List<AudioMetadata>)

/**
 * 主界面全局聚合 UI 状态容器。
 *
 * @param currentTab 当前激活的主视图分类 Tab
 */
data class MainState(
    val currentTab: MainTab = MainTab.FILES,
    val initialPath: String = "",
    val currentPath: String = "",
    val breadcrumbs: List<BreadcrumbItem> = emptyList(),
    val searchQuery: String = "",
    val sortMode: SortMode = SortMode.NAME,
    val showSortBottomSheet: Boolean = false,
    val showFileNameInFilesTab: Boolean = false,

    val isLoading: Boolean = false,
    val isSystemScanning: Boolean = false,
    val folders: List<FolderItem> = emptyList(),
    val tracks: List<AudioMetadata> = emptyList(),
    val systemTracks: List<AudioMetadata> = emptyList(),

    // Aggregations
    val albums: Map<String, List<AudioMetadata>> = emptyMap(),
    val artists: Map<String, List<AudioMetadata>> = emptyMap(),
    val activeAggregateDetail: AggregateDetail? = null,

    // Selection mode
    val isSelectionMode: Boolean = false,
    val selectedTrackPaths: Set<String> = emptySet(),
    val showDeleteConfirmDialog: Boolean = false,
    val isDeleting: Boolean = false,

    // Batch rename dialog
    val showRenameDialog: Boolean = false,
    val renameTargetTracks: List<AudioMetadata> = emptyList(),
    val isRenaming: Boolean = false,

    // Batch match dialog
    val isBatchMatching: Boolean = false,
    val showBatchDialog: Boolean = false,
    val showBatchConfirmDialog: Boolean = false,
    val showMatchConfirmDialog: Boolean = false,
    val pendingMatchTracks: List<AudioMetadata> = emptyList(),
    val batchCandidates: List<BatchMatchCandidate> = emptyList(),
    val batchSelectedFields: Map<String, Boolean> = mapOf(
        "title" to true,
        "artist" to true,
        "album" to true,
        "cover" to true,
        "lyrics" to true,
        "date" to true,
        "trackNum" to true,
    ),
    val isWritingBatch: Boolean = false,
    val batchProgress: Float = 0f,
    val batchSuccessCount: Int = 0,
    val batchTotalCount: Int = 0,
    val uncertainMatches: List<UncertainMatchItem> = emptyList(),
    val showUncertainSheet: Boolean = false,
    val directoryHistory: List<DirectorySnapshot> = emptyList(),
    val pendingScrollRestore: Pair<Int, Int>? = null,
    val updateInfo: org.mutag.app.update.AppUpdateInfo? = null,
    val isCheckingUpdate: Boolean = false,
)

/**
 * 目录导航历史快照数据实体。
 *
 * @param path 目录绝对路径
 * @param folders 子文件夹列表
 * @param tracks 音频文件列表
 * @param scrollIndex 滚动行索引位置
 * @param scrollOffset 滚动像素偏移量
 */
data class DirectorySnapshot(
    val path: String,
    val folders: List<FolderItem>,
    val tracks: List<AudioMetadata>,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)

/**
 * 低置信度存疑匹配项数据实体。
 *
 * @param localFile 本地音频文件元数据
 * @param candidate 在线候选匹配元数据
 * @param reason 判定为存疑的具体原因描述
 */
data class UncertainMatchItem(val localFile: AudioMetadata, val candidate: TrackMetadata?, val reason: String)
