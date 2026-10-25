package org.mutag.core.tagger.recognizer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mutag.model.AcousticFeatureType

class NeteaseFingerprintExtractorTest {

    @Test
    fun extractWithInsufficientSamplesReturnsNull() {
        val shortPcm = ShortArray(1000)
        assertNull(NeteaseFingerprintExtractor.extract(shortPcm))
    }

    @Test
    fun extractSynthesizedSineWaveGeneratesValidBinaryContainer() {
        val durationSec = 6.0f
        val sampleRate = 8000
        val totalSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(totalSamples)

        // 合成 440Hz 与 1000Hz 正弦信号
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val s1 = sin(2.0 * Math.PI * 440.0 * t)
            val s2 = sin(2.0 * Math.PI * 1000.0 * t)
            val mixed = ((s1 + s2) * 0.45 * 32767.0).toInt().coerceIn(-32768, 32767)
            pcm[i] = mixed.toShort()
        }

        val feature = NeteaseFingerprintExtractor.extract(pcm)
        assertNotNull(feature)
        assertEquals(AcousticFeatureType.NETEASE, feature!!.featureType)
        assertEquals(durationSec, feature.duration)

        val bytes = feature.data
        assertTrue(bytes.size > 80)

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val verLen1 = bb.getInt()
        assertEquals(23, verLen1)

        val verBytes1 = ByteArray(verLen1)
        bb.get(verBytes1)
        assertEquals("hyai_1.2.0_client_1.0.0", String(verBytes1, Charsets.US_ASCII))

        assertEquals(0, bb.getInt())
        assertEquals(0, bb.getInt())
        assertEquals(durationSec, bb.getFloat())

        val fpver = ByteArray(5)
        bb.get(fpver)
        assertEquals("FPVER", String(fpver, Charsets.US_ASCII))

        val verLen2 = bb.getInt()
        assertEquals(23, verLen2)
        val verBytes2 = ByteArray(verLen2)
        bb.get(verBytes2)
        assertEquals("hyai_1.2.0_client_1.0.0", String(verBytes2, Charsets.US_ASCII))

        val peakMagic = ByteArray(4)
        bb.get(peakMagic)
        assertEquals("Peak", String(peakMagic, Charsets.US_ASCII))

        val peakCount = bb.getInt()
        assertTrue(peakCount > 0)
        assertEquals(bb.position() + peakCount * 12, bytes.size)
    }
}
