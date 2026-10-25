package org.mutag.app.ui.cover

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import java.io.File
import okio.Buffer

/**
 * 音频内嵌封面图片 Coil 检索模型。
 *
 * @param file 音频源文件对象
 */
data class EmbeddedAudioCover(val file: File)

/**
 * 基于 Coil 框架的本地音频文件内嵌封面解码提取器。
 *
 * @param data 内嵌封面音频文件描述实体
 * @param options Coil 加载选项
 */
class EmbeddedCoverFetcher(private val data: EmbeddedAudioCover, private val options: Options) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val file = data.file
        if (!file.exists() || !file.isFile) return null

        val bytes = extractCoverBytes(file) ?: return null
        val buffer = Buffer().write(bytes)

        return SourceFetchResult(
            source = ImageSource(buffer, options.fileSystem),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    /**
     * 内嵌封面提取器构建工厂。
     */
    class Factory : Fetcher.Factory<EmbeddedAudioCover> {
        override fun create(data: EmbeddedAudioCover, options: Options, imageLoader: ImageLoader): Fetcher =
            EmbeddedCoverFetcher(data, options)
    }

    companion object {
        /**
         * 从指定音频文件解析提取内嵌封面图像字节数组。
         *
         * @param file 目标音频文件
         * @return 提取成功返回图像字节数组，无内嵌封面或解析失败返回 null
         */
        fun extractCoverBytes(file: File): ByteArray? = org.mutag.core.tagger.scanner.AudioCoverExtractor.extractCoverBytes(file)
    }
}
