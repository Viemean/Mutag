package org.mutag.app.main

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mutag.core.tagger.cache.MetadataCache
import org.mutag.core.tagger.scanner.AudioHeaderParser
import org.mutag.model.TrackMetadata

/**
 * 批量匹配低置信度存疑项本地序列化载荷结构。
 *
 * @param filePath 音频文件绝对路径
 * @param lastModified 存疑记录生成时的文件修改时间戳
 * @param reason 存疑判定原因描述
 * @param candidateJson 候选在线曲目元数据的 JSON 序列化字符串
 */
@Serializable
internal data class PersistedUncertainItem(
    val filePath: String,
    val lastModified: Long,
    val reason: String,
    val candidateJson: String? = null,
)

/**
 * 存疑核对列表轻量文件持久化与冷启动校验恢复仓储。
 */
object UncertainMatchRepository {

    private const val FILE_NAME = "uncertain_matches.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 将当前内存中的存疑匹配项持久化保存至本地应用私有目录。
     *
     * @param context Android 上下文实例
     * @param items 待持久化的存疑匹配列表
     */
    fun save(context: Context, items: List<UncertainMatchItem>) = save(context.filesDir, items)

    /**
     * 将当前内存中的存疑匹配项持久化保存至指定私有目录。
     *
     * @param filesDir 目标存储目录
     * @param items 待持久化的存疑匹配列表
     */
    fun save(filesDir: File, items: List<UncertainMatchItem>) {
        val targetFile = File(filesDir, FILE_NAME)
        if (items.isEmpty()) {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            return
        }

        try {
            val dtoList = items.map { item ->
                PersistedUncertainItem(
                    filePath = item.localFile.filePath,
                    lastModified = item.localFile.lastModified,
                    reason = item.reason,
                    candidateJson = item.candidate?.let { json.encodeToString(it) },
                )
            }
            val content = json.encodeToString(dtoList)
            val tmpFile = File(filesDir, "$FILE_NAME.tmp")
            tmpFile.writeText(content, Charsets.UTF_8)
            if (tmpFile.renameTo(targetFile)) {
                // 原子重命名完成
            } else {
                targetFile.writeText(content, Charsets.UTF_8)
                tmpFile.delete()
            }
        } catch (_: Exception) {
            // 写入异常静默保护，防止崩溃
        }
    }

    /**
     * 从本地私有文件恢复存疑匹配项，并针对当前文件系统执行有效性核验过滤。
     *
     * @param context Android 上下文实例
     * @param cache 音频元数据本地缓存实例
     * @return 经文件存在性与时间戳校验后的有效存疑项列表
     */
    fun load(context: Context, cache: MetadataCache): List<UncertainMatchItem> = load(context.filesDir, cache)

    /**
     * 从指定私有文件恢复存疑匹配项，并针对当前文件系统执行有效性核验过滤。
     *
     * @param filesDir 目标存储目录
     * @param cache 音频元数据本地缓存实例
     * @return 经文件存在性与时间戳校验后的有效存疑项列表
     */
    fun load(filesDir: File, cache: MetadataCache): List<UncertainMatchItem> {
        val targetFile = File(filesDir, FILE_NAME)
        if (!targetFile.exists()) return emptyList()

        try {
            val text = targetFile.readText(Charsets.UTF_8)
            val dtoList = json.decodeFromString<List<PersistedUncertainItem>>(text)
            if (dtoList.isEmpty()) return emptyList()

            val validItems = mutableListOf<UncertainMatchItem>()
            var hasInvalidItem = false

            for (dto in dtoList) {
                val file = File(dto.filePath)
                // 1. 文件不存在，或已被外部/用户修改则剔除
                if (!file.exists() || file.lastModified() != dto.lastModified) {
                    hasInvalidItem = true
                    continue
                }

                // 2. 获取最新本地音频元数据
                val localAudio = cache.get(dto.filePath, file.length(), dto.lastModified)
                    ?: AudioHeaderParser.parse(file)

                // 3. 反序列化候选曲目元数据
                val candidate = dto.candidateJson?.let {
                    runCatching { json.decodeFromString<TrackMetadata>(it) }.getOrNull()
                }

                validItems.add(
                    UncertainMatchItem(
                        localFile = localAudio,
                        candidate = candidate,
                        reason = dto.reason,
                    ),
                )
            }

            // 若存在已失效项目，同步更新持久化快照
            if (hasInvalidItem) {
                save(filesDir, validItems)
            }

            return validItems
        } catch (_: Exception) {
            return emptyList()
        }
    }

    /**
     * 清空本地已保存的存疑匹配记录文件。
     *
     * @param context Android 上下文实例
     */
    fun clear(context: Context) = clear(context.filesDir)

    /**
     * 清空指定目录中保存的存疑匹配记录文件。
     *
     * @param filesDir 目标存储目录
     */
    fun clear(filesDir: File) {
        try {
            val targetFile = File(filesDir, FILE_NAME)
            if (targetFile.exists()) {
                targetFile.delete()
            }
        } catch (_: Exception) {
            // 安全删除保护
        }
    }
}
