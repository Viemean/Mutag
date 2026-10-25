package org.mutag.app.main.rename

import android.content.Context
import android.media.MediaScannerConnection
import java.io.File
import org.mutag.core.tagger.scanner.AudioMetadata

/**
 * 音频文件及其关联附件批量原子重命名执行器。
 */
object BatchRenameExecutor {

    /**
     * 批量执行音频文件重命名并联动重命名同名关联附件。
     *
     * @param targets 待重命名的音频元数据列表
     * @param pattern 重命名通配模式规则
     * @param renameAssociated 是否联动重命名同级关联附件文件
     * @param context 可选的上下文实例，用于触发 MediaScanner 索引同步
     * @return 成功完成重命名的音频文件数量
     */
    fun execute(targets: List<AudioMetadata>, pattern: String, renameAssociated: Boolean, context: Context? = null): Int {
        val scannedPaths = mutableListOf<String>()
        var renamedCount = 0

        targets.forEach { track ->
            try {
                val oldFile = File(track.filePath)
                if (!oldFile.exists() || !oldFile.isFile) return@forEach

                val newFileName = FileRenameFormatter.formatNewFileName(track, pattern)
                val targetFile = File(oldFile.parentFile, newFileName)

                if (oldFile.name != targetFile.name) {
                    val associatedFiles = if (renameAssociated) {
                        FileRenameFormatter.findAssociatedFiles(oldFile)
                    } else {
                        emptyList()
                    }

                    if (oldFile.renameTo(targetFile)) {
                        renamedCount++
                        scannedPaths.add(oldFile.absolutePath)
                        scannedPaths.add(targetFile.absolutePath)

                        val newBaseName = targetFile.nameWithoutExtension
                        associatedFiles.forEach { assocFile ->
                            val newAssocFile = File(assocFile.parentFile, "$newBaseName.${assocFile.extension}")
                            if (assocFile.renameTo(newAssocFile)) {
                                scannedPaths.add(assocFile.absolutePath)
                                scannedPaths.add(newAssocFile.absolutePath)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("BatchRenameExecutor", "Failed to rename ${track.filePath}", e)
            }
        }

        if (scannedPaths.isNotEmpty() && context != null) {
            try {
                MediaScannerConnection.scanFile(
                    context,
                    scannedPaths.toTypedArray(),
                    null,
                    null,
                )
            } catch (e: Exception) {
                android.util.Log.w("BatchRenameExecutor", "MediaScanner scan failed", e)
            }
        }

        return renamedCount
    }
}
