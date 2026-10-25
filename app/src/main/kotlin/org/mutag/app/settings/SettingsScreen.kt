package org.mutag.app.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.launch
import org.mutag.app.ui.theme.DynamicColorSchemeGenerator

/**
 * 应用程序设置界面根组件。
 *
 * @param viewModel 应用设置状态 ViewModel
 * @param onNavigateBack 返回上一级页面的路由回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onNavigateBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<org.mutag.app.update.AppUpdateInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
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
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Section 1: 外观与显示
            Column {
                SectionHeader(title = "外观与显示")
                SectionCard {
                    // Segmented Theme Pill Selector
                    ThemeModeSelector(
                        selectedMode = state.themeMode,
                        onModeSelected = viewModel::setThemeMode,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    SettingsSwitchRow(
                        icon = Icons.Rounded.Palette,
                        title = "动态取色",
                        subtitle = "根据系统壁纸提取主题色",
                        checked = state.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ThemePaletteRow(
                        isDynamicEnabled = state.dynamicColor,
                        selectedColor = state.themeColor,
                        isCustomSelected = state.isCustomThemeColor,
                        onColorSelected = { color, isCustom ->
                            viewModel.setThemeColor(color, isCustom)
                        },
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    SettingsSwitchRow(
                        icon = Icons.Rounded.Contrast,
                        title = "AMOLED 纯黑背景",
                        subtitle = if (state.themeMode == ThemeMode.LIGHT) {
                            "当前处于浅色模式，切换至深色后生效"
                        } else {
                            "深色模式下使用纯黑底色降低功耗"
                        },
                        checked = state.amoledBlack,
                        onCheckedChange = viewModel::setAmoledBlack,
                    )
                }
            }

            // Section 2: 数据源与匹配策略
            Column {
                SectionHeader(title = "数据源与匹配")
                SectionCard {
                    Text(
                        text = "首选数据源",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SegmentedSourceSelector(
                        selectedSource = state.preferredSource,
                        onSourceSelected = viewModel::setPreferredSource,
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(14.dp))

                    SettingsSwitchRow(
                        icon = Icons.AutoMirrored.Rounded.AltRoute,
                        title = "双源 fallback 重试",
                        subtitle = "首选音源无结果或未达高置信度时，自动请求备选音源重试",
                        checked = state.dualSourceFallback,
                        onCheckedChange = viewModel::setDualSourceFallback,
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "多艺术家连接符号",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SegmentedSeparatorSelector(
                        selectedSeparator = state.artistSeparator,
                        onSeparatorSelected = viewModel::setArtistSeparator,
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(14.dp))

                    SettingsSwitchRow(
                        icon = Icons.Rounded.HighQuality,
                        title = "获取原图封面",
                        subtitle = "优先写入原图，上限制大小3M",
                        checked = state.fetchOriginalMasterCover,
                        onCheckedChange = viewModel::setFetchOriginalMasterCover,
                    )
                }
            }

            // Section 3: 歌词保存与翻译
            Column {
                SectionHeader(title = "歌词")
                SectionCard {
                    Text(
                        text = "歌词保存方式",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LyricsSavePolicy.entries.forEach { policy ->
                        val selected = state.lyricsSavePolicy == policy
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.setLyricsSavePolicy(policy) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = policy.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(14.dp))

                    SettingsSwitchRow(
                        icon = Icons.Rounded.Translate,
                        title = "获取翻译歌词",
                        subtitle = "获取歌词时自动将翻译合并入歌词文件",
                        checked = state.autoMergeTranslation,
                        onCheckedChange = viewModel::setAutoMergeTranslation,
                    )
                }
            }

            // Section 4: 文件过滤
            Column {
                SectionHeader(title = "文件过滤")
                SectionCard {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Timelapse,
                        title = "忽略短音频文件",
                        subtitle = "过滤小于 30 秒的音频文件",
                        checked = state.ignoreShortAudio,
                        onCheckedChange = viewModel::setIgnoreShortAudio,
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(14.dp))

                    SettingsSwitchRow(
                        icon = Icons.Rounded.VideocamOff,
                        title = "忽略视频文件",
                        subtitle = "过滤视频容器文件",
                        checked = state.ignoreVideoFiles,
                        onCheckedChange = viewModel::setIgnoreVideoFiles,
                    )
                }
            }

            // Section 5: 关于与更新
            Column {
                SectionHeader(title = "关于与更新")
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isCheckingUpdate) return@clickable
                                isCheckingUpdate = true
                                coroutineScope.launch {
                                    val res = org.mutag.app.update.UpdateChecker.checkUpdate(context, force = true)
                                    isCheckingUpdate = false
                                    val info = res.getOrNull()
                                    if (info != null) {
                                        updateInfo = info
                                    } else {
                                        android.widget.Toast.makeText(context, "当前已是最新版本", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "检查更新",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "当前版本：v${org.mutag.app.BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    updateInfo?.let { info ->
        org.mutag.app.update.UpdateDialog(
            info = info,
            onDownload = {
                org.mutag.app.update.UpdateChecker.openDownloadUrl(context, info.downloadUrl)
                updateInfo = null
            },
            onIgnore = {
                org.mutag.app.update.UpdateChecker.ignoreVersion(context, info.versionName)
                updateInfo = null
            },
            onDismiss = { updateInfo = null },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
    )
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun <T> SlidingSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    padding: Dp = 4.dp,
    itemContent: @Composable (item: T, isSelected: Boolean, contentColor: Color) -> Unit,
) {
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(height / 2),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val totalWidth = maxWidth
            val itemCount = items.size.coerceAtLeast(1)
            val itemWidth = totalWidth / itemCount

            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "sliding_indicator_offset",
            )

            Surface(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(itemWidth)
                    .fillMaxHeight(),
                shape = RoundedCornerShape((height - padding * 2) / 2),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shadowElevation = 0.5.dp,
            ) {}

            Row(modifier = Modifier.fillMaxSize()) {
                items.forEach { item ->
                    val isSelected = item == selectedItem
                    val animatedContentColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 200),
                        label = "segment_content_color",
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape((height - padding * 2) / 2))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onItemSelected(item)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        itemContent(item, isSelected, animatedContentColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeModeSelector(selectedMode: ThemeMode, onModeSelected: (ThemeMode) -> Unit) {
    SlidingSegmentedControl(
        items = ThemeMode.entries,
        selectedItem = selectedMode,
        onItemSelected = onModeSelected,
        height = 48.dp,
    ) { mode, isSelected, contentColor ->
        val icon = when (mode) {
            ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
            ThemeMode.LIGHT -> Icons.Rounded.LightMode
            ThemeMode.DARK -> Icons.Rounded.DarkMode
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = mode.title,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun ThemePaletteRow(
    isDynamicEnabled: Boolean,
    selectedColor: Long,
    isCustomSelected: Boolean,
    onColorSelected: (Long, Boolean) -> Unit,
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "主题配色方案",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (isDynamicEnabled) {
                    "壁纸动态取色生效中"
                } else if (isCustomSelected) {
                    "自定义配色"
                } else {
                    "预设配色"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isDynamicEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DEFAULT_THEME_PRESETS.forEach { preset ->
                val isSelected = !isCustomSelected && selectedColor == preset.color
                val itemAlpha = if (isDynamicEnabled) 0.38f else 1.0f
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isDynamicEnabled) {
                            onColorSelected(preset.color, false)
                        }
                        .padding(vertical = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .then(
                                if (isSelected) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier
                                },
                            )
                            .padding(if (isSelected) 3.dp else 0.dp)
                            .clip(CircleShape)
                            .background(Color(preset.color).copy(alpha = itemAlpha)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "已选定",
                                tint = if (isColorLight(preset.color)) Color.Black else Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = itemAlpha)
                        },
                    )
                }
            }

            // 自定义配色入口
            val customAlpha = if (isDynamicEnabled) 0.38f else 1.0f
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !isDynamicEnabled) {
                        showCustomDialog = true
                    }
                    .padding(vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .then(
                            if (isCustomSelected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            },
                        )
                        .padding(if (isCustomSelected) 3.dp else 0.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCustomSelected) {
                                Color(selectedColor).copy(alpha = customAlpha)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = customAlpha)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCustomSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "已选定自定义",
                            tint = if (isColorLight(selectedColor)) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.ColorLens,
                            contentDescription = "自定义颜色",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = customAlpha),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "自定义",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    color = if (isCustomSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = customAlpha)
                    },
                )
            }
        }
    }

    if (showCustomDialog) {
        CustomColorPickerDialog(
            initialColor = selectedColor,
            onDismiss = { showCustomDialog = false },
            onConfirm = { color ->
                onColorSelected(color, true)
                showCustomDialog = false
            },
        )
    }
}

private fun isColorLight(colorLong: Long): Boolean {
    val lab = DoubleArray(3)
    ColorUtils.colorToLAB(colorLong.toInt(), lab)
    return lab[0] > 60.0
}

@Composable
private fun CustomColorPickerDialog(initialColor: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var hexInput by remember {
        mutableStateOf(String.format("%06X", initialColor and 0x00FFFFFFL))
    }

    val quickColors = listOf(
        0xFFE91E63L,
        0xFF9C27B0L,
        0xFF3F51B5L,
        0xFF2196F3L,
        0xFF009688L,
        0xFF4CAF50L,
        0xFFFF9800L,
        0xFF795548L,
        0xFFFFEB3BL,
        0xFF00FF00L,
    )

    fun parseColor(hex: String): Long? {
        val clean = hex.trim().removePrefix("#")
        return when (clean.length) {
            6 -> {
                val value = clean.toLongOrNull(16) ?: return null
                0xFF000000L or value
            }

            8 -> clean.toLongOrNull(16)

            else -> null
        }
    }

    val parsedColor = parseColor(hexInput)
    val isError = parsedColor == null
    val currentValidColor = parsedColor ?: initialColor

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "自定义主题色彩",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column {
                    Text(
                        text = "候选与测试色",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        quickColors.take(5).forEach { c ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(c))
                                    .clickable {
                                        hexInput = String.format("%06X", c and 0x00FFFFFFL)
                                    },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        quickColors.drop(5).forEach { c ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(c))
                                    .clickable {
                                        hexInput = String.format("%06X", c and 0x00FFFFFFL)
                                    },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it.take(8).uppercase() },
                    label = { Text("颜色代码 (HEX)") },
                    prefix = { Text("#") },
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(currentValidColor)),
                        )
                    },
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text("请输入 6 位或 8 位 16 进制颜色代码")
                        } else {
                            Text("当前色彩值: #${String.format("%06X", currentValidColor and 0x00FFFFFFL)}")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                val previewLight = DynamicColorSchemeGenerator.createColorScheme(
                    seedColor = Color(currentValidColor),
                    isDark = false,
                )
                val previewDark = DynamicColorSchemeGenerator.createColorScheme(
                    seedColor = Color(currentValidColor),
                    isDark = true,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Material 3 转换预览",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = previewLight.primaryContainer,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = "浅色容器",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = previewLight.onPrimaryContainer,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .height(4.dp)
                                            .width(28.dp)
                                            .clip(CircleShape)
                                            .background(previewLight.primary),
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = previewDark.surfaceContainerHighest,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = "深色容器",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = previewDark.onSurface,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .height(4.dp)
                                            .width(28.dp)
                                            .clip(CircleShape)
                                            .background(previewDark.primary),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedColor != null) {
                        onConfirm(parsedColor)
                    }
                },
                enabled = !isError,
            ) {
                Text("应用配色")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun SegmentedSourceSelector(selectedSource: MetadataSource, onSourceSelected: (MetadataSource) -> Unit) {
    SlidingSegmentedControl(
        items = MetadataSource.entries,
        selectedItem = selectedSource,
        onItemSelected = onSourceSelected,
        height = 44.dp,
        padding = 3.dp,
    ) { source, isSelected, contentColor ->
        val icon = when (source) {
            MetadataSource.QQ_MUSIC -> Icons.Rounded.MusicNote
            MetadataSource.NETEASE -> Icons.Rounded.Cloud
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = source.title,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun SegmentedSeparatorSelector(selectedSeparator: String, onSeparatorSelected: (String) -> Unit) {
    val separators = remember { listOf(" / ", "; ", ", ") }
    SlidingSegmentedControl(
        items = separators,
        selectedItem = selectedSeparator,
        onItemSelected = onSeparatorSelected,
        height = 44.dp,
        padding = 3.dp,
    ) { sep, isSelected, contentColor ->
        Text(
            text = "\"$sep\"",
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
