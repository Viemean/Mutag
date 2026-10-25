package org.mutag.core.tagger.writer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

/**
 * 音频标签回写顶层分发器。
 */
object AudioMetadataWriter {
    private const val MAX_COVER_IMAGE_SIZE = (2.5 * 1024 * 1024).toInt() // 2.5 MB

    /**
     * 将编辑后的元数据载荷安全回写至物理音频文件。
     *
     * @param audioFile 目标音频文件
     * @param payload 待写入的标签载荷实体
     * @return 写入替换成功返回 true，失败返回 false
     */
    fun writeMetadata(audioFile: File, payload: MetadataPayload): Boolean {
        if (!audioFile.exists() || !audioFile.isFile || audioFile.length() <= 0L) {
            return false
        }

        val safePayload = prepareSafePayload(payload)
        val ext = audioFile.extension.lowercase()

        return when {
            ext == "flac" || isFlacStream(audioFile) -> {
                FlacMetadataWriter.write(audioFile, safePayload)
            }

            ext == "mp3" || isMp3Stream(audioFile) -> {
                Mp3Id3v23Writer.write(audioFile, safePayload)
            }

            else -> false
        }
    }

    private fun prepareSafePayload(payload: MetadataPayload): MetadataPayload {
        val cover = payload.coverBytes ?: return payload
        if (cover.size <= MAX_COVER_IMAGE_SIZE) return payload

        val compressed = compressCoverSafely(cover)
        return payload.copy(coverBytes = compressed, coverMime = "image/jpeg")
    }

    private fun compressCoverSafely(coverBytes: ByteArray): ByteArray {
        // 1. Try Android BitmapFactory if available
        try {
            val bitmapClass = Class.forName("android.graphics.BitmapFactory")
            val decodeMethod = bitmapClass.getMethod(
                "decodeByteArray",
                ByteArray::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            val bitmap = decodeMethod.invoke(null, coverBytes, 0, coverBytes.size)

            if (bitmap != null) {
                val compressFormatEnum = Class.forName("android.graphics.Bitmap\$CompressFormat")
                val jpegFormat = compressFormatEnum.getField("JPEG").get(null)
                val compressMethod = bitmap.javaClass.getMethod(
                    "compress",
                    compressFormatEnum,
                    Int::class.javaPrimitiveType,
                    java.io.OutputStream::class.java,
                )

                val baos = ByteArrayOutputStream()
                val success = compressMethod.invoke(bitmap, jpegFormat, 92, baos) as Boolean
                if (success && baos.size() > 0) {
                    return baos.toByteArray()
                }
            }
        } catch (_: Exception) {}

        // 2. Try standard JVM ImageIO fallback
        try {
            val imageIoClass = Class.forName("javax.imageio.ImageIO")
            val readMethod = imageIoClass.getMethod("read", java.io.InputStream::class.java)
            val bufferedImage = readMethod.invoke(null, ByteArrayInputStream(coverBytes))

            if (bufferedImage != null) {
                val getImageWritersMethod = imageIoClass.getMethod("getImageWritersByFormatName", String::class.java)

                @Suppress("UNCHECKED_CAST")
                val writers = getImageWritersMethod.invoke(null, "jpeg") as Iterator<Any>
                if (writers.hasNext()) {
                    val writer = writers.next()
                    val writeParamMethod = writer.javaClass.getMethod("getDefaultWriteParam")
                    val writeParam = writeParamMethod.invoke(writer)

                    val setCompressionModeMethod = writeParam.javaClass.getMethod("setCompressionMode", Int::class.javaPrimitiveType)
                    setCompressionModeMethod.invoke(writeParam, 2) // MODE_EXPLICIT

                    val setCompressionQualityMethod = writeParam.javaClass.getMethod(
                        "setCompressionQuality",
                        Float::class.javaPrimitiveType,
                    )
                    setCompressionQualityMethod.invoke(writeParam, 0.92f)

                    val baos = ByteArrayOutputStream()
                    val createOutputStreamMethod = imageIoClass.getMethod("createImageOutputStream", Any::class.java)
                    val ios = createOutputStreamMethod.invoke(null, baos)

                    val setOutputMethod = writer.javaClass.getMethod("setOutput", Any::class.java)
                    setOutputMethod.invoke(writer, ios)

                    val iiImageClass = Class.forName("javax.imageio.IIOImage")
                    val iioConstructor = iiImageClass.getConstructor(
                        Class.forName("java.awt.image.RenderedImage"),
                        Class.forName("java.util.List"),
                        Class.forName("javax.imageio.metadata.IIOMetadata"),
                    )
                    val iioImage = iioConstructor.newInstance(bufferedImage, null, null)

                    val writeMethod = writer.javaClass.getMethod(
                        "write",
                        Class.forName("javax.imageio.metadata.IIOMetadata"),
                        iiImageClass,
                        writeParam.javaClass,
                    )
                    writeMethod.invoke(writer, null, iioImage, writeParam)

                    val disposeMethod = writer.javaClass.getMethod("dispose")
                    disposeMethod.invoke(writer)

                    val closeMethod = ios.javaClass.getMethod("close")
                    closeMethod.invoke(ios)

                    if (baos.size() > 0) {
                        return baos.toByteArray()
                    }
                }
            }
        } catch (_: Exception) {}

        return coverBytes
    }

    private fun isFlacStream(file: File): Boolean = try {
        FileInputStream(file).use { fis ->
            val magic = ByteArray(4)
            if (fis.read(magic) == 4) {
                magic[0] == 'f'.code.toByte() &&
                    magic[1] == 'L'.code.toByte() &&
                    magic[2] == 'a'.code.toByte() &&
                    magic[3] == 'C'.code.toByte()
            } else {
                false
            }
        }
    } catch (_: Exception) {
        false
    }

    private fun isMp3Stream(file: File): Boolean = try {
        FileInputStream(file).use { fis ->
            val header = ByteArray(3)
            if (fis.read(header) == 3) {
                (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) ||
                    (header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0)
            } else {
                false
            }
        }
    } catch (_: Exception) {
        false
    }
}
