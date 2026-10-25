package org.mutag.core.tagger.recognizer

import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import org.mutag.model.AcousticFeature

/**
 * 基于短时傅里叶变换与频域峰值检测的声学指纹特征提取器。
 */
object AcousticFingerprintExtractor {
    const val SAMPLE_RATE = 8000
    const val WINDOW_SIZE = 1024
    const val HOP_SIZE = 128
    const val FFT_SIZE = 1024
    const val CHANNEL_COUNT = 4
    const val TIME_WINDOW = 5
    const val FREQ_WINDOW = 20
    const val MAX_PEAKS_PER_FRAME = 5
    const val MAG_THRESHOLD = 20000.0f
    const val MAG_THRESHOLD_SQ = MAG_THRESHOLD * MAG_THRESHOLD

    private val _hW =
        FloatArray(WINDOW_SIZE) { n ->
            (0.54 - 0.46 * cos(2.0 * Math.PI * n / (WINDOW_SIZE - 1))).toFloat()
        }

    private val _brS: IntArray =
        run {
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
            val wIm = kotlin.math.sin(angle)
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

    private val _mbw = intArrayOf(1, 2, 3, 4, 5, 6, 7, 9)
    private val _mic = intArrayOf(36, 18, 12, 9, 7, 6, 5, 4)
    private val _mpb = intArrayOf(0, 0, 0, 0, 1, 0, 1, 0)
    private val _mxd = intArrayOf(1, 3, 4, 15, 31, 63, 127, 511)

    /**
     * 时频峰值地标点数据结构。
     *
     * @param time 帧时间索引
     * @param freq 频点索引
     */
    data class Landmark(val time: Int, val freq: Int)

    /**
     * 将 16 kHz 采样率 PCM 信号通过隔点平均下采样至 8 kHz。
     *
     * @param pcm16k 16 kHz 采样率短整型样本序列
     * @return 8 kHz 采样率短整型样本序列
     */
    fun downsample16kTo8k(pcm16k: ShortArray): ShortArray {
        val targetLen = pcm16k.size / 2
        val pcm8k = ShortArray(targetLen)
        for (i in 0 until targetLen) {
            val sum = pcm16k[i * 2].toInt() + pcm16k[i * 2 + 1].toInt()
            pcm8k[i] = (sum / 2).toShort()
        }
        return pcm8k
    }

    private class IntArrayList(capacity: Int = 256) {
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

    /**
     * 从 8000 Hz 单声道 PCM 音频样本提取指纹特征载荷。
     *
     * @param samples 8000 Hz 单声道 PCM 短整型样本序列
     * @return 编码生成的声学指纹特征对象，样本量不足时返回 null
     */
    fun extract(samples: ShortArray): AcousticFeature? {
        if (samples.size < WINDOW_SIZE + HOP_SIZE * (TIME_WINDOW * CHANNEL_COUNT)) {
            return null
        }

        val totalSamples = samples.size
        val numFrames = (totalSamples - WINDOW_SIZE) / HOP_SIZE + 1
        if (numFrames <= TIME_WINDOW * CHANNEL_COUNT) {
            return null
        }

        val halfFft = FFT_SIZE / 2
        val binsPerFrame = halfFft + 1
        val spectrogram = FloatArray(numFrames * binsPerFrame)
        val fftRe = FloatArray(FFT_SIZE)
        val fftIm = FloatArray(FFT_SIZE)

        for (i in 0 until numFrames) {
            val stStart = i * HOP_SIZE
            for (n in 0 until WINDOW_SIZE) {
                fftRe[n] = samples[stStart + n] * _hW[n]
                fftIm[n] = 0.0f
            }
            for (n in WINDOW_SIZE until FFT_SIZE) {
                fftRe[n] = 0.0f
                fftIm[n] = 0.0f
            }

            _cFft(fftRe, fftIm)

            val frameOffset = i * binsPerFrame
            for (f in 0..halfFft) {
                val re = fftRe[f]
                val im = fftIm[f]
                spectrogram[frameOffset + f] = re * re + im * im
            }
        }

        val out = ByteArrayOutputStream(1024)
        val topFreqs = IntArray(MAX_PEAKS_PER_FRAME)
        val topVals = FloatArray(MAX_PEAKS_PER_FRAME)

        val chTimes = IntArrayList(256)
        val chFreqs = IntArrayList(256)

        for (ch in 0 until CHANNEL_COUNT) {
            chTimes.size = 0
            chFreqs.size = 0

            val numT = (numFrames - ch + CHANNEL_COUNT - 1) / CHANNEL_COUNT
            val targetEnd = max(0, numT - TIME_WINDOW)

            for (t in 0 until targetEnd) {
                var candCount = 0
                val frameIdx = t * CHANNEL_COUNT + ch
                val rowOffset = frameIdx * binsPerFrame

                for (f in 3..510) {
                    val v = spectrogram[rowOffset + f]
                    if (v < MAG_THRESHOLD_SQ) continue
                    if (candCount == MAX_PEAKS_PER_FRAME && v <= topVals[MAX_PEAKS_PER_FRAME - 1]) continue

                    val tStart = max(0, t - TIME_WINDOW)
                    val tEnd = t + TIME_WINDOW
                    val fStart = max(3, f - FREQ_WINDOW)
                    val fEnd = min(510, f + FREQ_WINDOW)

                    var isLocalMax = true
                    for (tPrime in tStart..tEnd) {
                        val primeOffset = (tPrime * CHANNEL_COUNT + ch) * binsPerFrame
                        for (fPrime in fStart..fEnd) {
                            if (spectrogram[primeOffset + fPrime] > v) {
                                isLocalMax = false
                                break
                            }
                        }
                        if (!isLocalMax) break
                    }

                    if (isLocalMax) {
                        if (candCount < MAX_PEAKS_PER_FRAME) {
                            var insertPos = candCount
                            while (insertPos > 0 && topVals[insertPos - 1] < v) {
                                topVals[insertPos] = topVals[insertPos - 1]
                                topFreqs[insertPos] = topFreqs[insertPos - 1]
                                insertPos--
                            }
                            topVals[insertPos] = v
                            topFreqs[insertPos] = f
                            candCount++
                        } else {
                            var insertPos = MAX_PEAKS_PER_FRAME - 1
                            while (insertPos > 0 && topVals[insertPos - 1] < v) {
                                topVals[insertPos] = topVals[insertPos - 1]
                                topFreqs[insertPos] = topFreqs[insertPos - 1]
                                insertPos--
                            }
                            topVals[insertPos] = v
                            topFreqs[insertPos] = f
                        }
                    }
                }

                for (k in 0 until candCount) {
                    chTimes.add(t)
                    chFreqs.add(topFreqs[k])
                }
            }

            packPart1Direct(chTimes, out)
            packPart2Direct(chFreqs, out)
        }

        val featBytes = out.toByteArray()
        if (featBytes.isEmpty()) return null
        val duration = samples.size.toFloat() / SAMPLE_RATE
        return AcousticFeature(featBytes, duration, 0, 0.0f)
    }

    private fun packPart1Direct(times: IntArrayList, out: ByteArrayOutputStream) {
        val n = times.size
        if (n == 0) {
            out.write(0)
            out.write(0)
            out.write(0)
            out.write(0)
            return
        }

        var blockCount = 0
        var currIdx = 0
        while (currIdx < n) {
            var chosenMode = 7
            for (m in 0 until 8) {
                val cnt = _mic[m]
                val maxVal = _mxd[m]
                var valid = true
                for (k in 0 until cnt) {
                    val idx2 = currIdx + 1 + k
                    val diff = if (idx2 < n) times[idx2] - times[idx2 - 1] else 0
                    if (diff > maxVal) {
                        valid = false
                        break
                    }
                }
                if (valid) {
                    chosenMode = m
                    break
                }
            }
            blockCount++
            currIdx += 1 + _mic[chosenMode]
        }

        val bw = _bWr()
        bw._wB(n, 16)
        bw._wB(blockCount, 16)

        currIdx = 0
        while (currIdx < n) {
            val t0 = times[currIdx]
            var chosenMode = 7
            for (m in 0 until 8) {
                val cnt = _mic[m]
                val maxVal = _mxd[m]
                var valid = true
                for (k in 0 until cnt) {
                    val idx2 = currIdx + 1 + k
                    val diff = if (idx2 < n) times[idx2] - times[idx2 - 1] else 0
                    if (diff > maxVal) {
                        valid = false
                        break
                    }
                }
                if (valid) {
                    chosenMode = m
                    break
                }
            }

            bw._wB(t0 and 0x1FF, 9)
            bw._wB(chosenMode and 7, 3)
            val w = _mbw[chosenMode]
            val blockCnt = _mic[chosenMode]
            for (k in 0 until blockCnt) {
                val idx2 = currIdx + 1 + k
                val diff = if (idx2 < n) times[idx2] - times[idx2 - 1] else 0
                bw._wB(diff, w)
            }
            if (_mpb[chosenMode] > 0) {
                bw._wB(0, 1)
            }

            currIdx += 1 + blockCnt
        }

        bw.writeTo(out)
    }

    private fun packPart2Direct(freqs: IntArrayList, out: ByteArrayOutputStream) {
        val bw = _bWr()
        bw._wB(freqs.size, 16)
        for (i in 0 until freqs.size) {
            bw._wB(freqs[i] and 0x1FF, 9)
        }
        bw.writeTo(out)
    }

    private fun _cFft(re: FloatArray, im: FloatArray) {
        var idx = 0
        while (idx < _brS.size) {
            val i = _brS[idx]
            val j = _brS[idx + 1]
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

    private class _bWr(initialCapacity: Int = 256) {
        private var buffer = ByteArray(initialCapacity)
        private var byteCount = 0
        private var bitBuf = 0L
        private var bitsInBuf = 0

        fun _wB(value: Int, numBits: Int) {
            bitBuf = (bitBuf shl numBits) or (value.toLong() and ((1L shl numBits) - 1))
            bitsInBuf += numBits
            while (bitsInBuf >= 8) {
                bitsInBuf -= 8
                if (byteCount >= buffer.size) {
                    buffer = buffer.copyOf(buffer.size * 2)
                }
                buffer[byteCount++] = ((bitBuf ushr bitsInBuf) and 0xFF).toByte()
            }
        }

        fun writeTo(out: ByteArrayOutputStream) {
            if (bitsInBuf > 0) {
                val padBits = 8 - bitsInBuf
                val finalByte = ((bitBuf shl padBits) and 0xFF).toByte()
                if (byteCount >= buffer.size) {
                    buffer = buffer.copyOf(buffer.size * 2)
                }
                buffer[byteCount++] = finalByte
                bitsInBuf = 0
            }
            out.write(buffer, 0, byteCount)
        }
    }
}
