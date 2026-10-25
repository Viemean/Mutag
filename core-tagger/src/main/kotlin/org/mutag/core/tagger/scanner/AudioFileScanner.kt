package org.mutag.core.tagger.scanner

import android.content.Context
import java.io.File
import org.mutag.core.tagger.cache.MetadataCache

/**
 * 音频文件系统扫描与 MediaStore 曲库检索工具。
 */
object AudioFileScanner {
    private val AUDIO_EXTENSIONS = setOf("flac", "mp3")

    private val RECORDING_KEYWORDS = listOf(
        "recording", "recordings", "sound_recorder", "recorder",
        "call_rec", "voice", "录音", "通话录音", "录音机",
    )

    /**
     * 判定路径是否归属于录音机目录。
     *
     * @param path 待检验路径
     * @return 为录音路径时返回 true
     */
    fun isRecordingPath(path: String): Boolean {
        val lower = path.lowercase()
        return RECORDING_KEYWORDS.any { lower.contains(it) }
    }

    /**
     * 检测指定文件扩展名是否受支持。
     *
     * @param file 待检查的文件对象
     * @return 扩展名为受支持音频格式返回 true，否则返回 false
     */
    fun isAudioFile(file: File): Boolean {
        if (!file.isFile || file.isHidden) return false
        val ext = file.extension.lowercase()
        return ext in AUDIO_EXTENSIONS
    }

    /**
     * 扫描 Android 媒体库中的音频曲目，并结合本地缓存加载元数据。
     *
     * @param context Android 上下文实例
     * @param cache 可选的元数据缓存接口
     * @param minDurationMs 过滤的最短音频时长（毫秒）
     * @return 扫描解析后的音频元数据列表
     */
    fun scanSystemAudio(context: Context, cache: MetadataCache? = null, minDurationMs: Long = 30000L): List<AudioMetadata> {
        val results = mutableListOf<AudioMetadata>()
        try {
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Media.DATA,
                android.provider.MediaStore.Audio.Media.DURATION,
            )
            val selection = "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${android.provider.MediaStore.Audio.Media.DURATION} >= $minDurationMs"
            val cursor = context.contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${android.provider.MediaStore.Audio.Media.TITLE} ASC",
            )
            cursor?.use { c ->
                val dataIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    if (dataIdx >= 0) {
                        val path = c.getString(dataIdx)
                        if (!path.isNullOrBlank() && !isRecordingPath(path)) {
                            val file = File(path)
                            if (file.exists() && file.isFile && isAudioFile(file)) {
                                val cached = cache?.get(file.absolutePath, file.length(), file.lastModified())
                                if (cached != null) {
                                    results.add(cached)
                                } else {
                                    val parsed = AudioHeaderParser.parse(file)
                                    cache?.put(parsed)
                                    results.add(parsed)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("AudioFileScanner", "scanSystemAudio failed", e)
        }
        return results
    }

    /**
     * 遍历指定目录并解析子文件夹与音频文件列表。
     *
     * @param directory 待扫描的目标目录
     * @param cache 可选的元数据缓存接口
     * @param onProgress 进度回调函数（当前已处理数，总数）
     * @return 包含子目录和音频元数据列表的扫描结果容器
     */
    fun scanDirectory(
        directory: File,
        cache: MetadataCache? = null,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
    ): DirectoryScanResult {
        if (!directory.exists() || !directory.isDirectory) {
            return DirectoryScanResult(currentPath = directory.absolutePath)
        }

        val entries = directory.listFiles() ?: emptyArray()
        val subDirs = mutableListOf<File>()
        val audioFiles = mutableListOf<File>()

        for (e in entries) {
            if (e.isHidden) continue
            if (e.isDirectory) {
                subDirs.add(e)
            } else if (isAudioFile(e)) {
                audioFiles.add(e)
            }
        }

        subDirs.sortBy { it.name.lowercase() }
        audioFiles.sortBy { it.name.lowercase() }

        val folderItems = mutableListOf<FolderItem>()
        for (subDir in subDirs) {
            val children = subDir.listFiles() ?: emptyArray()
            var subFolderCount = 0
            var audioCount = 0
            var pendingCount = 0

            for (c in children) {
                if (c.isHidden) continue
                if (c.isDirectory) {
                    subFolderCount++
                } else if (isAudioFile(c)) {
                    audioCount++
                    val cached = cache?.get(c.absolutePath, c.length(), c.lastModified())
                    if (cached != null) {
                        if (cached.tagHealth == TagHealth.NEEDS_MATCH) {
                            pendingCount++
                        }
                    } else {
                        // Quick guess: if filename contains " - " likely has artist + title, but without full parse count as pending
                        pendingCount++
                    }
                }
            }

            folderItems.add(
                FolderItem(
                    path = subDir.absolutePath,
                    name = subDir.name,
                    subFolderCount = subFolderCount,
                    audioFileCount = audioCount,
                    pendingMatchCount = pendingCount,
                ),
            )
        }

        val trackItems = mutableListOf<AudioMetadata>()
        val totalAudio = audioFiles.size

        for ((index, af) in audioFiles.withIndex()) {
            onProgress?.invoke(index + 1, totalAudio)
            val size = af.length()
            val lastMod = af.lastModified()

            val cached = cache?.get(af.absolutePath, size, lastMod)
            if (cached != null) {
                trackItems.add(cached)
            } else {
                val parsed = AudioHeaderParser.parse(af)
                cache?.put(parsed)
                trackItems.add(parsed)
            }
        }

        return DirectoryScanResult(
            currentPath = directory.absolutePath,
            folders = folderItems,
            tracks = trackItems,
        )
    }
}
