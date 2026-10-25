package org.mutag.core.tagger.recognizer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import org.mutag.model.AcousticFeature
import org.mutag.model.AcousticFeatureType

/**
 * 网易云音乐听歌识曲原生音频指纹特征提取器。
 * 遵循官方 WebAssembly (afp.wasm) 算法规范。
 */
object NeteaseFingerprintExtractor {
    const val SAMPLE_RATE = 8000
    const val WINDOW_SIZE = 2048
    const val HOP_SIZE = 160
    const val FFT_SIZE = 2048
    const val MIN_BIN = 25
    const val MAX_BIN = 1024
    const val BAND_BINS = MAX_BIN - MIN_BIN // 999 频点

    private val CLIENT_VERSION = "hyai_1.2.0_client_1.0.0".toByteArray(Charsets.US_ASCII)
    private val MAGIC_FPVER = "FPVER".toByteArray(Charsets.US_ASCII)
    private val MAGIC_PEAK = "Peak".toByteArray(Charsets.US_ASCII)

    private val HAMMING_WINDOW = FloatArray(WINDOW_SIZE) { n ->
        (0.54 - 0.46 * cos(2.0 * Math.PI * n / (WINDOW_SIZE - 1))).toFloat()
    }

    private val BIT_REVERSAL_SWAPS: IntArray = run {
        val swaps = mutableListOf<Int>()
        var j = 0
        for (i in 0 until FFT_SIZE - 1) {
            if (i < j) {
                swaps.add(i)
                swaps.add(j)
            }
            var k = FFT_SIZE shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }
        swaps.toIntArray()
    }

    private val twiddlesRe: Array<FloatArray>
    private val twiddlesIm: Array<FloatArray>

    init {
        var stages = 0
        var lTmp = 1
        while (lTmp < FFT_SIZE) {
            stages++
            lTmp = lTmp shl 1
        }
        twiddlesRe = Array(stages) { FloatArray(0) }
        twiddlesIm = Array(stages) { FloatArray(0) }

        var stage = 0
        var l = 1
        while (l < FFT_SIZE) {
            val re = FloatArray(l)
            val im = FloatArray(l)
            val angle = -Math.PI / l
            val wRe = cos(angle)
            val wIm = sin(angle)
            var curRe = 1.0
            var curIm = 0.0
            for (k in 0 until l) {
                re[k] = curRe.toFloat()
                im[k] = curIm.toFloat()
                val nextRe = curRe * wRe - curIm * wIm
                val nextIm = curRe * wIm + curIm * wRe
                curRe = nextRe
                curIm = nextIm
            }
            twiddlesRe[stage] = re
            twiddlesIm[stage] = im
            stage++
            l = l shl 1
        }
    }

    private data class Peak(val freqBin: Int, val timeFrame: Int, val amp: Float)

