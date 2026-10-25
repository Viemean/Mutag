package org.mutag.core.tagger.scanner

import java.io.File

/**
 * 音频头部信息与内嵌元数据解析入口调度器。
 */
object AudioHeaderParser {

    /**
     * 解析指定音频文件的流属性与内嵌标签。
     *
     * @param file 待解析物理音频文件
     * @return 解析完成的音频元数据实体；解析异常或格式不受支持时返回基础兜底元数据
     */
    fun parse(file: File): AudioMetadata {
        val fileName = file.name
        val fileSize = file.length()
        val lastModified = file.lastModified()
        val ext = file.extension.lowercase()

        return try {
            when (ext) {
                "flac" -> FlacHeaderParser.parse(file, fileName, fileSize, lastModified)

                "mp3" -> Mp3Id3Parser.parse(file, fileName, fileSize, lastModified)

                else -> {
                    if (FlacHeaderParser.isFlacStream(file)) {
                        FlacHeaderParser.parse(file, fileName, fileSize, lastModified)
                    } else if (Mp3Id3Parser.isMp3Stream(file)) {
                        Mp3Id3Parser.parse(file, fileName, fileSize, lastModified)
                    } else {
                        fallbackMetadata(file, fileName, fileSize, lastModified, ext)
                    }
                }
            }
        } catch (_: Exception) {
            fallbackMetadata(file, fileName, fileSize, lastModified, ext)
        }
    }

    private fun fallbackMetadata(file: File, fileName: String, fileSize: Long, lastModified: Long, ext: String): AudioMetadata =
        AudioMetadata(
            filePath = file.absolutePath,
            fileName = fileName,
            fileSize = fileSize,
            lastModified = lastModified,
            format = ext.uppercase(),
            title = file.nameWithoutExtension,
            tagHealth = TagHealth.NEEDS_MATCH,
        )
}
