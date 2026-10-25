package org.mutag.core.tagger.scanner

import java.io.FileInputStream
import java.nio.charset.Charset

/**
 * 音频流字节读取、编码识别与格式化通用工具。
 */
internal object AudioParserUtils {

    /**
     * 从输入流中精确读取指定字节数。
     *
     * @param fis 输入文件流
     * @param length 期望读取的目标字节长度
     * @return 读取得到的字节数组
     */
    fun readFully(fis: FileInputStream, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val data = ByteArray(length)
        var readLen = 0
        while (readLen < length) {
            val r = fis.read(data, readLen, length - readLen)
            if (r <= 0) break
            readLen += r
        }
        return if (readLen == length) data else data.copyOf(readLen)
    }

    /**
     * 在输入流中向前跳过指定字节数。
     *
     * @param fis 输入文件流
     * @param length 跳过的字节数
     */
    fun seekSkip(fis: FileInputStream, length: Long) {
        if (length <= 0L) return
        try {
            fis.channel.position(fis.channel.position() + length)
        } catch (_: Exception) {
            var rem = length
            while (rem > 0L) {
                val s = fis.skip(rem)
                if (s <= 0L) {
                    if (fis.read() == -1) break
                    rem -= 1
                } else {
                    rem -= s
                }
            }
        }
    }

    /**
     * 依据 ID3 编码标识字节映射字符集。
     *
     * @param encodingByte ID3 编码标识（0: ISO-8859-1, 1: UTF-16, 2: UTF-16BE, 3: UTF-8）
     * @return 对应的 Charset 字符集
     */
    fun charsetForEncoding(encodingByte: Int): Charset = when (encodingByte) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.UTF_8
    }

    /**
     * 将毫秒时长格式化为 mm:ss 字符串。
     *
     * @param durationMs 毫秒时间数值
     * @return 格式化后的分秒字符串
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "未知"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    /**
     * 将字节大小格式化为 MB 及字节说明文本。
     *
     * @param sizeBytes 文件字节长度
     * @return 格式化文件体积字符串
     */
    fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes <= 0L) return "0 B"
        val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
        return "%.2f MB (%s 字节)".format(mb, java.text.NumberFormat.getIntegerInstance().format(sizeBytes))
    }
}
