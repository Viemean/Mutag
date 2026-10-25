package org.mutag.app.edit

import org.mutag.model.SyncedLyrics
import org.mutag.model.TrackMetadata

/**
 * 音频标签单曲编辑页 UI 状态容器。
 *
 * @param filePath 正在编辑的音频文件绝对路径
 */
data class EditState(
    val filePath: String = "",
    val fileName: String = "",
    val format: String = "",
    val specChip: String = "",
    val coverBytes: ByteArray? = null,
    val coverMime: String = "image/jpeg",
    val coverDimensionsText: String = "",

    // Card 1: 核心曲目信息
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",

    // Card 2: 音轨与发布规范
    val trackNumber: String = "",
    val trackTotal: String = "",
    val discNumber: String = "1",
    val discTotal: String = "1",
    val publishDate: String = "",
    val genre: String = "",
    val language: String = "",

    // Card 3: 创作者与发行归属
    val lyricist: String = "",
    val composer: String = "",
    val company: String = "",
    val comment: String = "",

    // Card 4: 高级与专业元数据
    val isAdvancedExpanded: Boolean = false,
    val arranger: String = "",
    val producer: String = "",
    val replayGainTrackGain: String = "",
    val replayGainTrackPeak: String = "",
    val chorusStartMs: String = "",
    val cloudTrackId: String = "",
    val cloudAlbumMid: String = "",
    val description: String = "",

    // Card 5: 同步歌词
    val lyrics: String = "",
    val syncedLyrics: SyncedLyrics? = null,

    // Dialogs & Progress
    val isSearchingOnline: Boolean = false,
    val searchCandidates: List<TrackMetadata> = emptyList(),
    val showMatchDialog: Boolean = false,
    val matchSource: org.mutag.app.settings.MetadataSource = org.mutag.app.settings.MetadataSource.QQ_MUSIC,
    val selectedCandidate: TrackMetadata? = null,
    val candidateDiffFields: Map<String, Boolean> = emptyMap(), // fieldKey -> isSelected
    val onlineSearchQuery: String = "",
    val quickSearchChips: List<Pair<String, String>> = emptyList(),
    val durationMs: Long = 0L,

    val isSaving: Boolean = false,
    val saveSuccess: Boolean? = null,
    val errorMessage: String? = null,
    val showCoverActionSheet: Boolean = false,
    val showFullCoverViewer: Boolean = false,
    val showTechSpecDialog: Boolean = false,
    val showSaveCoverConfirm: Boolean = false,
    val techSpec: org.mutag.core.tagger.scanner.AudioTechSpec? = null,
    val userNoticeMessage: String? = null,
    val isCoverDownloading: Boolean = false,

    // Lyric Search Dialog State
    val showLyricSearchDialog: Boolean = false,
    val isSearchingLyrics: Boolean = false,
    val lyricSearchQuery: String = "",
    val lyricCandidates: List<LyricCandidate> = emptyList(),
    val showQqLyrics: Boolean = true,
    val showNeteaseLyrics: Boolean = true,
    val hasFilteredPureMusic: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EditState) return false

        return coverBytes.contentEquals(other.coverBytes) &&
            filePath == other.filePath &&
            fileName == other.fileName &&
            format == other.format &&
            specChip == other.specChip &&
            coverMime == other.coverMime &&
            coverDimensionsText == other.coverDimensionsText &&
            title == other.title &&
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
            company == other.company &&
            comment == other.comment &&
            isAdvancedExpanded == other.isAdvancedExpanded &&
            arranger == other.arranger &&
            producer == other.producer &&
            replayGainTrackGain == other.replayGainTrackGain &&
            replayGainTrackPeak == other.replayGainTrackPeak &&
            cloudTrackId == other.cloudTrackId &&
            cloudAlbumMid == other.cloudAlbumMid &&
            description == other.description &&
            lyrics == other.lyrics &&
            syncedLyrics == other.syncedLyrics &&
            isSearchingOnline == other.isSearchingOnline &&
            searchCandidates == other.searchCandidates &&
            showMatchDialog == other.showMatchDialog &&
            matchSource == other.matchSource &&
            selectedCandidate == other.selectedCandidate &&
            candidateDiffFields == other.candidateDiffFields &&
            onlineSearchQuery == other.onlineSearchQuery &&
            quickSearchChips == other.quickSearchChips &&
            durationMs == other.durationMs &&
            isSaving == other.isSaving &&
            saveSuccess == other.saveSuccess &&
            errorMessage == other.errorMessage &&
            showCoverActionSheet == other.showCoverActionSheet &&
            showFullCoverViewer == other.showFullCoverViewer &&
            showTechSpecDialog == other.showTechSpecDialog &&
            showSaveCoverConfirm == other.showSaveCoverConfirm &&
            techSpec == other.techSpec &&
            userNoticeMessage == other.userNoticeMessage &&
            isCoverDownloading == other.isCoverDownloading &&
            language == other.language &&
            chorusStartMs == other.chorusStartMs &&
            showLyricSearchDialog == other.showLyricSearchDialog &&
            isSearchingLyrics == other.isSearchingLyrics &&
            lyricSearchQuery == other.lyricSearchQuery &&
            lyricCandidates == other.lyricCandidates &&
            showQqLyrics == other.showQqLyrics &&
            showNeteaseLyrics == other.showNeteaseLyrics &&
            hasFilteredPureMusic == other.hasFilteredPureMusic
    }

    override fun hashCode(): Int {
        var result = filePath.hashCode()
        result = 31 * result + (coverBytes?.contentHashCode() ?: 0)
        result = 31 * result + title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + isAdvancedExpanded.hashCode()
        result = 31 * result + showMatchDialog.hashCode()
        result = 31 * result + matchSource.hashCode()
        result = 31 * result + candidateDiffFields.hashCode()
        result = 31 * result + onlineSearchQuery.hashCode()
        result = 31 * result + showCoverActionSheet.hashCode()
        result = 31 * result + showFullCoverViewer.hashCode()
        result = 31 * result + showTechSpecDialog.hashCode()
        result = 31 * result + showSaveCoverConfirm.hashCode()
        result = 31 * result + showLyricSearchDialog.hashCode()
        result = 31 * result + isSearchingLyrics.hashCode()
        result = 31 * result + lyricSearchQuery.hashCode()
        result = 31 * result + lyricCandidates.hashCode()
        result = 31 * result + showQqLyrics.hashCode()
        result = 31 * result + showNeteaseLyrics.hashCode()
        result = 31 * result + hasFilteredPureMusic.hashCode()
        return result
    }
}

val CANDIDATE_METADATA_FIELDS = listOf(
    "title" to "歌曲标题",
    "artist" to "艺术家",
    "album" to "专辑名称",
    "albumArtist" to "专辑艺术家",
    "cover" to "专辑封面",
    "lyrics" to "歌词",
    "date" to "发行日期",
    "trackNumber" to "音轨号",
    "trackTotal" to "总音轨数",
    "discNumber" to "光盘号",
    "discTotal" to "总光盘数",
    "genre" to "流派",
    "lyricist" to "作词",
    "composer" to "作曲",
    "arranger" to "编曲",
    "producer" to "制作人",
    "company" to "唱片公司",
    "comment" to "备注",
    "language" to "语种",
    "chorusStartMs" to "副歌起点",
    "description" to "专辑介绍",
    "cloudTrackId" to "云端ID",
)
