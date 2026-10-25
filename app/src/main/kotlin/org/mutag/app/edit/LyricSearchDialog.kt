package org.mutag.app.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 双源歌词在线搜索与预览选用对话框。
 *
 * @param query 当前检索关键词
 * @param isSearching 是否处于并发搜索中
 * @param candidates 聚合候选列表
 * @param showQq 是否显示 QQ 音乐候选
 * @param showNetease 是否显示网易云音乐候选
 * @param hasFilteredPureMusic 是否已有纯音乐候选被过滤
 * @param onSearch 发起检索回调
 * @param onToggleSource 切换音源来源过滤回调
 * @param onTogglePreview 展开/收起预览回调
 * @param onApply 选用歌词回调
 * @param onDismissRequest 关闭对话框回调
 */
@Composable
fun LyricSearchDialog(
    query: String,
    isSearching: Boolean,
    candidates: List<LyricCandidate>,
    showQq: Boolean = true,
    showNetease: Boolean = true,
    hasFilteredPureMusic: Boolean = false,
    onSearch: (String) -> Unit,
    onToggleSource: (String) -> Unit,
    onTogglePreview: (LyricCandidate) -> Unit,
    onApply: (LyricCandidate) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var inputQuery by remember(query) { mutableStateOf(query) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val filteredCandidates = remember(candidates, showQq, showNetease) {
        candidates.filter { c ->
            (c.sourceId == "qqmusic" && showQq) || (c.sourceId == "netease" && showNetease)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp),
            ) {
                // 顶部标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "歌词搜索",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "选择音源",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = showQq,
                                                onCheckedChange = null,
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("QQ音乐")
                                        }
                                    },
                                    onClick = { onToggleSource("qqmusic") },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = showNetease,
                                                onCheckedChange = null,
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("网易云音乐")
                                        }
                                    },
                                    onClick = { onToggleSource("netease") },
                                )
                            }
                        }
                        IconButton(onClick = onDismissRequest) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 搜索框
                OutlinedTextField(
                    value = inputQuery,
                    onValueChange = { inputQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("搜索歌曲、歌手或歌词关键词", fontSize = 13.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (inputQuery.isNotBlank()) {
                            IconButton(onClick = { onSearch(inputQuery.trim()) }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "搜索",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(inputQuery.trim()) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 内容列表区
                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "正在聚合检索 QQ音乐 与 网易云 歌词...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (filteredCandidates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        val emptyPrompt = when {
                            hasFilteredPureMusic -> "纯音乐，暂无歌词"
                            candidates.isEmpty() -> "未找到候选歌词，可调整关键词后重新搜索"
                            else -> "当前所选音源无匹配结果"
                        }
                        Text(
                            text = emptyPrompt,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filteredCandidates, key = { it.id }) { item ->
                            LyricCandidateCard(
                                candidate = item,
                                onTogglePreview = { onTogglePreview(item) },
                                onApply = { onApply(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricCandidateCard(candidate: LyricCandidate, onTogglePreview: () -> Unit, onApply: () -> Unit) {
    val durationText = remember(candidate.durationMs) {
        val totalSec = candidate.durationMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        "%02d:%02d".format(min, sec)
    }

    val isNetease = candidate.sourceId == "netease"
    val sourceLabel = if (isNetease) "网易云" else "QQ音乐"
    val badgeBg = if (isNetease) Color(0xFFD32F2F).copy(alpha = 0.12f) else Color(0xFF1E88E5).copy(alpha = 0.12f)
    val badgeColor = if (isNetease) Color(0xFFD32F2F) else Color(0xFF1E88E5)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 音源与时长徽标
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = sourceLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                        )
                    }
                    if (candidate.durationMs > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = durationText,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 操作按钮组
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = onTogglePreview,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(if (candidate.isExpanded) "收起" else "预览", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = if (candidate.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onApply,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("选用", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 歌曲信息
            Text(
                text = candidate.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subInfo = buildString {
                if (candidate.artist.isNotBlank()) append(candidate.artist)
                if (candidate.album.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(candidate.album)
                }
            }
            if (subInfo.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subInfo,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 展开歌词预览区域
            AnimatedVisibility(
                visible = candidate.isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    if (candidate.isLoadingLyrics) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在拉取歌词...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val lyricText = candidate.lyrics.orEmpty().ifBlank { "（歌词内容为空）" }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = lyricText,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
