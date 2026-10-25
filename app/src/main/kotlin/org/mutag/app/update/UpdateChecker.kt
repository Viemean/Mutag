package org.mutag.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.mutag.app.BuildConfig

/**
 * 基于 GitHub Releases 的应用版本更新检查器。
 */
object UpdateChecker {

    private const val PREFS_NAME = "mutag_update_prefs"
    private const val KEY_LAST_CHECK_TIME = "last_check_timestamp"
    private const val KEY_IGNORED_VERSION = "ignored_version"

    private const val REPO_OWNER = "Viemean"
    private const val REPO_NAME = "Mutag"
    private const val API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    private const val DAILY_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 比较两个语义化版本号大小。
     *
     * @param v1 第一个版本号 (如 "1.0.1")
     * @param v2 第二个版本号 (如 "1.0.0")
     * @return 1 表示 v1 > v2，-1 表示 v1 < v2，0 表示相等
     */
    fun compareVersion(v1: String, v2: String): Int {
        val clean1 = v1.trim().removePrefix("v").removePrefix("V")
        val clean2 = v2.trim().removePrefix("v").removePrefix("V")

        val parts1 = clean1.split('.').mapNotNull { it.toIntOrNull() }
        val parts2 = clean2.split('.').mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val num1 = parts1.getOrElse(i) { 0 }
            val num2 = parts2.getOrElse(i) { 0 }
            if (num1 > num2) return 1
            if (num1 < num2) return -1
        }
        return 0
    }

    /**
     * 执行版本检查。
     *
     * @param context Android 上下文
     * @param force 是否强制检查（为 false 时遵循每天一次及忽略版本限制）
     * @return 检查结果包含的新版本信息实体，若无更新或跳过则为 null
     */
    suspend fun checkUpdate(context: Context, force: Boolean = false): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()

            if (!force) {
                val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
                if (now - lastCheck < DAILY_CHECK_INTERVAL_MS) {
                    return@runCatching null
                }
            }

            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MuTag-Android-App")
                .get()
                .build()

            val response = try {
                httpClient.newCall(request).execute()
            } catch (e: Exception) {
                return@runCatching null
            }

            prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()

            val bodyString = response.use { resp ->
                if (!resp.isSuccessful) {
                    return@runCatching null
                }
                resp.body?.string().orEmpty()
            }
            if (bodyString.isBlank()) return@runCatching null

            val root = json.parseToJsonElement(bodyString).jsonObject
            val tagName = root["tag_name"]?.jsonPrimitive?.content ?: return@runCatching null
            val remoteVersion = tagName.removePrefix("v").removePrefix("V")
            val releaseNotes = root["body"]?.jsonPrimitive?.content.orEmpty()
            val publishedAt = root["published_at"]?.jsonPrimitive?.content.orEmpty().take(10)
            val htmlUrl = root["html_url"]?.jsonPrimitive?.content.orEmpty()

            // 查找 APK 资源直链
            var downloadUrl = htmlUrl
            val assets = root["assets"]?.jsonArray
            if (assets != null) {
                for (elem in assets) {
                    val assetObj = elem.jsonObject
                    val name = assetObj["name"]?.jsonPrimitive?.content.orEmpty()
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        val directUrl = assetObj["browser_download_url"]?.jsonPrimitive?.content
                        if (!directUrl.isNullOrBlank()) {
                            downloadUrl = directUrl
                            break
                        }
                    }
                }
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val isNewer = compareVersion(remoteVersion, currentVersion) > 0

            if (!isNewer) {
                return@runCatching null
            }

            if (!force) {
                val ignoredVersion = prefs.getString(KEY_IGNORED_VERSION, null)
                if (ignoredVersion == remoteVersion) {
                    return@runCatching null
                }
            }

            AppUpdateInfo(
                versionName = remoteVersion,
                tag = tagName,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl,
                publishDate = publishedAt,
                isNewer = true,
            )
        }
    }

    /**
     * 记录忽略指定版本。
     *
     * @param context Android 上下文
     * @param version 目标版本号 (例如 "1.0.1")
     */
    fun ignoreVersion(context: Context, version: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_IGNORED_VERSION, version).apply()
    }

    /**
     * 打开外部浏览器跳转至下载地址。
     *
     * @param context Android 上下文
     * @param url 下载直链或发行页面地址
     */
    fun openDownloadUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.w("UpdateChecker", "Failed to open download url: $url", e)
            android.widget.Toast.makeText(context, "无法打开浏览器，请检查相关应用权限", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
