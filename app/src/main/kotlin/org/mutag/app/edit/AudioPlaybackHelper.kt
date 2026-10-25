package org.mutag.app.edit

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * 音频外部播放器调用与系统 Intent 路由辅助工具。
 */
object AudioPlaybackHelper {

    /**
     * 调用系统应用选择器打开并播放目标音频文件。
     *
     * @param context Android 上下文实例
     * @param filePath 目标音频文件的绝对路径
     */
    fun playAudio(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "音频文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        val ext = file.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "audio/*"
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(viewIntent, "选择音乐播放器").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未找到可用的音乐播放器", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "打开播放器失败", Toast.LENGTH_SHORT).show()
        }
    }
}
