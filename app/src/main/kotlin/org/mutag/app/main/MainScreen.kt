package org.mutag.app.main

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mutag.app.ui.cover.EmbeddedAudioCover
import org.mutag.core.tagger.scanner.AudioMetadata
import org.mutag.core.tagger.scanner.FolderItem

/**
 * MuTag 主界面根视图组件，承载目录浏览、曲库分类导航与批量处理交互。
 *
 * @param viewModel 主界面状态与业务逻辑 ViewModel
 * @param onNavigateToEdit 导航至单曲标签编辑页面的路由回调
 * @param onNavigateToSettings 导航至应用设置界面的路由回调
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onNavigateToEdit: (String) -> Unit, onNavigateToSettings: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    BackHandler {
        when {
            state.isSelectionMode -> viewModel.clearSelection()

            state.activeAggregateDetail != null -> viewModel.closeAggregateDetail()

            viewModel.canNavigateUp() -> viewModel.navigateUp()

            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime <= 2000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(context, "再次返回退出应用", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val filteredFolders = remember(state.folders, state.searchQuery) {
        if (state.searchQuery.isBlank()) {
            state.folders
        } else {
            state.folders.filter { it.name.contains(state.searchQuery, ignoreCase = true) }
        }
    }
    val filteredTracks = remember(state.tracks, state.searchQuery) {
        if (state.searchQuery.isBlank()) {
            state.tracks
        } else {
            state.tracks.filter {
                it.title.contains(state.searchQuery, ignoreCase = true) ||
                    it.artist.contains(state.searchQuery, ignoreCase = true) ||
                    it.fileName.contains(state.searchQuery, ignoreCase = true)
            }
        }
    }
    val filteredSystemTracks = remember(state.systemTracks, state.searchQuery) {
        if (state.searchQuery.isBlank()) {
            state.systemTracks
        } else {
            state.systemTracks.filter {
                it.title.contains(state.searchQuery, ignoreCase = true) ||
                    it.artist.contains(state.searchQuery, ignoreCase = true) ||
                    it.fileName.contains(state.searchQuery, ignoreCase = true)
            }
        }
    }
    val filteredAlbums = remember(state.albums, state.searchQuery) {
        if (state.searchQuery.isBlank()) {
            state.albums
        } else {
            state.albums.filter { (name, tracks) ->
                name.contains(state.searchQuery, ignoreCase = true) ||
                    tracks.any {
                        it.title.contains(state.searchQuery, ignoreCase = true) ||
                            it.artist.contains(state.searchQuery, ignoreCase = true)
                    }
            }
        }
    }
    val filteredArtists = remember(state.artists, state.searchQuery) {
        if (state.searchQuery.isBlank()) {
            state.artists
        } else {
            state.artists.filter { (name, tracks) ->
                name.contains(state.searchQuery, ignoreCase = true) ||
                    tracks.any { it.title.contains(state.searchQuery, ignoreCase = true) }
            }
        }
    }

    val currentTabTracks: List<AudioMetadata> = remember(
        state.activeAggregateDetail,
        state.currentTab,
        filteredTracks,
        filteredSystemTracks,
        filteredAlbums,
        filteredArtists,
    ) {
        val detail = state.activeAggregateDetail
        when {
            detail != null -> detail.tracks
            state.currentTab == MainTab.FILES -> filteredTracks
            state.currentTab == MainTab.LIBRARY -> filteredSystemTracks
            state.currentTab == MainTab.ALBUMS -> filteredAlbums.values.flatten()
            state.currentTab == MainTab.ARTISTS -> filteredArtists.values.flatten()
            else -> emptyList()
        }
    }

    val pagerState = rememberPagerState(initialPage = state.currentTab.ordinal) { MainTab.entries.size }
    val scope = rememberCoroutineScope()

    val filesListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val albumsListState = rememberLazyListState()
    val artistsListState = rememberLazyListState()

    val currentListState = when (state.currentTab) {
        MainTab.FILES -> filesListState
        MainTab.LIBRARY -> libraryListState
        MainTab.ALBUMS -> albumsListState
        MainTab.ARTISTS -> artistsListState
    }
    val showScrollToTop by remember {
        derivedStateOf {
            currentListState.firstVisibleItemIndex > 2 || currentListState.firstVisibleItemScrollOffset > 300
        }
    }

    LaunchedEffect(state.pendingScrollRestore) {
        val restore = state.pendingScrollRestore
        if (restore != null) {
            filesListState.scrollToItem(restore.first, restore.second)
            viewModel.consumePendingScrollRestore()
        }
    }

    // 左右滑动手势切页与 Tab 联动
    LaunchedEffect(pagerState.currentPage) {
        val tab = MainTab.entries[pagerState.currentPage]
        if (state.currentTab != tab) {
            viewModel.setTab(tab)
        }
    }

    LaunchedEffect(state.currentTab) {
        if (pagerState.currentPage != state.currentTab.ordinal) {
            pagerState.animateScrollToPage(state.currentTab.ordinal)
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { _ -> }

        LaunchedEffect(Unit) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                MainTopAppBar(
                    uncertainCount = state.uncertainMatches.size,
                    onUncertainClick = viewModel::openUncertainSheet,
                    onSettingsClick = onNavigateToSettings,
                )
                AnimatedVisibility(
                    visible = state.isWritingBatch,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 2.dp,
                        border = BorderStroke(
                            width = 0.8.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "正在匹配元数据",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "匹配成功: ${state.batchSuccessCount} / 总队列: ${state.batchTotalCount}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { state.batchProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            val activeDetail = state.activeAggregateDetail
            var lastNonNullDetail by remember { mutableStateOf<AggregateDetail?>(null) }
            if (activeDetail != null) {
                lastNonNullDetail = activeDetail
            }

            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // 3. 顶级分类导航栏 (4 个 Tab)
                PrimaryTabBar(
                    selectedTab = MainTab.entries[pagerState.currentPage],
                    onTabSelect = { tab ->
                        scope.launch {
                            pagerState.animateScrollToPage(tab.ordinal)
                        }
                    },
                )

                // 支持左右滑动切换视图
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (MainTab.entries[page]) {
                        MainTab.FILES -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // 路径导航
                                BreadcrumbBar(
                                    breadcrumbs = state.breadcrumbs,
                                    onBreadcrumbClick = { path ->
                                        viewModel.navigateToBreadcrumb(
                                            targetPath = path,
                                            currentScrollIndex = filesListState.firstVisibleItemIndex,
                                            currentScrollOffset = filesListState.firstVisibleItemScrollOffset,
                                        )
                                    },
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "当前包含 ${filteredFolders.size + filteredTracks.size} 项",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ViewModeButton(
                                            showFileName = state.showFileNameInFilesTab,
                                            onClick = viewModel::toggleFileNameView,
                                        )
                                        SortButton(onClick = viewModel::openSortBottomSheet)
                                    }
                                }

                                PullToRefreshBox(
                                    isRefreshing = state.isLoading,
                                    onRefresh = viewModel::refresh,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    LazyColumn(
                                        state = filesListState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 88.dp),
                                    ) {
                                        items(filteredFolders, key = { it.path }) { folder ->
                                            FolderItemRow(
                                                folder = folder,
                                                onClick = {
                                                    focusManager.clearFocus()
                                                    viewModel.navigateToDirectory(
                                                        path = folder.path,
                                                        currentScrollIndex = filesListState.firstVisibleItemIndex,
                                                        currentScrollOffset = filesListState.firstVisibleItemScrollOffset,
                                                    )
                                                },
                                                modifier = Modifier.animateItem(),
                                            )
                                        }
                                        items(filteredTracks, key = { it.filePath }) { track ->
                                            TrackItemRow(
                                                track = track,
                                                isSelectionMode = state.isSelectionMode,
                                                isSelected = state.selectedTrackPaths.contains(track.filePath),
                                                onClick = {
                                                    focusManager.clearFocus()
                                                    onNavigateToEdit(track.filePath)
                                                },
                                                onToggleSelect = {
                                                    viewModel.toggleTrackSelection(track.filePath)
                                                },
                                                onLongClick = {
                                                    viewModel.toggleTrackSelection(track.filePath)
                                                },
                                                showFileName = state.showFileNameInFilesTab,
                                                modifier = Modifier.animateItem(),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        MainTab.LIBRARY -> {
                            // 歌曲库：显示系统自动扫描的音乐（自动排除录音）
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "共 ${filteredSystemTracks.size} 首歌曲",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    SortButton(onClick = viewModel::openSortBottomSheet)
                                }

                                PullToRefreshBox(
                                    isRefreshing = state.isSystemScanning,
                                    onRefresh = { viewModel.scanSystemAudioLibrary(rescanFiles = true) },
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    LazyColumn(
                                        state = libraryListState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 88.dp),
                                    ) {
                                        items(filteredSystemTracks, key = { it.filePath }) { track ->
                                            TrackItemRow(
                                                track = track,
                                                isSelectionMode = state.isSelectionMode,
                                                isSelected = state.selectedTrackPaths.contains(track.filePath),
                                                onClick = {
                                                    focusManager.clearFocus()
                                                    onNavigateToEdit(track.filePath)
                                                },
                                                onToggleSelect = {
                                                    viewModel.toggleTrackSelection(track.filePath)
                                                },
                                                onLongClick = {
                                                    viewModel.toggleTrackSelection(track.filePath)
                                                },
                                                modifier = Modifier.animateItem(),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        MainTab.ALBUMS -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "共 ${filteredAlbums.size} 张专辑",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    SortButton(onClick = viewModel::openSortBottomSheet)
                                }
                                LazyColumn(
                                    state = albumsListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 88.dp),
                                ) {
                                    filteredAlbums.forEach { (albumName, tracks) ->
                                        item(key = "album_$albumName") {
                                            val coverTrack = tracks.firstOrNull { it.hasCover } ?: tracks.firstOrNull()
                                            AggregateGroupRow(
                                                title = albumName,
                                                subtitle = "${tracks.size} 首歌曲 · ${tracks.firstOrNull()?.artist ?: "未知歌手"}",
                                                icon = Icons.Rounded.Album,
                                                coverTrack = coverTrack,
                                                onClick = {
                                                    focusManager.clearFocus()
                                                    if (tracks.size == 1) {
                                                        tracks.firstOrNull()?.let { onNavigateToEdit(it.filePath) }
                                                    } else {
                                                        viewModel.openAggregateDetail(albumName, "${tracks.size} 首歌曲", tracks)
                                                    }
                                                },
                                                modifier = Modifier.animateItem(),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        MainTab.ARTISTS -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "共 ${filteredArtists.size} 位艺术家",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    SortButton(onClick = viewModel::openSortBottomSheet)
                                }
                                LazyColumn(
                                    state = artistsListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 88.dp),
                                ) {
                                    filteredArtists.forEach { (artistName, tracks) ->
                                        item(key = "artist_$artistName") {
                                            AggregateGroupRow(
                                                title = artistName,
                                                subtitle = "${tracks.size} 首歌曲",
                                                icon = Icons.Rounded.Person,
                                                onClick = {
                                                    focusManager.clearFocus()
                                                    if (tracks.size == 1) {
                                                        tracks.firstOrNull()?.let { onNavigateToEdit(it.filePath) }
                                                    } else {
                                                        viewModel.openAggregateDetail(artistName, "${tracks.size} 首歌曲", tracks)
                                                    }
                                                },
                                                modifier = Modifier.animateItem(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = activeDetail != null,
                enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                ),
            ) {
                val detailToRender = activeDetail ?: lastNonNullDetail
                if (detailToRender != null) {
                    AggregateDetailView(
                        detail = detailToRender,
                        isSelectionMode = state.isSelectionMode,
                        selectedTrackPaths = state.selectedTrackPaths,
                        searchQuery = state.searchQuery,
                        onBack = viewModel::closeAggregateDetail,
                        onNavigateToEdit = {
                            focusManager.clearFocus()
                            onNavigateToEdit(it)
                        },
                        onToggleSelect = viewModel::toggleTrackSelection,
                    )
                }
            }

            // 多选操作悬浮栏
            AnimatedVisibility(
                visible = state.isSelectionMode,
                enter = fadeIn(tween(200)) + slideInHorizontally(tween(250)) { it / 2 },
                exit = fadeOut(tween(200)) + slideOutHorizontally(tween(250)) { it / 2 },
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                val isAllSelected = currentTabTracks.isNotEmpty() && currentTabTracks.all { state.selectedTrackPaths.contains(it.filePath) }
                SelectionActionColumn(
                    isAllSelected = isAllSelected,
                    hasSelection = state.selectedTrackPaths.isNotEmpty(),
                    onSelectAllOrInvert = {
                        val allPaths = currentTabTracks.map { it.filePath }
                        viewModel.selectAll(allPaths)
                    },
                    onRename = {
                        val selected = currentTabTracks.filter { state.selectedTrackPaths.contains(it.filePath) }
                        viewModel.openRenameDialog(selected)
                    },
                    onMatch = viewModel::requestBatchMatchForSelected,
                    onDelete = viewModel::requestDeleteSelected,
                    onClose = viewModel::clearSelection,
                )
            }

            // 常规态搜索栏与匹配按钮
            AnimatedVisibility(
                visible = !state.isSelectionMode,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                val isImeVisible = WindowInsets.isImeVisible
                val bottomPadding = if (isImeVisible) 16.dp else 88.dp
                Column(
                    modifier = Modifier
                        .imePadding()
                        .padding(end = 16.dp, bottom = bottomPadding),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AnimatedVisibility(
                        visible = showScrollToTop,
                        enter = fadeIn(tween(150)) + scaleIn(tween(150)),
                        exit = fadeOut(tween(150)) + scaleOut(tween(150)),
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    currentListState.animateScrollToItem(0)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "返回顶部",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    FloatingSearchPill(
                        query = state.searchQuery,
                        onQueryChange = viewModel::setSearchQuery,
                    )
                    SmallFloatingActionButton(
                        onClick = {
                            if (currentTabTracks.isEmpty()) {
                                Toast.makeText(context, "当前列表暂无音频文件", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.requestBatchMatchForAll(currentTabTracks)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = "匹配当前视图所有歌曲",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            if (currentTabTracks.isEmpty()) {
                                Toast.makeText(context, "当前列表暂无音频文件", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.openRenameDialog(currentTabTracks)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DriveFileRenameOutline,
                            contentDescription = "重命名文件",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }

    // 排序 BottomSheet
    if (state.showSortBottomSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSortBottomSheet,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "列表排序方式",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(12.dp))
                SortMode.entries.forEach { mode ->
                    val selected = state.sortMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.setSortMode(mode) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = mode.title,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // 存疑曲目待核对 BottomSheet
    if (state.showUncertainSheet && state.uncertainMatches.isNotEmpty()) {
        UncertainReviewBottomSheet(
            items = state.uncertainMatches,
            onApply = viewModel::applyUncertainMatch,
            onApplyAll = viewModel::applyAllUncertainMatches,
            onDismissItem = viewModel::dismissUncertainItem,
            onClearAll = viewModel::clearAllUncertainMatches,
            onDismissRequest = viewModel::dismissUncertainSheet,
            onNavigateToEdit = onNavigateToEdit,
        )
    }

    state.updateInfo?.let { updateInfo ->
        org.mutag.app.update.UpdateDialog(
            info = updateInfo,
            onDownload = { viewModel.downloadUpdate(updateInfo.downloadUrl) },
            onIgnore = { viewModel.ignoreUpdateVersion(updateInfo.versionName) },
            onDismiss = viewModel::dismissUpdateDialog,
        )
    }

    // 批量匹配开始前二次确认 Dialog
    if (state.showMatchConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMatchConfirmDialog,
            title = {
                Text(
                    text = "批量匹配确认",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Text(
                    text = "即将为选中的 ${state.pendingMatchTracks.size} 首歌曲在网络中检索并匹配元数据标签，是否继续？",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmStartBatchMatch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text("开始匹配")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMatchConfirmDialog) {
                    Text("取消")
                }
            },
        )
    }

    // 物理删除前二次确认 Dialog
    if (state.showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirmDialog,
            title = {
                Text(
                    text = "彻底删除确认",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            },
            text = {
                Text(
                    text = "确定要从设备中物理删除选中的 ${state.selectedTrackPaths.size} 个音频文件吗？\n\n此操作不可逆，文件将被永久删除！",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteSelected(context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("彻底删除")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirmDialog) {
                    Text("取消")
                }
            },
        )
    }

    // 批量重命名 Dialog
    if (state.showRenameDialog) {
        BatchRenameDialog(
            targetTracks = state.renameTargetTracks,
            onDismiss = viewModel::dismissRenameDialog,
            onConfirm = { pattern, renameAssociated ->
                viewModel.executeBatchRename(pattern, renameAssociated, context)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopAppBar(uncertainCount: Int, onUncertainClick: () -> Unit, onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        title = {
            Text(
                text = "MuTag",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            IconButton(onClick = onUncertainClick) {
                BadgedBox(
                    badge = {
                        if (uncertainCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ) {
                                Text(if (uncertainCount > 99) "99+" else uncertainCount.toString())
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = "待核对曲目",
                        tint = if (uncertainCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        modifier = modifier,
    )
}

@Composable
private fun FloatingSearchPill(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var isExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 展开时拦截系统返回键：收起搜索框而不是退出应用
    BackHandler(enabled = isExpanded) {
        isExpanded = false
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            delay(120L)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    AnimatedContent(
        targetState = isExpanded,
        transitionSpec = {
            (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f))
                .togetherWith(fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f))
        },
        contentAlignment = Alignment.CenterEnd,
        modifier = modifier,
        label = "SearchPillTransition",
    ) { expanded ->
        if (expanded) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shadowElevation = 4.dp,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .height(40.dp)
                    .widthIn(min = 180.dp, max = 240.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            },
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = "过滤曲目...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                    )
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "清空过滤",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            isExpanded = false
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "收起过滤",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        } else {
            SmallFloatingActionButton(
                onClick = { isExpanded = true },
                containerColor = if (query.isNotEmpty()) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (query.isNotEmpty()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 6.dp,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "搜索",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun PrimaryTabBar(selectedTab: MainTab, onTabSelect: (MainTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MainTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            FilterChip(
                selected = selected,
                onClick = { onTabSelect(tab) },
                label = {
                    Text(
                        text = tab.title,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                ),
            )
        }
    }
}

@Composable
private fun BreadcrumbBar(breadcrumbs: List<BreadcrumbItem>, onBreadcrumbClick: (String) -> Unit) {
    val scrollState = rememberScrollState()
    LaunchedEffect(breadcrumbs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                breadcrumbs.forEachIndexed { index, item ->
                    val isLast = index == breadcrumbs.lastIndex
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = !isLast) { onBreadcrumbClick(item.path) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                    if (!isLast) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier
                                .padding(horizontal = 1.dp)
                                .size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Sort,
            contentDescription = "排序",
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ViewModeButton(showFileName: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (showFileName) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (showFileName) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Icon(
            imageVector = if (showFileName) Icons.Rounded.MusicNote else Icons.Rounded.Description,
            contentDescription = if (showFileName) "切换为歌曲标题视图" else "切换为文件名视图",
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun FolderItemRow(folder: FolderItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧图标区 (42x42dp, 圆角 8dp，对齐 Melo)
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${folder.subFolderCount} 个文件夹 · ${folder.audioFileCount} 首文件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TrackItemRow(
    track: AudioMetadata,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onLongClick: () -> Unit,
    showFileName: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelect() else onClick()
                },
                onLongClick = {
                    if (isSelectionMode) onToggleSelect() else onLongClick()
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧封面图 (44x44dp, 圆角 8dp)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (track.hasCover) {
                AsyncImage(
                    model = EmbeddedAudioCover(File(track.filePath)),
                    contentDescription = "封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp),
                )
            }

            // 多选指示层
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            } else {
                                Color.Black.copy(alpha = 0.25f)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "已选",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            val titleText = if (showFileName) track.fileName else track.title.ifBlank { track.fileName }
            Text(
                text = titleText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            val subtitle = if (showFileName) {
                if (track.title.isNotBlank()) {
                    if (track.artist.isNotBlank()) "${track.title} · ${track.artist}" else track.title
                } else if (track.artist.isNotBlank()) {
                    track.artist
                } else {
                    track.format
                }
            } else {
                if (track.artist.isNotBlank() && track.album.isNotBlank()) {
                    "${track.artist} · ${track.album}"
                } else if (track.artist.isNotBlank()) {
                    track.artist
                } else {
                    track.format
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AggregateGroupRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverTrack: AudioMetadata? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (coverTrack?.hasCover == true) {
                AsyncImage(
                    model = EmbeddedAudioCover(File(coverTrack.filePath)),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
