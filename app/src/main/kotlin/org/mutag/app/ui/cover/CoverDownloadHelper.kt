package org.mutag.app.ui.cover

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import org.mutag.app.MuTagApplication
import org.mutag.app.settings.AppSettingsManager
import org.mutag.model.TrackMetadata
import org.mutag.provider.api.CoverResolution
import org.mutag.provider.api.MusicMetadataProvider

/**
 * 在线专辑封面多分辨率阶梯式下载与尺寸安全压缩辅助工具。
 */
object CoverDownloadHelper {

    private const val MAX_COVER_IMAGE_SIZE = 3 * 1024 * 1024 // 3 MB

    /**
     * 根据设置与曲目元数据计算预期的云端封面下载直链。
     *
     * @param trackMetadata 曲目元数据
     * @param preferMaster 是否优先母带原图
     * @param provider 可选元数据提供商实例
     * @return 目标封面 URL
     */
    fun resolveTargetCoverUrl(
        trackMetadata: TrackMetadata,
        preferMaster: Boolean = runCatching { AppSettingsManager.settingsState.value.fetchOriginalMasterCover }.getOrDefault(false),
        provider: MusicMetadataProvider? = null,
    ): String {
        val activeProvider = provider ?: runCatching { MuTagApplication.instance.getProviderForTrack(trackMetadata) }.getOrNull()
        if (activeProvider != null) {
            val resolution = if (preferMaster) CoverResolution.ORIGINAL_MASTER else CoverResolution.LARGE
            return activeProvider.resolveCoverUrl(trackMetadata, resolution)
        }
        val albumMid = trackMetadata.album.id
        return if (albumMid.isNotBlank() && !albumMid.all { it.isDigit() }) {
            "https://y.gtimg.cn/music/photo_new/T002R${if (preferMaster) "800x800" else "1200x1200"}M000$albumMid.jpg"
        } else {
            trackMetadata.coverUrl.replace("http://", "https://")
        }
    }

    /**
     * 发送轻量 HTTP HEAD 请求探测远端封面文件字节大小。
     *
     * @param httpClient 网络请求客户端
     * @param url 目标图片直链
     * @return 远端 Content-Length 字节数，请求失败或响应异常返回 null
     */
    fun probeCoverLength(httpClient: OkHttpClient, url: String): Long? {
        if (url.isBlank()) return null
        return try {
            val req = Request.Builder().url(url).head().build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val len = resp.header("Content-Length")?.toLongOrNull()
                    if (len != null && len > 0L) len else null
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 根据曲目元信息阶梯式下载专辑封面图片字节流并执行尺寸压缩。
     *
     * @param httpClient 网络请求客户端
     * @param trackMetadata 曲目元数据信息
     * @return 下载并校验通过的封面图片字节数组，下载失败返回 null
     */
    fun downloadCover(httpClient: OkHttpClient, trackMetadata: TrackMetadata, provider: MusicMetadataProvider? = null): ByteArray? {
        val activeProvider = provider ?: runCatching { MuTagApplication.instance.getProviderForTrack(trackMetadata) }.getOrNull()
        val fetchMaster = runCatching { AppSettingsManager.settingsState.value.fetchOriginalMasterCover }.getOrDefault(false)

        var downloadedBytes: ByteArray? = null

        if (activeProvider != null) {
            if (fetchMaster) {
                val masterUrl = activeProvider.resolveCoverUrl(trackMetadata, CoverResolution.ORIGINAL_MASTER)
                if (masterUrl.isNotBlank()) {
                    val masterBytes = fetchBytes(httpClient, masterUrl)
                    if (masterBytes != null && isValidBitmap(masterBytes)) {
                        downloadedBytes = masterBytes
                    }
                }
            }

            if (downloadedBytes == null) {
                val hdUrl = activeProvider.resolveCoverUrl(trackMetadata, CoverResolution.LARGE)
                if (hdUrl.isNotBlank()) {
                    val hdBytes = fetchBytes(httpClient, hdUrl)
                    if (hdBytes != null && isValidBitmap(hdBytes)) {
                        downloadedBytes = hdBytes
                    }
                }
            }
        }

        if (downloadedBytes == null) {
            val targetUrl = resolveTargetCoverUrl(trackMetadata, fetchMaster, activeProvider)
            if (targetUrl.isNotBlank()) {
                val fallbackBytes = fetchBytes(httpClient, targetUrl)
                if (fallbackBytes != null && isValidBitmap(fallbackBytes)) {
                    downloadedBytes = fallbackBytes
                }
            }
        }

        val rawBytes = downloadedBytes ?: return null

        return if (rawBytes.size > MAX_COVER_IMAGE_SIZE) {
            compressCoverSafely(rawBytes)
        } else {
            rawBytes
        }
    }

    private fun isValidBitmap(bytes: ByteArray): Boolean = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        opts.outWidth > 0 && opts.outHeight > 0
    } catch (_: Exception) {
        false
    } catch (_: OutOfMemoryError) {
        false
    }

    private fun fetchBytes(client: OkHttpClient, url: String): ByteArray? = try {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val bytes = resp.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) bytes else null
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun compressCoverSafely(coverBytes: ByteArray): ByteArray {
        return try {
            var bitmap = BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size) ?: return coverBytes
            val baos = ByteArrayOutputStream()
            var quality = 95
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)

            while (baos.size() > MAX_COVER_IMAGE_SIZE && quality > 60) {
                baos.reset()
                quality -= 10
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            }

            while (baos.size() > MAX_COVER_IMAGE_SIZE && bitmap.width > 1200 && bitmap.height > 1200) {
                val scale = 0.8f
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
                baos.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            }

            if (baos.size() in 1..MAX_COVER_IMAGE_SIZE) {
                baos.toByteArray()
            } else {
                coverBytes
            }
        } catch (_: Exception) {
            coverBytes
        } catch (_: OutOfMemoryError) {
            coverBytes
        }
    }
}
