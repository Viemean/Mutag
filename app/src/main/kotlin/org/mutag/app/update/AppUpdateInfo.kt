package org.mutag.app.update

/**
 * 远端应用版本更新实体。
 *
 * @param versionName 规范化版本名称 (例如 "1.0.1")
 * @param tag 原始 GitHub 发布 Tag (例如 "v1.0.1")
 * @param releaseNotes 版本更新说明日志
 * @param downloadUrl 目标下载地址 (优先为 APK 直链，回退为 GitHub Release 详情页)
 * @param publishDate 发布日期格式化文本
 * @param isNewer 远端版本是否高于当前本地安装版本
 */
data class AppUpdateInfo(
    val versionName: String,
    val tag: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val publishDate: String,
    val isNewer: Boolean,
)
