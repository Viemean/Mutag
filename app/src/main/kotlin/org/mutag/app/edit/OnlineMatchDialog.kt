package org.mutag.app.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.mutag.app.MuTagApplication
import org.mutag.app.settings.MetadataSource
import org.mutag.app.ui.theme.ColorTextMuted
import org.mutag.model.TrackMetadata
import org.mutag.provider.api.CoverResolution

/**
 * 单曲在线搜索与元数据差异比对选择对话框组件。
 *
 * @param state 当前单曲编辑状态
 * @param onSelectCandidate 候选曲目选中回调
 * @param onToggleField 字段差异勾选状态切换回调
 * @param onSelectAllFields 全选字段差异回调
 * @param onInvertFields 反选字段差异回调
 * @param onClearFields 清空字段差异回调
 * @param onResetDefaultFields 重置默认字段勾选回调
 * @param onQueryChange 搜索输入词变化回调
 * @param onSearch 发起在线搜索回调
 * @param onSourceChange 切换在线匹配音源回调
 * @param onAcousticRecognize 发起声学指纹识别回调
 * @param onApply 确认应用候选元数据回调
 * @param onDismiss 对话框关闭回调
 */
@Composable
fun OnlineMatchDialog(
    state: EditState,
    onSelectCandidate: (TrackMetadata) -> Unit,
    onToggleField: (String) -> Unit,
    onSelectAllFields: () -> Unit,
    onInvertFields: () -> Unit,
    onClearFields: () -> Unit,
    onResetDefaultFields: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSourceChange: (MetadataSource) -> Unit = {},
    onAcousticRecognize: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showFieldSelectionDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("匹配信息", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var sourceMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            onClick = { sourceMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.height(28.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (state.matchSource == MetadataSource.NETEASE) "网易云" else "QQ 音乐",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "切换音源",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = sourceMenuExpanded,
                            onDismissRequest = { sourceMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("QQ 音乐", fontSize = 13.sp) },
                                trailingIcon = {
                                    if (state.matchSource == MetadataSource.QQ_MUSIC) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                },
                                onClick = {
                                    sourceMenuExpanded = false
                                    onSourceChange(MetadataSource.QQ_MUSIC)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("网易云音乐", fontSize = 13.sp) },
                                trailingIcon = {
                                    if (state.matchSource == MetadataSource.NETEASE) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                },
                                onClick = {
                                    sourceMenuExpanded = false
                                    onSourceChange(MetadataSource.NETEASE)
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onAcousticRecognize,
                        modifier = Modifier.size(32.dp),
                        enabled = !state.isSearchingOnline,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "音频识别",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { showFieldSelectionDialog = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "选择字段",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // 搜索框
                OutlinedTextField(
                    value = state.onlineSearchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入关键词搜索...", fontSize = 13.sp) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.onlineSearchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { onQueryChange("") },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "清除", modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(
                                onClick = { onSearch(state.onlineSearchQuery) },
                                modifier = Modifier.size(28.dp),
                                enabled = !state.isSearchingOnline,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "搜索",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(state.onlineSearchQuery) }),
                    shape = RoundedCornerShape(8.dp),
                )

                // 快捷检索胶囊（水平滑动）
                if (state.quickSearchChips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(state.quickSearchChips) { (label, q) ->
                            val isCurrent = state.onlineSearchQuery.trim() == q.trim()
                            SuggestionChip(
                                onClick = {
                                    onQueryChange(q)
                                    onSearch(q)
                                },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                border = null,
                                modifier = Modifier.height(28.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (state.isSearchingOnline) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else if (state.searchCandidates.isEmpty()) {
                    Text("未检索到匹配曲目，请检查关键词", fontSize = 14.sp, color = ColorTextMuted)
                } else {
                    Text("候选列表:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    state.searchCandidates.forEach { c ->
                        val selected = state.selectedCandidate?.trackId == c.trackId
                        val durationText = remember(c.durationMs) {
                            if (c.durationMs > 0L) {
                                val totalSec = c.durationMs / 1000L
                                val min = totalSec / 60L
                                val sec = totalSec % 60L
                                "%02d:%02d".format(min, sec)
                            } else {
                                ""
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable { onSelectCandidate(c) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val thumbUrl = MuTagApplication.instance.getProviderForTrack(c)
                                .resolveCoverUrl(c, CoverResolution.THUMBNAIL)

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (thumbUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = thumbUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Album,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = c.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${c.artistName} · ${c.album.name}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.height(44.dp),
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                } else {
                                    Spacer(modifier = Modifier.size(20.dp))
                                }

                                if (durationText.isNotEmpty()) {
                                    Text(
                                        text = durationText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApply,
                enabled = state.selectedCandidate != null && !state.isSearchingOnline,
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )

    if (showFieldSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showFieldSelectionDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("选择字段", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row {
                        TextButton(onClick = onSelectAllFields) {
                            Text("全选", fontSize = 12.sp)
                        }
                        TextButton(onClick = onClearFields) {
                            Text("清空", fontSize = 12.sp)
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    CANDIDATE_METADATA_FIELDS.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleField(key) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = state.candidateDiffFields[key] ?: true,
                                onCheckedChange = { onToggleField(key) },
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showFieldSelectionDialog = false }) {
                    Text("完成")
                }
            },
        )
    }
}
