package org.mutag.app.media

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.mutag.core.tagger.scanner.AudioFileScanner

/**
 * 系统媒体库（MediaStore）索引刷新与主动扫描辅助工具。
 */
object MediaScanHelper {

    /**
     * 向系统媒体库发起单个音频文件的重新扫描索引请求。
     *
     * @param context Android 上下文实例
     * @param filePath 目标音频文件绝对路径
     * @param onComplete 扫描完成回调，携带扫描入库后的 Uri（若失败为 null）
     */
    fun scanFile(context: Context, filePath: String, onComplete: ((Uri?) -> Unit)? = null) {
        val file = File(filePath)
        if (!file.exists()) return

        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            null,
        ) { _, uri ->
            onComplete?.invoke(uri)
        }
    }

    /**
     * 批量向系统媒体库发起文件扫描请求，支持协程等待全部完成或超时返回。
     *
     * @param context Android 上下文实例
     * @param filePaths 待扫描的文件绝对路径列表
     */
    suspend fun scanFiles(context: Context, filePaths: List<String>) = withContext(Dispatchers.IO) {
        val validPaths = filePaths.filter { File(it).exists() }
        if (validPaths.isEmpty()) return@withContext

        val chunkSize = 100
        for (chunk in validPaths.chunked(chunkSize)) {
            scanChunkWithTimeout(context, chunk, timeoutMs = 8000L)
        }
    }

    private suspend fun scanChunkWithTimeout(context: Context, paths: List<String>, timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val remaining = AtomicInteger(paths.size)
                MediaScannerConnection.scanFile(
                    context,
                    paths.toTypedArray(),
                    null,
                ) { _, _ ->
                    if (remaining.decrementAndGet() == 0) {
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                }
            }
        }
    }

    /**
     * 扫描常见的系统音乐存储目录及指定附加目录，递归收集音频文件并提交系统媒体库索引更新。
     *
     * @param context Android 上下文实例
     * @param extraPath 可选的附加浏览路径（如当前正在浏览的文件夹）
     */
    suspend fun scanCommonMusicDirectories(context: Context, extraPath: String? = null) = withContext(Dispatchers.IO) {
        val targetDirs = mutableSetOf<File>()

        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (musicDir != null && musicDir.exists() && musicDir.isDirectory) {
            targetDirs.add(musicDir)
        }

        if (!extraPath.isNullOrBlank()) {
            val extraFile = File(extraPath)
            if (extraFile.exists() && extraFile.isDirectory) {
                targetDirs.add(extraFile)
            }
        }

        val audioFiles = mutableListOf<String>()
        for (dir in targetDirs) {
            collectAudioFilesRecursively(dir, audioFiles)
        }

        scanFiles(context, audioFiles)
    }

    private fun collectAudioFilesRecursively(dir: File, outList: MutableList<String>) {
        val entries = dir.listFiles() ?: return
        for (e in entries) {
            if (e.isHidden) continue
            if (e.isDirectory) {
                if (!AudioFileScanner.isRecordingPath(e.name)) {
                    collectAudioFilesRecursively(e, outList)
                }
            } else if (AudioFileScanner.isAudioFile(e)) {
                outList.add(e.absolutePath)
            }
        }
    }
}
