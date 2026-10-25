package org.mutag.core.tagger.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * 外部存储权限检测与系统音频存储路径解析工具。
 */
object StorageAccessManager {

    /**
     * 检查当前应用是否具备外部存储或所有文件访问权限。
     *
     * @param context Android 上下文实例
     * @return 具备读写权限返回 true，未授权返回 false
     */
    fun hasStoragePermission(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        val readPerm = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
        )
        val writePerm = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
        readPerm == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            writePerm == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * 构建所有文件访问权限系统授权界面的跳转 Intent。
     *
     * @param context Android 上下文实例
     * @return Android 11 及以上返回对应的设置页面 Intent，低版本返回 null
     */
    fun createStorageManagerIntent(context: Context): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } catch (_: Exception) {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    } else {
        null
    }

    /**
     * 获取系统公共音乐目录文件句柄。
     *
     * @return 系统 Music 目录对象，回退路径为 /sdcard/Music
     */
    fun getDefaultMusicDirectory(): java.io.File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        ?: java.io.File("/sdcard/Music")
}
