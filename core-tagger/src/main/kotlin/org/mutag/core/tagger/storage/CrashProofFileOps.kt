package org.mutag.core.tagger.storage

import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 防崩溃原子文件更新操作工具。
 */
object CrashProofFileOps {

    /**
     * 构造临时镜像写入文件句柄。
     *
     * @param originalFile 原始音频文件
     * @return 同级目录下的临时镜像文件对象
     */
    fun getTempMirrorFile(originalFile: File): File = File(originalFile.parentFile, "${originalFile.name}.mutag_tmp")

    /**
     * 校验临时镜像文件的音频头部魔数有效性。
     *
     * @param tempFile 临时镜像文件
     * @param expectedFormat 期望的音频格式名称（flac 或 mp3）
     * @return 头部魔数匹配返回 true，校验失败返回 false
     */
    fun validateHeader(tempFile: File, expectedFormat: String): Boolean {
        if (!tempFile.exists() || tempFile.length() < 4L) return false
        return try {
            FileInputStream(tempFile).use { fis ->
                when (expectedFormat.lowercase()) {
                    "flac" -> {
                        val magic = ByteArray(4)
                        if (fis.read(magic) != 4) return false
                        magic[0] == 'f'.code.toByte() &&
                            magic[1] == 'L'.code.toByte() &&
                            magic[2] == 'a'.code.toByte() &&
                            magic[3] == 'C'.code.toByte()
                    }

                    "mp3" -> {
                        val header = ByteArray(3)
                        if (fis.read(header) != 3) return false
                        (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) ||
                            (header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0)
                    }

                    else -> true
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("CrashProofFileOps", "validateHeader failed for ${tempFile.name}", e)
            false
        }
    }

    /**
     * 执行临时镜像文件向目标文件的原子移动与替换。
     *
     * @param tempFile 已写入校验完成的临时文件
     * @param targetFile 待替换的目标源文件
     * @return 替换成功返回 true，发生异常或文件不存在返回 false
     */
    fun atomicReplace(tempFile: File, targetFile: File): Boolean {
        if (!tempFile.exists() || tempFile.length() <= 0L) {
            tempFile.delete()
            return false
        }

        return try {
            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
                true
            } catch (_: Exception) {
                try {
                    Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                    true
                } catch (_: Exception) {
                    if (tempFile.renameTo(targetFile)) {
                        true
                    } else {
                        Files.copy(
                            tempFile.toPath(),
                            targetFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                        tempFile.delete()
                        true
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CrashProofFileOps", "Atomic replace failed for target file: ${targetFile.name}", e)
            tempFile.delete()
            false
        }
    }

    /**
     * 执行带容灾保护与头部校验的事务性文件写入流程。
     *
     * @param targetFile 待写入替换的目标音频文件
     * @param expectedFormat 期望的音频格式名称（flac 或 mp3）
     * @param block 针对临时镜像文件的实际写入回调逻辑
     * @return 写入及原子替换全流程成功返回 true，写入或校验失败返回 false
     */
    inline fun executeWithCrashProof(targetFile: File, expectedFormat: String, block: (tempFile: File) -> Boolean): Boolean {
        val tempFile = getTempMirrorFile(targetFile)
        try {
            val writeSuccess = block(tempFile)
            if (!writeSuccess) {
                tempFile.delete()
                return false
            }

            if (!validateHeader(tempFile, expectedFormat)) {
                android.util.Log.w("CrashProofFileOps", "Header validation failed for format $expectedFormat on: ${tempFile.name}")
                tempFile.delete()
                return false
            }

            return atomicReplace(tempFile, targetFile)
        } catch (e: Exception) {
            android.util.Log.e("CrashProofFileOps", "executeWithCrashProof failed for ${targetFile.name}", e)
            tempFile.delete()
            return false
        }
    }
}
