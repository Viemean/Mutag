package org.mutag.app.ui.cover

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/**
 * 封面图片文件名合法化过滤与系统相册导出工具。
 */
object CoverExportHelper {

    /**
     * 清理文件名中的非法字符并限制最大字符长度。
     *
     * @param input 原始文本内容
     * @param maxLength 最大保留字符长度
     * @return 过滤非法字符后的安全文件名片段
     */
    fun sanitizeFileNamePart(input: String, maxLength: Int = 40): String {
        if (input.isBlank()) return "Unknown"

        val cleaned = input
            .replace(Regex("""[\\/:*?"<>|\x00-\x1F\x7F]"""), "_")
            .replace(Regex("""[\s_]+"""), "_")
            .trim('_', '.', ' ')

        val truncated = if (cleaned.length > maxLength) {
            cleaned.take(maxLength).trimEnd('_', '.')
        } else {
            cleaned
        }

        return truncated.ifBlank { "Unknown" }
    }

    /**
     * 根据歌曲标题与专辑名称生成导出的图片文件名。
     *
     * @param title 歌曲标题
     * @param album 专辑名称
     * @return 格式化后的封面图片文件名
     */
    fun buildCoverFileName(title: String, album: String): String {
        val safeTitle = sanitizeFileNamePart(title)
        val safeAlbum = sanitizeFileNamePart(album)
        return "MuTag_${safeTitle}_$safeAlbum.jpg"
    }

    /**
     * 将封面图片字节流保存写入系统相册目录。
     *
     * @param context Android 上下文实例
     * @param coverBytes 待保存的图片字节流
     * @param title 歌曲标题
     * @param album 专辑名称
     * @return 包含目标保存 URI 字符串的操作结果
     */
    fun saveCoverToGallery(context: Context, coverBytes: ByteArray, title: String, album: String): Result<String> = runCatching {
        val fileName = buildCoverFileName(title, album)
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MuTag")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Failed to create MediaStore entry for $fileName")

        try {
            resolver.openOutputStream(uri)?.use { os ->
                os.write(coverBytes)
                os.flush()
            } ?: throw IllegalStateException("Failed to open output stream for $uri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }

        fileName
    }
}
