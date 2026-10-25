package org.mutag.app.main.rename

import java.io.File
import org.mutag.core.tagger.scanner.AudioMetadata

/**
 * 文件批量重命名模式匹配与关联文件关联定位工具。
 */
object FileRenameFormatter {

    val PRESET_PATTERNS = listOf(
        "@2 - @1",
        "@1 - @2",
        "@5. @1",
        "@5. @2 - @1",
        "@4@5. @1",
        "@4@5. @2 - @1",
    )

    private val ILLEGAL_CHARS_REGEX = Regex("""[\\/:*?"<>|\r\n\t]""")

    private val ASSOCIATED_EXTENSIONS = setOf(
        "lrc",
        "txt",
        "jpg",
        "jpeg",
        "png",
        "webp",
    )

    /**
     * 根据通配模式字符串与音频元数据计算重命名后的文件名。
     *
     * @param track 音频文件元数据
     * @param pattern 重命名通配模式字符串
     * @return 格式化后的目标文件名
     */
    fun formatNewFileName(track: AudioMetadata, pattern: String): String {
        val extension = File(track.filePath).extension.let { if (it.isNotBlank()) ".$it" else "" }
        val originalBaseName = File(track.filePath).nameWithoutExtension

        val discStr = if (track.discNumber > 0) track.discNumber.toString() else ""
        val trackStr = if (track.trackNumber > 0) {
            if (track.trackNumber < 10) "0${track.trackNumber}" else track.trackNumber.toString()
        } else {
            ""
        }
        val yearStr = track.publishDate.trim().let { date ->
            val match = Regex("""\b(19\d{2}|20\d{2})\b""").find(date)
            match?.value ?: date.take(4)
        }

        var result = pattern
            .replace("@1", track.title.trim())
            .replace("@2", track.artist.trim())
            .replace("@3", track.album.trim())
            .replace("@4", discStr)
            .replace("@5", trackStr)
            .replace("@6", yearStr)
            .replace("@7", track.comment.trim())
            .replace("@8", track.albumArtist.trim())
            .replace("@0", "")

        result = result.replace(ILLEGAL_CHARS_REGEX, "_").trim()
        result = result.trimEnd('.', ' ')

        val finalBaseName = result.ifBlank { originalBaseName }
        return "$finalBaseName$extension"
    }

    /**
     * 检索音频文件同级目录下的同名关联附件文件（如 lrc、txt、封面图片）。
     *
     * @param audioFile 源音频文件
     * @return 同基名的关联文件对象列表
     */
    fun findAssociatedFiles(audioFile: File): List<File> {
        val parent = audioFile.parentFile ?: return emptyList()
        val baseName = audioFile.nameWithoutExtension
        val audioExt = audioFile.extension.lowercase()

        return parent.listFiles { file ->
            if (!file.isFile || file.name == audioFile.name) return@listFiles false
            val ext = file.extension.lowercase()
            file.nameWithoutExtension == baseName && ext in ASSOCIATED_EXTENSIONS && ext != audioExt
        }?.toList() ?: emptyList()
    }
}
