package org.mutag.app.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mutag.app.ui.theme.ColorPrimary
import org.mutag.app.ui.theme.ColorTextMuted
import org.mutag.app.ui.theme.ColorTextSecondary
import org.mutag.app.ui.theme.ColorWarning

/**
 * 批量在线匹配候选确认与字段筛选对话框组件。
 *
 * @param state 主界面当前状态
 * @param onToggleSelection 单项曲目候选勾选切换回调
 * @param onToggleField 全局待写入字段勾选切换回调
 * @param onRequestConfirm 请求开始批量写入回调
 * @param onDismiss 对话框关闭回调
 */
@Composable
fun BatchMatchDialog(
    state: MainState,
    onToggleSelection: (Int) -> Unit,
    onToggleField: (String) -> Unit,
    onRequestConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量标签匹配确认", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (state.isBatchMatching) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                    Text("正在并发分析与云端检索待匹配曲目...", fontSize = 12.sp, color = ColorTextMuted)
                } else if (state.isWritingBatch) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                        Text("正在串行写入音频标签...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { state.batchProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    // 字段勾选区域
                    Text(
                        text = "选择要匹配与覆盖的数据字段:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val fieldList = listOf(
                        "title" to "歌曲标题",
                        "artist" to "艺术家",
                        "album" to "专辑名称",
                        "cover" to "高清封面",
                        "lyrics" to "同步歌词",
                        "date" to "发行日期",
                        "trackNum" to "音轨序号",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        fieldList.forEach { (key, label) ->
                            val isSelected = state.batchSelectedFields[key] ?: true
                            FilterChip(
                                selected = isSelected,
                                onClick = { onToggleField(key) },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("待匹配曲目清单 (${state.batchCandidates.size} 首):", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    state.batchCandidates.forEachIndexed { index, candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onToggleSelection(index) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = candidate.isSelected,
                                onCheckedChange = { onToggleSelection(index) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    candidate.localFile.fileName,
                                    fontSize = 12.sp,
                                    color = ColorTextSecondary,
                                    maxLines = 1,
                                )
                                val matchText = candidate.matchedTrack?.let {
                                    "-> 匹配: ${it.title} · ${it.artistName}"
                                } ?: "-> 未检索到高置信度结果"
                                Text(
                                    matchText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (candidate.matchedTrack != null) ColorPrimary else ColorWarning,
                                    maxLines = 1,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRequestConfirm,
                enabled = !state.isBatchMatching && !state.isWritingBatch &&
                    state.batchCandidates.any { it.isSelected && it.matchedTrack != null } &&
                    state.batchSelectedFields.values.any { it },
            ) {
                Text("写入标签")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isWritingBatch) {
                Text("取消")
            }
        },
    )
}
