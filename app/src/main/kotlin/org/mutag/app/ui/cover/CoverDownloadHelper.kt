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

    const val MAX_COVER_IMAGE_SIZE = (2.5 * 1024 * 1024).toInt() // 2.5 MB
    const val MAX_COVER_DIMENSION = 3000
    const val DEFAULT_UI_SAMPLE_DIMENSION = 1024

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

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
        val needsDimensionReduction = maxOf(opts.outWidth, opts.outHeight) > MAX_COVER_DIMENSION
        val needsSizeCompression = rawBytes.size > MAX_COVER_IMAGE_SIZE

        return if (needsDimensionReduction || needsSizeCompression) {
            compressCoverSafely(rawBytes)
        } else {
            rawBytes
        }
    }

    /**
     * 获取曲目匹配封面，若本地已有等价封面则就地压缩复用，避免重复网络下载；否则从网络下载并执行安全规格压缩。
     *
     * @param httpClient 网络请求客户端
     * @param targetTrack 云端曲目元数据
     * @param localCoverBytes 本地音频已内嵌的封面字节数据
     * @param localCloudTrackId 本地音频已记录的云端曲目 ID
     * @param localCloudAlbumMid 本地音频已记录的云端专辑 MID
     * @param provider 可选元数据提供商实例
     * @return 最终合规的封面字节数组，若均不可用返回 null
     */
    fun obtainOrCompressCover(
        httpClient: OkHttpClient,
        targetTrack: TrackMetadata,
        localCoverBytes: ByteArray?,
        localCloudTrackId: String = "",
        localCloudAlbumMid: String = "",
        provider: MusicMetadataProvider? = null,
    ): ByteArray? {
        if (localCoverBytes != null && localCoverBytes.isNotEmpty()) {
            val isKnownSameCloud = (localCloudAlbumMid.isNotBlank() && localCloudAlbumMid == targetTrack.album.id) ||
                (localCloudTrackId.isNotBlank() && localCloudTrackId == targetTrack.trackId)

            val isSameByProbe = if (!isKnownSameCloud) {
                val preferMaster = runCatching { AppSettingsManager.settingsState.value.fetchOriginalMasterCover }.getOrDefault(false)
                val targetUrl = resolveTargetCoverUrl(targetTrack, preferMaster, provider)
                val cloudLen = probeCoverLength(httpClient, targetUrl)
                cloudLen != null && cloudLen == localCoverBytes.size.toLong()
            } else {
                false
            }

            if (isKnownSameCloud || isSameByProbe) {
                return compressCoverSafely(localCoverBytes)
            }
        }

        if (targetTrack.coverUrl.isNotBlank() || targetTrack.album.id.isNotBlank()) {
            return downloadCover(httpClient, targetTrack, provider)
        }

        return localCoverBytes
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

    /**
     * 将封面图像字节压缩至 2.5MB 内，并限制最大边长不超过 3000 像素。
     *
     * @param coverBytes 原始封面字节数组
     * @return 压缩与缩放后的图片字节数组
     */
    fun compressCoverSafely(coverBytes: ByteArray): ByteArray {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, opts)
            val origW = opts.outWidth
            val origH = opts.outHeight
            if (origW <= 0 || origH <= 0) return coverBytes

            val needsDimensionReduction = maxOf(origW, origH) > MAX_COVER_DIMENSION
            val needsSizeCompression = coverBytes.size > MAX_COVER_IMAGE_SIZE
            if (!needsDimensionReduction && !needsSizeCompression) {
                return coverBytes
            }

            var sampleSize = 1
            val maxSide = maxOf(origW, origH)
            while (maxSide / (sampleSize * 2) >= MAX_COVER_DIMENSION) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bitmap = BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, decodeOpts) ?: return coverBytes

            val curMaxSide = maxOf(bitmap.width, bitmap.height)
            if (curMaxSide > MAX_COVER_DIMENSION) {
                val scale = MAX_COVER_DIMENSION.toFloat() / curMaxSide
                val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
            }

            val baos = ByteArrayOutputStream()
            var quality = 95
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)

            while (baos.size() > MAX_COVER_IMAGE_SIZE && quality > 60) {
                baos.reset()
                quality -= 10
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            }

            while (baos.size() > MAX_COVER_IMAGE_SIZE && bitmap.width > 1200 && bitmap.height > 1200) {
                val scale = 0.85f
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

    /**
     * 按目标最大边长对图像字节进行采样率下采样解码，防止超大位图撑爆 Canvas。
     *
     * @param bytes 图像原始字节数组
     * @param maxDimension 目标最大边长物理像素（默认 1024）
     * @return 采样解码后的 Bitmap，失败返回 null
     */
    fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int = DEFAULT_UI_SAMPLE_DIMENSION): Bitmap? {
        if (bytes.isEmpty()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

            var sampleSize = 1
            val maxSide = maxOf(opts.outWidth, opts.outHeight)
            while (maxSide / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }
}
