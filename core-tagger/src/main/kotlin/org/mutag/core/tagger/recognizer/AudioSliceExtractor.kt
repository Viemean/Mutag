package org.mutag.core.tagger.recognizer

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mutag.model.AcousticFeature
import org.mutag.model.AcousticFeatureType

/**
 * 音频片段声学特征提取结果。
 *
 * @param feature 提取的声学特征载荷
 * @param startSeconds 片段在原始音频中的起始时间偏移（秒）
 */
data class AudioSliceResult(val feature: AcousticFeature, val startSeconds: Double)

/**
 * 基于 MediaExtractor 与 MediaCodec 的音频切片重采样与声学特征提取器。
 */
object AudioSliceExtractor {
    const val TARGET_SAMPLE_RATE = 8000
    const val TARGET_DURATION_SEC = 8

    /**
     * 音频切片时间窗口规划配置。
     *
     * @param startOffsetSec 切片起始偏移秒数
     * @param durationSec 切片截取持续秒数
     */
    data class SlicePlan(val startOffsetSec: Double, val durationSec: Double)

    /**
     * 根据音频总时长与推荐副歌时间计算切片截取时间窗口。
     *
     * @param durationMs 音频总时长（毫秒）
     * @param preferredStartMs 推荐切片起始时间戳（毫秒，如副歌起点）
     * @return 规划的切片起始偏移与截取时长配置
     */
    fun calculateSlicePlan(durationMs: Long, preferredStartMs: Long = 0L): SlicePlan {
        val totalSec = durationMs / 1000.0

        // 1. 若指定了有效副歌时间戳且位于安全时长内，优先在副歌起点截取
        if (preferredStartMs > 0L) {
            val prefSec = preferredStartMs / 1000.0
            if (prefSec + TARGET_DURATION_SEC <= totalSec) {
                return SlicePlan(startOffsetSec = prefSec, durationSec = TARGET_DURATION_SEC.toDouble())
            }
        }

        // 2. 依据时长动态选取人声黄金区间，避开前奏纯乐器伴奏段
        val startSec = when {
            totalSec >= 90.0 -> minOf(totalSec * 0.3, 60.0)

            // 长音频截取 30% 进度（上限 60 秒）
            totalSec >= 45.0 -> totalSec * 0.35

            // 45~90 秒音频截取 35% 进度
            totalSec >= 20.0 -> 10.0

            // 20~45 秒音频避开前 10 秒
            else -> 0.0 // 短音频从头开始
        }

        val duration = if (totalSec - startSec < TARGET_DURATION_SEC) {
            maxOf(0.0, totalSec - startSec)
        } else {
            TARGET_DURATION_SEC.toDouble()
        }
        return SlicePlan(startOffsetSec = startSec, durationSec = duration)
    }

    /**
     * 截取音频目标区间并提取指纹特征。
     *
     * @param audioFile 输入音频文件
     * @param startOffsetSec 可选的自定义切片起始偏移（秒）
     * @param preferredStartMs 推荐副歌时间戳（毫秒）
     * @return 成功提取返回声学特征对象，失败返回 null
     */
    suspend fun extractSliceFeature(audioFile: File, startOffsetSec: Double? = null, preferredStartMs: Long = 0L): AcousticFeature? =
        extractSliceWithTime(audioFile, startOffsetSec, preferredStartMs)?.feature

    /**
     * 依据音源提供方类型调度提取对应特征规范的音频切片。
     *
     * @param audioFile 输入音频文件
     * @param sourceId 音源标识（"qqmusic" 或 "netease"）
     * @param startOffsetSec 可选的自定义切片起始偏移（秒）
     * @param preferredStartMs 推荐副歌时间戳（毫秒）
     * @return 包含起始秒数与声学特征的切片结果对象
     */
    suspend fun extractSliceForSource(
        audioFile: File,
        sourceId: String,
        startOffsetSec: Double? = null,
        preferredStartMs: Long = 0L,
    ): AudioSliceResult? {
        val type = if (sourceId == "netease") AcousticFeatureType.NETEASE else AcousticFeatureType.QQ_MUSIC
        return extractSliceWithTime(audioFile, startOffsetSec, preferredStartMs, type)
    }

