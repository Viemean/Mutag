package org.mutag.core.tagger.scanner

/**
 * 音频硬件技术规格与流参数。
 *
 * @param format 格式标识
 * @param sampleRateHz 采样率数值（单位：Hz）
 * @param sampleRateDisplay 采样率格式化显示串
 * @param bitDepth 量化位深（如 16, 24）
 * @param bitDepthDisplay 位深显示串
 * @param channels 声道数量
 * @param channelsDisplay 声道模式显示串
 * @param bitrateKbps 码率数值（单位：kbps）
 * @param bitrateDisplay 码率显示串
 * @param fileSize 文件字节大小
 * @param fileSizeDisplay 格式化文件体积串
 * @param qualityLevel 音质级别描述（如 Hi-Res, SQ 无损, HQ）
 * @param durationMs 播放总时长（毫秒）
 * @param durationDisplay 格式化时长分秒串
 * @param tagVersion 标签规范版本（如 ID3v2.3, Vorbis Comment）
 * @param filePath 物理文件绝对路径
 */
data class AudioTechSpec(
    val format: String = "",
    val sampleRateHz: Int = 0,
    val sampleRateDisplay: String = "",
    val bitDepth: Int = 0,
    val bitDepthDisplay: String = "",
    val channels: Int = 0,
    val channelsDisplay: String = "",
    val bitrateKbps: Int = 0,
    val bitrateDisplay: String = "",
    val fileSize: Long = 0L,
    val fileSizeDisplay: String = "",
    val qualityLevel: String = "",
    val durationMs: Long = 0L,
    val durationDisplay: String = "",
    val tagVersion: String = "",
    val filePath: String = "",
)
