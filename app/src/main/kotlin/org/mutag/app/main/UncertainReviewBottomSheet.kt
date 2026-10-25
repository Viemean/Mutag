package org.mutag.app.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 批量匹配低置信度存疑项人工核验复查底栏组件。
 *
 * @param items 存疑匹配项列表
 * @param onApply 单项确认采纳回调
 * @param onApplyAll 全部有候选存疑项批量采纳回调
 * @param onDismissItem 忽略单条存疑项回调
 * @param onClearAll 清空全部存疑列表回调
 * @param onDismissRequest 关闭底栏回调
 * @param onNavigateToEdit 导航至单曲标签编辑页面回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UncertainReviewBottomSheet(
    items: List<UncertainMatchItem>,
    onApply: (UncertainMatchItem) -> Unit,
    onApplyAll: () -> Unit,
    onDismissItem: (UncertainMatchItem) -> Unit,
    onClearAll: () -> Unit,
    onDismissRequest: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "存疑曲目待核对",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "共 ${items.size} 首曲目需要确认版本",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onApplyAll) {
                        Text(
                            text = "全部采纳",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(onClick = onClearAll) {
                        Text("全部忽略", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.localFile.filePath }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToEdit(item.localFile.filePath) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = item.localFile.title.ifBlank { item.localFile.fileName },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val durSec = if (item.localFile.durationMs >
                                0
                            ) {
                                "%02d:%02d".format(item.localFile.durationMs / 60000, (item.localFile.durationMs % 60000) / 1000)
                            } else {
                                "--:--"
                            }
                            Text(
                                text = "本地: ${item.localFile.artist.ifBlank { "未知歌手" }} · $durSec",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            if (item.candidate != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val candDurSec = if (item.candidate.durationMs >
                                    0
                                ) {
                                    "%02d:%02d".format(item.candidate.durationMs / 60000, (item.candidate.durationMs % 60000) / 1000)
                                } else {
                                    "--:--"
                                }
                                Text(
                                    text = "匹配: ${item.candidate.title} - ${item.candidate.artistName} · $candDurSec",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = item.reason,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { onDismissItem(item) }) {
                                    Text("忽略", fontSize = 12.sp)
                                }
                                if (item.candidate != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = { onApply(item) },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    ) {
                                        Text("采纳此版本", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