    /**
     * 解码音频切片并提取包含时间戳信息的声学特征。
     *
     * @param audioFile 输入音频文件
     * @param startOffsetSec 可选的自定义切片起始偏移（秒）
     * @param preferredStartMs 推荐副歌时间戳（毫秒）
     * @param featureType 目标声学特征类型（默认 QQ 音乐）
     * @return 包含起始秒数与声学特征的切片结果对象，提取失败返回 null
     */
    suspend fun extractSliceWithTime(
        audioFile: File,
        startOffsetSec: Double? = null,
        preferredStartMs: Long = 0L,
        featureType: Int = AcousticFeatureType.QQ_MUSIC,
    ): AudioSliceResult? = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || !audioFile.canRead() || audioFile.length() < 16 * 1024L) {
            return@withContext null
        }
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(audioFile.absolutePath)

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) return@withContext null
            extractor.selectTrack(audioTrackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val srcSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            val plan = calculateSlicePlan(durationMs = durationUs / 1000L, preferredStartMs = preferredStartMs)
            val targetStartSec = startOffsetSec ?: plan.startOffsetSec

            extractor.seekTo((targetStartSec * 1_000_000.0).toLong(), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val actualStartSec = if (extractor.sampleTime >= 0L) extractor.sampleTime / 1_000_000.0 else targetStartSec

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val targetDurationSec = if (featureType == AcousticFeatureType.NETEASE) 6 else TARGET_DURATION_SEC
            val maxSamplesToDecode = (targetDurationSec * srcSampleRate * channelCount)
            val rawPcmList = ArrayList<Short>(maxSamplesToDecode)
            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false
            val timeoutUs = 10_000L

            while (!isEos && rawPcmList.size < maxSamplesToDecode) {
                val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            val pts = extractor.sampleTime
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, pts, 0)
                            extractor.advance()
                        }
                    }
                }

                var outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                while (outputIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                    }
                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                            val shortBuf = outputBuffer.asShortBuffer()
                            val shortCount = shortBuf.remaining()
                            val needed = maxSamplesToDecode - rawPcmList.size
                            val toRead = minOf(shortCount, needed)
                            val temp = ShortArray(toRead)
                            shortBuf.get(temp, 0, toRead)
                            for (s in temp) rawPcmList.add(s)
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (rawPcmList.size >= maxSamplesToDecode || isEos) break
                    outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
                }
            }

            if (rawPcmList.isEmpty()) return@withContext null

            val monoSamples = if (channelCount <= 1) {
                ShortArray(rawPcmList.size) { rawPcmList[it] }
            } else {
                val frames = rawPcmList.size / channelCount
                val mono = ShortArray(frames)
                for (i in 0 until frames) {
                    var sum = 0
                    val base = i * channelCount
                    for (ch in 0 until channelCount) {
                        sum += rawPcmList[base + ch]
                    }
                    mono[i] = (sum / channelCount).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                mono
            }

            val resampled8k = resampleMonoTo8k(monoSamples, srcSampleRate)
            val feature = if (featureType == AcousticFeatureType.NETEASE) {
                NeteaseFingerprintExtractor.extract(resampled8k)
            } else {
                AcousticFingerprintExtractor.extract(resampled8k)
            } ?: return@withContext null
            AudioSliceResult(feature, actualStartSec)
        } catch (e: Exception) {
            android.util.Log.w("AudioSliceExtractor", "Audio slice decode or feature extraction failed for ${audioFile.name}", e)
            null
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {
                android.util.Log.d("AudioSliceExtractor", "Failed to release codec", e)
            }
            try {
                extractor?.release()
            } catch (e: Exception) {
                android.util.Log.d("AudioSliceExtractor", "Failed to release extractor", e)
            }
        }
    }

    private fun resampleMonoTo8k(monoSamples: ShortArray, srcSampleRate: Int): ShortArray {
        if (srcSampleRate == TARGET_SAMPLE_RATE) return monoSamples
        val ratio = srcSampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
        val targetLen = (monoSamples.size / ratio).toInt()
        if (targetLen <= 0) return ShortArray(0)

        val output = ShortArray(targetLen)
        for (i in 0 until targetLen) {
            val srcPos = i * ratio
            val idx = srcPos.toInt()
            val frac = srcPos - idx
            if (idx + 1 < monoSamples.size) {
                val s1 = monoSamples[idx].toDouble()
                val s2 = monoSamples[idx + 1].toDouble()
                output[i] = (s1 + frac * (s2 - s1)).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            } else if (idx < monoSamples.size) {
                output[i] = monoSamples[idx]
            }
        }
        return output
    }

    /**
     * 将原始 PCM 字节流重采样并混音为 8000 Hz 单声道 16 位样本序列。
     *
     * @param pcmData 原始 PCM 字节流
     * @param srcSampleRate 源采样率
     * @param channels 源声道数
     * @param bitsPerSample 位深（默认 16 位）
     * @return 转换后的 8000 Hz 单声道短整型数组
     */
    fun resamplePcmTo8kMono(pcmData: ByteArray, srcSampleRate: Int, channels: Int, bitsPerSample: Int = 16): ShortArray {
        if (pcmData.isEmpty() || srcSampleRate <= 0 || channels <= 0) return ShortArray(0)

        val bytesPerSample = bitsPerSample / 8
        val frameSize = bytesPerSample * channels
        val totalFrames = pcmData.size / frameSize
        if (totalFrames == 0) return ShortArray(0)

        val monoSamples = ShortArray(totalFrames)

        for (i in 0 until totalFrames) {
            val offset = i * frameSize
            var sumSample = 0
            for (ch in 0 until channels) {
                val sampleOffset = offset + ch * bytesPerSample
                val sampleVal = if (bytesPerSample == 2) {
                    (pcmData[sampleOffset].toInt() and 0xFF) or (pcmData[sampleOffset + 1].toInt() shl 8)
                } else {
                    pcmData[sampleOffset].toInt() shl 8
                }
                sumSample += sampleVal.toShort()
            }
            monoSamples[i] = (sumSample / channels).toShort()
        }

        if (srcSampleRate == TARGET_SAMPLE_RATE) {
            return monoSamples
        }

        // 线性插值重采样至 8000 Hz
        val targetLength = ((monoSamples.size.toDouble() * TARGET_SAMPLE_RATE) / srcSampleRate).toInt()
        val resampled = ShortArray(targetLength)
        val ratio = srcSampleRate.toDouble() / TARGET_SAMPLE_RATE

        for (i in 0 until targetLength) {
            val srcPos = i * ratio
            val index0 = srcPos.toInt()
            val index1 = minOf(index0 + 1, monoSamples.size - 1)
            val frac = srcPos - index0

            val v0 = monoSamples[index0].toDouble()
            val v1 = monoSamples[index1].toDouble()
            resampled[i] = (v0 + frac * (v1 - v0)).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return resampled
    }
}
