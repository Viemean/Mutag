package org.mutag.app.edit

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import org.mutag.app.ui.cover.CoverDownloadHelper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 封面操作底栏菜单组件（支持全屏查看、导出相册、替换、移除与技术规格查看）。
 *
 * @param state 当前单曲编辑状态
 * @param onDismiss 底栏关闭回调
 * @param onViewFullCover 打开全屏大图查看器回调
 * @param onSaveCover 导出封面到相册回调
 * @param onPickCover 本地选取图片替换封面回调
 * @param onViewTechSpec 查看音频技术规格回调
 * @param onPlayAudio 调用外部系统播放器播放音频回调
 * @param onRemoveCover 移除内嵌封面回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverActionBottomSheet(
    state: EditState,
    onDismiss: () -> Unit,
    onViewFullCover: () -> Unit,
    onSaveCover: () -> Unit,
    onPickCover: () -> Unit,
    onViewTechSpec: () -> Unit,
    onPlayAudio: () -> Unit,
    onRemoveCover: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasCover = state.coverBytes != null && state.coverBytes.isNotEmpty()

    val thumbBitmap = remember(state.coverBytes) {
        state.coverBytes?.let {
            CoverDownloadHelper.decodeSampledBitmap(it, 512)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
        ) {
            // 头部曲目摘要
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumbBitmap != null) {
                        Image(
                            bitmap = thumbBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title.ifBlank { state.fileName },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${state.artist.ifBlank { "未知艺术家" }} · ${state.album.ifBlank { "未知专辑" }}",
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(6.dp))

            // 菜单选项
            if (hasCover) {
                ListItem(
                    headlineContent = { Text("查看大图", fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                    leadingContent = {
                        Icon(imageVector = Icons.Default.ZoomIn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable { onViewFullCover() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                ListItem(
                    headlineContent = { Text("保存图片", fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                    leadingContent = {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable { onSaveCover() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            ListItem(
                headlineContent = { Text("更换图片", fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                leadingContent = {
                    Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable { onPickCover() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            ListItem(
                headlineContent = { Text("查看歌曲信息", fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                leadingContent = {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable { onViewTechSpec() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            ListItem(
                headlineContent = { Text("播放音乐", fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                leadingContent = {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable { onPlayAudio() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            if (hasCover) {
                ListItem(
                    headlineContent = { Text("移除封面", fontSize = 14.sp, color = MaterialTheme.colorScheme.error) },
                    leadingContent = {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable { onRemoveCover() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

/**
 * 全屏封面高分辨率大图查看器弹窗组件。
 *
 * @param coverBytes 封面图片字节数组
 * @param onDismiss 弹窗关闭回调
 * @param onLongPressSave 长按触发保存相册回调
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullCoverViewerDialog(coverBytes: ByteArray, onDismiss: () -> Unit, onLongPressSave: () -> Unit) {
    val (specText, fullBitmap) = remember(coverBytes) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, opts)
            val w = opts.outWidth
            val h = opts.outHeight
            val kb = coverBytes.size / 1024.0
            val bmp = CoverDownloadHelper.decodeSampledBitmap(coverBytes, 1024)
            "$w × $h · %.1f KB".format(kb) to bmp
        } catch (_: Exception) {
            "${coverBytes.size / 1024} KB" to null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // 关闭按钮
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 40.dp)
                    .size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                )
            }

            // 居中大图
            if (fullBitmap != null) {
                Image(
                    bitmap = fullBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(
                            onClick = onDismiss,
                            onLongClick = onLongPressSave,
                        ),
                    contentScale = ContentScale.Fit,
                )
            }

            // 底部元信息胶囊与提示
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = specText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "长按图片可保存到相册",
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}