    /**
     * 从 8000Hz 采样率的单声道 16-bit PCM 序列提取网易云指纹特征载荷。
     *
     * @param pcm 8000Hz 单声道 PCM 短整型样本
     * @return 序列化的声学指纹载荷对象，样本不足时返回 null
     */
    fun extract(pcm: ShortArray): AcousticFeature? {
        if (pcm.size < WINDOW_SIZE + HOP_SIZE * 10) return null

        val durationSec = pcm.size.toFloat() / SAMPLE_RATE
        val nFrames = (pcm.size - WINDOW_SIZE) / HOP_SIZE + 1
        if (nFrames < 10) return null

        // 1. STFT 短时傅里叶变换与对数能量矩阵构造
        val matrix = FloatArray(BAND_BINS * nFrames)
        val fftRe = FloatArray(FFT_SIZE)
        val fftIm = FloatArray(FFT_SIZE)

        for (t in 0 until nFrames) {
            val frameStart = t * HOP_SIZE
            for (n in 0 until WINDOW_SIZE) {
                // 16-bit 整数线性归一化到 [-1.0, 1.0] 浮点区间
                val sampleFloat = pcm[frameStart + n].toFloat() / 32768.0f
                fftRe[n] = sampleFloat * HAMMING_WINDOW[n]
                fftIm[n] = 0.0f
            }
            for (n in WINDOW_SIZE until FFT_SIZE) {
                fftRe[n] = 0.0f
                fftIm[n] = 0.0f
            }

            executeFft(fftRe, fftIm)

            for (f in 0 until BAND_BINS) {
                val binIdx = f + MIN_BIN
                val re = fftRe[binIdx]
                val im = fftIm[binIdx]
                val mag = sqrt(re * re + im * im)
                val protectedMag = max(mag, 1.11920929e-6f)
                val logAmp = ln(protectedMag)
                matrix[f * nFrames + t] = logAmp
            }
        }

        // 2. 阶段 1: 全局去均值
        var sum = 0.0
        val totalElements = BAND_BINS * nFrames
        for (i in 0 until totalElements) {
            sum += matrix[i]
        }
        val meanVal = (sum / totalElements).toFloat()
        for (i in 0 until totalElements) {
            matrix[i] -= meanVal
        }

        // 3. 阶段 2: 3x3 空间局部极大值初筛
        val candF = IntArrayList(1024)
        val candT = IntArrayList(1024)

        for (f in 1 until BAND_BINS - 1) {
            val rowOffset = f * nFrames
            val rowPrev = (f - 1) * nFrames
            val rowNext = (f + 1) * nFrames

            for (t in 1 until nFrames - 1) {
                val v = matrix[rowOffset + t]
                if (v <= 0.0f) continue

                if (v >= matrix[rowPrev + (t - 1)] &&
                    v >= matrix[rowPrev + t] &&
                    v >= matrix[rowPrev + (t + 1)] &&
                    v >= matrix[rowOffset + (t - 1)] &&
                    v >= matrix[rowOffset + (t + 1)] &&
                    v >= matrix[rowNext + (t - 1)] &&
                    v >= matrix[rowNext + t] &&
                    v >= matrix[rowNext + (t + 1)]
                ) {
                    candF.add(f)
                    candT.add(t)
                }
            }
        }

        if (candF.size == 0) return null

        // 4. 阶段 3: 2D 积分图 LocalMean 过滤
        val stride = nFrames + 1
        val integral = FloatArray((BAND_BINS + 1) * stride)

        for (f in 0 until BAND_BINS) {
            val srcRow = f * nFrames
            val intRowCur = (f + 1) * stride
            val intRowPrev = f * stride

            for (t in 0 until nFrames) {
                val v = matrix[srcRow + t]
                integral[intRowCur + (t + 1)] = v +
                    integral[intRowCur + t] +
                    integral[intRowPrev + (t + 1)] -
                    integral[intRowPrev + t]
            }
        }

        val survivedF = IntArrayList(candF.size)
        val survivedT = IntArrayList(candF.size)

        for (i in 0 until candF.size) {
            val f = candF[i]
            val t = candT[i]
            val f0 = max(0, f - 10)
            val f1 = min(BAND_BINS, f + 11)
            val t0 = max(0, t - 5)
            val t1 = min(nFrames, t + 6)

            val area = (f1 - f0) * (t1 - t0)
            val rectSum = integral[f1 * stride + t1] -
                integral[f0 * stride + t1] -
                integral[f1 * stride + t0] +
                integral[f0 * stride + t0]
            val localAvg = rectSum / area

            val amp = matrix[f * nFrames + t]
            if (localAvg > 2.0f || (amp - localAvg) > 1.0f) {
                survivedF.add(f)
                survivedT.add(t)
            }
        }

        if (survivedF.size == 0) return null

        // 5. 阶段 4: 2D 矩形非极大值抑制 (RtgMax 61x17, 带 Early-Exit 快速剪枝)
        val selectedPeaks = mutableListOf<Peak>()

        for (i in 0 until survivedF.size) {
            val f = survivedF[i]
            val t = survivedT[i]
            val amp = matrix[f * nFrames + t]

            val f0 = max(0, f - 30)
            val f1 = min(BAND_BINS, f + 31)
            val t0 = max(0, t - 8)
            val t1 = min(nFrames, t + 9)

            var isStrictMax = true
            for (rf in f0 until f1) {
                val rowOffset = rf * nFrames
                for (rt in t0 until t1) {
                    if (matrix[rowOffset + rt] > amp) {
                        isStrictMax = false
                        break
                    }
                }
                if (!isStrictMax) break
            }

            if (isStrictMax) {
                selectedPeaks.add(Peak(freqBin = f + MIN_BIN, timeFrame = t, amp = amp))
            }
        }

        if (selectedPeaks.isEmpty()) return null

        // 容器排布规范: 按时间帧升序、频点升序排列
        selectedPeaks.sortWith(compareBy({ it.timeFrame }, { it.freqBin }))

        // 6. 二进制序列化 (Little-Endian)
        val payloadSize = 4 + CLIENT_VERSION.size + 4 + 4 + 4 + 5 + 4 + CLIENT_VERSION.size + 4 + 4 + selectedPeaks.size * 12
        val bb = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN)

