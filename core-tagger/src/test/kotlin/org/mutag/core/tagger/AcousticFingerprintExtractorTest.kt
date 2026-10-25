package org.mutag.core.tagger

import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mutag.core.tagger.recognizer.AcousticFingerprintExtractor
import org.mutag.core.tagger.recognizer.AudioSliceExtractor

class AcousticFingerprintExtractorTest {

    @Test
    fun testDownsample16kTo8k() {
        val pcm16k = ShortArray(16000) { 1000 }
        val pcm8k = AcousticFingerprintExtractor.downsample16kTo8k(pcm16k)
        assertEquals(8000, pcm8k.size)
        assertEquals(1000, pcm8k[0].toInt())
    }

    @Test
    fun testExtractShortSamplesReturnsNull() {
        assertNull(AcousticFingerprintExtractor.extract(ShortArray(0)))
        assertNull(AcousticFingerprintExtractor.extract(ShortArray(1000)))
    }

    @Test
    fun testExtractSyntheticPcmProducesValidFeature() {
        val sampleRate = 8000
        val durationSec = 3
        val totalSamples = sampleRate * durationSec
        val pcm8k = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val s1 = sin(2.0 * Math.PI * 440.0 * t)
            val s2 = sin(2.0 * Math.PI * 880.0 * t)
            val s3 = sin(2.0 * Math.PI * 1200.0 * t)
            val sampleVal = ((s1 + s2 + s3) / 3.0 * 25000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcm8k[i] = sampleVal.toShort()
        }

        val feature = AcousticFingerprintExtractor.extract(pcm8k)
        assertNotNull(feature)
        feature?.let {
            assertTrue(it.data.isNotEmpty())
            assertEquals(3.0f, it.duration, 0.01f)
            assertTrue(it.data.size >= 16)
        }
    }

    @Test
    fun testAudioSlicePlanCalculation() {
        val planLong = AudioSliceExtractor.calculateSlicePlan(240_000L) // 4 min -> 黄金人声区 60s
        assertEquals(60.0, planLong.startOffsetSec)
        assertEquals(8.0, planLong.durationSec)

        val planMedium = AudioSliceExtractor.calculateSlicePlan(20_000L) // 20s -> 避开前奏 10s
        assertEquals(10.0, planMedium.startOffsetSec)
        assertEquals(8.0, planMedium.durationSec)

        val planShort = AudioSliceExtractor.calculateSlicePlan(6_000L) // 6s -> 从头截取
        assertEquals(0.0, planShort.startOffsetSec)
        assertEquals(6.0, planShort.durationSec)

        val planWithChorus = AudioSliceExtractor.calculateSlicePlan(240_000L, preferredStartMs = 45_000L)
        assertEquals(45.0, planWithChorus.startOffsetSec)
        assertEquals(8.0, planWithChorus.durationSec)
    }
}
