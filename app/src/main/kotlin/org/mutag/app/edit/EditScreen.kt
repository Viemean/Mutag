package org.mutag.app.edit

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import org.mutag.app.ui.cover.CoverDownloadHelper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mutag.app.ui.cover.CoverExportHelper

/**
 * 单曲音频元数据编辑界面根组件。
 *
 * @param viewModel 单曲编辑业务逻辑 ViewModel
 * @param onNavigateBack 返回上一级页面的路由回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(viewModel: EditViewModel, onNavigateBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    viewModel.replaceCover(bytes)
                } else {
                    viewModel.showUserNotice("未能读取到有效图片内容")
                }
            } catch (_: Exception) {
                viewModel.showUserNotice("读取所选图片失败，请检查文件访问权限")
            }
        }
    }

    LaunchedEffect(state.userNoticeMessage) {
        state.userNoticeMessage?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearUserNotice()
        }
    }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var showScrollToTop by remember { mutableStateOf(false) }

    LaunchedEffect(scrollState.isScrollInProgress, scrollState.value) {
        if (scrollState.value > 300) {
            if (scrollState.isScrollInProgress) {
                showScrollToTop = true
            } else if (showScrollToTop) {
                delay(3000L)
                showScrollToTop = false
            }
        } else {
            showScrollToTop = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "编辑歌曲标签",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            scrollState.animateScrollTo(0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "返回顶部",
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
        ) {
            // 8.1 头部与封面区域
            CoverHeader(
                state = state,
                onCoverClick = viewModel::openCoverActionSheet,
                onSearchOnline = { viewModel.searchOnline() },
                onSearchLyrics = viewModel::openLyricSearchDialog,
                onSave = { viewModel.saveChanges(onSuccess = onNavigateBack) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 卡片 1：曲目信息
                EssentialInfoCard(
                    state = state,
                    onTitleChange = viewModel::updateTitle,
                    onArtistChange = viewModel::updateArtist,
                    onAlbumChange = viewModel::updateAlbum,
                    onAlbumArtistChange = viewModel::updateAlbumArtist,
                )

                // 卡片 2：音轨与发布规范 (双列紧凑)
                TracksAndMetadataCard(
                    state = state,
                    onTrackNumChange = viewModel::updateTrackNumber,
                    onTrackTotChange = viewModel::updateTrackTotal,
                    onDiscNumChange = viewModel::updateDiscNumber,
                    onDiscTotChange = viewModel::updateDiscTotal,
                    onDateChange = viewModel::updatePublishDate,
                    onGenreChange = viewModel::updateGenre,
                    onLanguageChange = viewModel::updateLanguage,
                )

                // 卡片 3：创作者与发行归属
                CreditsAndPublishingCard(
                    state = state,
                    onLyricistChange = viewModel::updateLyricist,
                    onComposerChange = viewModel::updateComposer,
                    onCompanyChange = viewModel::updateCompany,
                    onCommentChange = viewModel::updateComment,
                )

                // 卡片 4：高级与专业元数据 (折叠/展开)
                AdvancedMetadataCard(
                    state = state,
                    onToggleExpand = viewModel::toggleAdvancedExpanded,
                    onArrangerChange = viewModel::updateArranger,
                    onProducerChange = viewModel::updateProducer,
                    onGainChange = viewModel::updateReplayGainTrackGain,
                    onPeakChange = viewModel::updateReplayGainTrackPeak,
                    onChorusStartChange = viewModel::updateChorusStartMs,
                    onCloudIdChange = viewModel::updateCloudTrackId,
                    onDescChange = viewModel::updateDescription,
                )

                // 卡片 5：内嵌同步歌词
                SyncedLyricsCard(
                    state = state,
                    onLyricsChange = viewModel::updateLyrics,
                    onSearchLyrics = viewModel::openLyricSearchDialog,
                )

                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (state.showLyricSearchDialog) {
        LyricSearchDialog(
            query = state.lyricSearchQuery,
            isSearching = state.isSearchingLyrics,
            candidates = state.lyricCandidates,
            showQq = state.showQqLyrics,
            showNetease = state.showNeteaseLyrics,
            hasFilteredPureMusic = state.hasFilteredPureMusic,
            onSearch = viewModel::searchLyrics,
            onToggleSource = viewModel::toggleLyricSourceFilter,
            onTogglePreview = viewModel::toggleLyricPreview,
            onApply = viewModel::applyLyricCandidate,
            onDismissRequest = viewModel::closeLyricSearchDialog,
        )
    }

    if (state.showMatchDialog) {
        OnlineMatchDialog(
            state = state,
            onSelectCandidate = viewModel::selectCandidate,
            onToggleField = viewModel::toggleDiffField,
            onSelectAllFields = viewModel::selectAllDiffFields,
            onInvertFields = viewModel::invertDiffFields,
            onClearFields = viewModel::clearDiffFields,
            onResetDefaultFields = viewModel::resetDefaultDiffFields,
            onQueryChange = viewModel::updateOnlineSearchQuery,
            onSearch = { viewModel.searchOnline(it) },
            onSourceChange = viewModel::setMatchSource,
            onAcousticRecognize = viewModel::recognizeByAcoustic,
            onApply = viewModel::applyCandidate,
            onDismiss = viewModel::dismissMatchDialog,
        )
    }

    if (state.showCoverActionSheet) {
        CoverActionBottomSheet(
            state = state,
            onDismiss = viewModel::dismissCoverActionSheet,
            onViewFullCover = viewModel::openFullCoverViewer,
            onSaveCover = viewModel::requestSaveCover,
            onPickCover = {
                viewModel.dismissCoverActionSheet()
                pickImageLauncher.launch("image/*")
            },
            onViewTechSpec = viewModel::openTechSpecDialog,
            onPlayAudio = {
                viewModel.dismissCoverActionSheet()
                AudioPlaybackHelper.playAudio(context, state.filePath)
            },
            onRemoveCover = viewModel::removeCover,
        )
    }

    val currentCover = state.coverBytes
    if (state.showFullCoverViewer && currentCover != null) {
        FullCoverViewerDialog(
            coverBytes = currentCover,
            onDismiss = viewModel::dismissFullCoverViewer,
            onLongPressSave = viewModel::requestSaveCover,
        )
    }

    if (state.showTechSpecDialog) {
        AudioTechSpecDialog(
            spec = state.techSpec,
            format = state.format,
            filePath = state.filePath,
            coverBytes = state.coverBytes,
            onCopyPath = {
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("FilePath", state.filePath))
                android.widget.Toast.makeText(context, "路径已复制", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDismiss = viewModel::dismissTechSpecDialog,
        )
    }

    if (state.showSaveCoverConfirm) {
        val previewName = CoverExportHelper.buildCoverFileName(state.title, state.album)
        AlertDialog(
            onDismissRequest = viewModel::dismissSaveCoverConfirm,
            title = { Text("保存封面", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "是否将当前封面保存至系统相册？\n保存目录：Pictures/MuTag\n文件名：$previewName",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSaveCover(context) }) {
                    Text("保存", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSaveCoverConfirm) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun CoverHeader(
    state: EditState,
    onCoverClick: () -> Unit,
    onSearchOnline: () -> Unit,
    onSearchLyrics: () -> Unit,
    onSave: () -> Unit,
) {
    val bitmap = remember(state.coverBytes) {
        state.coverBytes?.let {
            CoverDownloadHelper.decodeSampledBitmap(it, 1024)
        }
    }

    val cardShape = RoundedCornerShape(8.dp)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val shadowTint = Color(0xFF0D1016)

    val spotColor = if (isDark) {
        shadowTint.copy(alpha = 0.55f)
    } else {
        shadowTint.copy(alpha = 0.30f)
    }
    val ambientColor = if (isDark) {
        shadowTint.copy(alpha = 0.30f)
    } else {
        shadowTint.copy(alpha = 0.14f)
    }

    val borderModifier = if (isDark) {
        Modifier.border(
            width = 0.8.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.18f),
                    Color.White.copy(alpha = 0.05f),
                    Color.Transparent,
                ),
            ),
            shape = cardShape,
        )
    } else {
        Modifier.border(
            width = 0.8.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
            shape = cardShape,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            // 阴影层
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = if (isDark) 16.dp else 12.dp,
                        shape = cardShape,
                        spotColor = spotColor,
                        ambientColor = ambientColor,
                        clip = false,
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = cardShape,
                    ),
            )

            // 封面卡片容器
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(borderModifier)
                    .clip(cardShape)
                    .clickable { onCoverClick() },
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "封面",
                        contentScale = ContentScale.Crop,
                        alpha = if (state.isCoverDownloading) 0.65f else 1.0f,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }

                if (state.isCoverDownloading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                color = Color.White,
                                strokeWidth = 1.5.dp,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "下载封面中",
                                color = Color.White,
                                fontSize = 11.sp,
                            )
                        }
                    }
                } else if (state.coverDimensionsText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = state.coverDimensionsText,
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 操作胶囊
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = onSearchLyrics,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = "搜索歌词",
                    modifier = Modifier.size(20.dp),
                )
            }

            Button(
                onClick = onSearchOnline,
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(imageVector = Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("匹配信息", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                enabled = !state.isSaving,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("保存", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EssentialInfoCard(
    state: EditState,
    onTitleChange: (String) -> Unit,
    onArtistChange: (String) -> Unit,
    onAlbumChange: (String) -> Unit,
    onAlbumArtistChange: (String) -> Unit,
) {
    CardContainer(title = "歌曲信息") {
        CustomTextField(label = "标题", value = state.title, onValueChange = onTitleChange)
        Spacer(modifier = Modifier.height(10.dp))
        CustomTextField(label = "艺术家 (多人用 \" / \" 分隔)", value = state.artist, onValueChange = onArtistChange)
        Spacer(modifier = Modifier.height(10.dp))
        CustomTextField(label = "专辑名称", value = state.album, onValueChange = onAlbumChange)
        Spacer(modifier = Modifier.height(10.dp))
        CustomTextField(label = "专辑艺术家", value = state.albumArtist, onValueChange = onAlbumArtistChange)
    }
}

@Composable
private fun TracksAndMetadataCard(
    state: EditState,
    onTrackNumChange: (String) -> Unit,
    onTrackTotChange: (String) -> Unit,
    onDiscNumChange: (String) -> Unit,
    onDiscTotChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onGenreChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
) {
    CardContainer(title = "音轨信息") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CustomTextField(label = "音轨号", value = state.trackNumber, onValueChange = onTrackNumChange, modifier = Modifier.weight(1f))
            CustomTextField(label = "总音轨数", value = state.trackTotal, onValueChange = onTrackTotChange, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CustomTextField(label = "光盘号", value = state.discNumber, onValueChange = onDiscNumChange, modifier = Modifier.weight(1f))
            CustomTextField(label = "总光盘数", value = state.discTotal, onValueChange = onDiscTotChange, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CustomTextField(label = "发行日期", value = state.publishDate, onValueChange = onDateChange, modifier = Modifier.weight(1f))
            CustomTextField(label = "流派", value = state.genre, onValueChange = onGenreChange, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        CustomTextField(label = "语种", value = state.language, onValueChange = onLanguageChange)
    }
}

@Composable
private fun CreditsAndPublishingCard(
    state: EditState,
    onLyricistChange: (String) -> Unit,
    onComposerChange: (String) -> Unit,
    onCompanyChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
) {
    CardContainer(title = "创作信息") {
        CustomTextField(label = "作词", value = state.lyricist, onValueChange = onLyricistChange)
        Spacer(modifier = Modifier.height(10.dp))
        CustomTextField(label = "作曲", value = state.composer, onValueChange = onComposerChange)
        Spacer(modifier = Modifier.height(10.dp))
        CustomTextField(label = "唱片公司", value = state.company, onValueChange = onCompanyChange)
        Spacer(modifier = Modifier.height(10.dp))
        CustomTextField(label = "备注", value = state.comment, onValueChange = onCommentChange)
    }
}

@Composable
private fun AdvancedMetadataCard(
    state: EditState,
    onToggleExpand: () -> Unit,
    onArrangerChange: (String) -> Unit,
    onProducerChange: (String) -> Unit,
    onGainChange: (String) -> Unit,
    onPeakChange: (String) -> Unit,
    onChorusStartChange: (String) -> Unit,
    onCloudIdChange: (String) -> Unit,
    onDescChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleExpand() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "更多信息",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (state.isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (state.isAdvancedExpanded) "折叠" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(visible = state.isAdvancedExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    CustomTextField(label = "编曲", value = state.arranger, onValueChange = onArrangerChange)
                    Spacer(modifier = Modifier.height(10.dp))
                    CustomTextField(label = "制作人", value = state.producer, onValueChange = onProducerChange)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CustomTextField(
                            label = "回放增益 (dB)",
                            value = state.replayGainTrackGain,
                            onValueChange = onGainChange,
                            modifier = Modifier.weight(1f),
                        )
                        CustomTextField(
                            label = "峰值",
                            value = state.replayGainTrackPeak,
                            onValueChange = onPeakChange,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CustomTextField(
                            label = "副歌起点 (毫秒)",
                            value = state.chorusStartMs,
                            onValueChange = onChorusStartChange,
                            modifier = Modifier.weight(1f),
                        )
                        CustomTextField(
                            label = "云端ID",
                            value = state.cloudTrackId,
                            onValueChange = onCloudIdChange,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    CustomTextField(label = "专辑介绍", value = state.description, onValueChange = onDescChange, maxLines = 4)
                }
            }
        }
    }
}

@Composable
private fun SyncedLyricsCard(state: EditState, onLyricsChange: (String) -> Unit, onSearchLyrics: () -> Unit) {
    CardContainer(
        title = "内嵌歌词",
        headerAction = {
            FilledTonalIconButton(
                onClick = onSearchLyrics,
                shape = CircleShape,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "在线搜索歌词",
                    modifier = Modifier.size(16.dp),
                )
            }
        },
    ) {
        OutlinedTextField(
            value = state.lyrics,
            onValueChange = onLyricsChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 400.dp),
            placeholder = { Text("[00:00.00] 暂无歌词", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}

@Composable
private fun CardContainer(title: String, headerAction: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                headerAction?.invoke()
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun CustomTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        modifier = modifier.fillMaxWidth(),
        singleLine = maxLines == 1,
        maxLines = maxLines,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}
