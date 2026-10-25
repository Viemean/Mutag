package org.mutag.app.edit

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mutag.core.tagger.scanner.AudioTechSpec

/**
 * 音频硬件级物理技术规格（编码、采样率、位深、比特率等）参数展示对话框。
 *
 * @param spec 音频技术规格数据对象
 * @param format 音频容器格式名称
 * @param filePath 音频文件绝对路径
 * @param coverBytes 可选的内嵌封面图像字节数组
 * @param onCopyPath 复制文件路径至剪贴板回调
 * @param onDismiss 对话框关闭回调
 */
@Composable
fun AudioTechSpecDialog(
    spec: AudioTechSpec?,
    format: String,
    filePath: String,
    coverBytes: ByteArray?,
    onCopyPath: () -> Unit,
    onDismiss: () -> Unit,
) {
    val coverInfo = remember(coverBytes) {
        if (coverBytes != null && coverBytes.isNotEmpty()) {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, opts)
                "${opts.outWidth} × ${opts.outHeight} px (%.1f KB)".format(coverBytes.size / 1024.0)
            } catch (_: Exception) {
                "${coverBytes.size / 1024} KB"
            }
        } else {
            "无内嵌封面"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "技术规格",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TechSpecRow(label = "编码格式", value = spec?.format?.ifBlank { format } ?: format)
                TechSpecRow(label = "采样率", value = spec?.sampleRateDisplay?.ifBlank { "未知" } ?: "未知")
                TechSpecRow(label = "采样位深", value = spec?.bitDepthDisplay?.ifBlank { "未知" } ?: "未知")
                TechSpecRow(label = "声道配置", value = spec?.channelsDisplay?.ifBlank { "未知" } ?: "未知")
                TechSpecRow(label = "比特率", value = spec?.bitrateDisplay?.ifBlank { "未知" } ?: "未知")
                TechSpecRow(label = "文件大小", value = spec?.fileSizeDisplay?.ifBlank { "未知" } ?: "未知")
                TechSpecRow(label = "音质级别", value = spec?.qualityLevel?.ifBlank { "未知" } ?: "未知")
                TechSpecRow(label = "音频时长", value = spec?.durationDisplay?.ifBlank { "未知" } ?: "未知")
                TechSpecRow(label = "标签规范", value = spec?.tagVersion?.ifBlank { "未知" } ?: "未知")
                TechSpecRow(label = "封面规格", value = coverInfo)

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )

                // 文件路径与复制小按钮
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "文件路径",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = onCopyPath, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "复制路径",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = filePath,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

/**
 * 技术规格单项键值展示行组件。
 *
 * @param label 参数名称标签
 * @param value 参数取值文本
 */
@Composable
fun TechSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