        bb.putInt(CLIENT_VERSION.size)
        bb.put(CLIENT_VERSION)
        bb.putInt(0)
        bb.putInt(0)
        bb.putFloat(durationSec)
        bb.put(MAGIC_FPVER)
        bb.putInt(CLIENT_VERSION.size)
        bb.put(CLIENT_VERSION)
        bb.put(MAGIC_PEAK)
        bb.putInt(selectedPeaks.size)

        for (peak in selectedPeaks) {
            bb.putInt(peak.freqBin)
            bb.putInt(peak.timeFrame)
            bb.putFloat(peak.amp)
        }

        return AcousticFeature(
            data = bb.array(),
            duration = durationSec,
            featureType = AcousticFeatureType.NETEASE,
            confidence = 0.0f,
        )
    }

    private fun executeFft(re: FloatArray, im: FloatArray) {
        var idx = 0
        while (idx < BIT_REVERSAL_SWAPS.size) {
            val i = BIT_REVERSAL_SWAPS[idx]
            val j = BIT_REVERSAL_SWAPS[idx + 1]
            val tRe = re[i]
            re[i] = re[j]
            re[j] = tRe
            val tIm = im[i]
            im[i] = im[j]
            im[j] = tIm
            idx += 2
        }

        var stage = 0
        var l = 1
        while (l < FFT_SIZE) {
            val stageTwiddlesRe = twiddlesRe[stage]
            val stageTwiddlesIm = twiddlesIm[stage]
            stage++
            val step = l shl 1

            var p0 = 0
            while (p0 < FFT_SIZE) {
                val q = p0 + l
                val tRe = re[q]
                val tIm = im[q]
                re[q] = re[p0] - tRe
                im[q] = im[p0] - tIm
                re[p0] += tRe
                im[p0] += tIm
                p0 += step
            }

            for (k in 1 until l) {
                val curRe = stageTwiddlesRe[k]
                val curIm = stageTwiddlesIm[k]
                var p = k
                while (p < FFT_SIZE) {
                    val q = p + l
                    val tRe = curRe * re[q] - curIm * im[q]
                    val tIm = curRe * im[q] + curIm * re[q]

                    re[q] = re[p] - tRe
                    im[q] = im[p] - tIm
                    re[p] += tRe
                    im[p] += tIm
                    p += step
                }
            }
            l = l shl 1
        }
    }

    private class IntArrayList(capacity: Int = 512) {
        var data = IntArray(capacity)
        var size = 0

        fun add(value: Int) {
            if (size == data.size) {
                data = data.copyOf(data.size * 2)
            }
            data[size++] = value
        }

        operator fun get(index: Int): Int = data[index]
    }
}
