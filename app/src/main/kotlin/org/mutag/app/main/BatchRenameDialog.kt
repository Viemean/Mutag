package org.mutag.app.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import org.mutag.app.main.rename.FileRenameFormatter
import org.mutag.core.tagger.scanner.AudioMetadata

/**
 * 批量文件重命名模式配置与预览确认对话框组件。
 *
 * @param targetTracks 待重命名的目标曲目元数据列表
 * @param onDismiss 对话框关闭回调
 * @param onConfirm 确认重命名执行回调（通配模式，是否联动关联文件）
 */
@Composable
fun BatchRenameDialog(
    targetTracks: List<AudioMetadata>,
    onDismiss: () -> Unit,
    onConfirm: (pattern: String, renameAssociated: Boolean) -> Unit,
) {
    val presetRows = listOf(
        listOf("@2 - @1", "@1 - @2"),
        listOf("@5. @1", "@5. @2 - @1"),
        listOf("@4@5. @1", "@4@5. @2 - @1"),
    )

    var selectedPattern by remember { mutableStateOf("@2 - @1") }
    var isCustom by remember { mutableStateOf(false) }
    var customPattern by remember { mutableStateOf("") }
    var renameAssociated by remember { mutableStateOf(true) }

    val effectivePattern = if (isCustom) customPattern else selectedPattern

    val previewNewName = remember(targetTracks, effectivePattern) {
        val first = targetTracks.firstOrNull()
        if (first != null && effectivePattern.isNotBlank()) {
            FileRenameFormatter.formatNewFileName(first, effectivePattern)
        } else {
            ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "文件名相关批量处理",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                presetRows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { pattern ->
                            val isSelected = !isCustom && selectedPattern == pattern
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        selectedPattern = pattern
                                        isCustom = false
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedPattern = pattern
                                        isCustom = false
                                    },
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = pattern,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { isCustom = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isCustom,
                            onClick = { isCustom = true },
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "自定义: ",
                            fontSize = 13.sp,
                            fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                    ) {
                        BasicTextField(
                            value = customPattern,
                            onValueChange = {
                                customPattern = it
                                isCustom = true
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                Column {
                                    Box(modifier = Modifier.padding(bottom = 4.dp)) {
                                        if (customPattern.isEmpty()) {
                                            Text(
                                                text = "如 @2 - @1",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            )
                                        }
                                        innerTextField()
                                    }
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        color = if (isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            },
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 6.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = true,
                        onClick = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "重命名文件",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 2.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { renameAssociated = !renameAssociated },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = renameAssociated,
                        onCheckedChange = { renameAssociated = it },
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "重命名相关 Lrc 文件和图片文件",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 6.dp),
                )

                Text(
                    text = "参数定义：",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                val paramRows = listOf(
                    listOf("@1 - 标题", "@2 - 艺术家", "@3 - 专辑"),
                    listOf("@4 - 碟号", "@5 - 音轨号", "@6 - 年份"),
                    listOf("@7 - 注释", "@8 - 专辑艺术家", "@0 - 无用途"),
                )

                paramRows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        row.forEach { param ->
                            Text(
                                text = param,
                                fontSize = 11.sp,
                                color = if (param.contains("@0")) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (targetTracks.isNotEmpty() && previewNewName.isNotBlank()) {
                    val firstTrack = targetTracks.first()
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "首项预览 (共 ${targetTracks.size} 项)：",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${File(firstTrack.filePath).name}\n➔ $previewNewName",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (effectivePattern.isNotBlank()) {
                        onConfirm(effectivePattern, renameAssociated)
                    }
                },
                enabled = effectivePattern.isNotBlank(),
            ) {
                Text("确定重命名")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
