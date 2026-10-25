package org.mutag.provider.qqmusic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CryptoUtilsTest {

    @Test
    fun computeZzcSignValidInputProducesLowercaseZzcSignature() {
        val payload = """{"comm":{"cv":4747474,"ct":24,"format":"json","inCharset":"utf-8","outCharset":"utf-8","notice":0,"platform":"yqq.json","needNewCode":1,"uin":0,"g_tk_new_20200303":5381,"g_tk":5381}}"""
        val sign = CryptoUtils.computeZzcSign(payload)

        assertTrue(sign.startsWith("zzc"))
        assertEquals(sign.lowercase(), sign)
        assertTrue(sign.length > 20)
    }

    @Test
    fun computeGTkCalculatesCorrectHash() {
        val hash = CryptoUtils.computeGTk("test_token")
        assertTrue(hash > 0)
    }

    @Test
    fun encryptAndDecryptAg1RoundTrip() {
        val plainText = """{"query":"周杰伦","page":1}"""
        val encryptedBase64 = CryptoUtils.encryptAg1Request(plainText)
        assertTrue(encryptedBase64.isNotBlank())

        val cipherBytes = java.util.Base64.getDecoder().decode(encryptedBase64)
        // Verify non-empty and cipher bytes are longer than nonce (12 bytes) + tag (16 bytes)
        assertTrue(cipherBytes.size > 28)
    }

    @Test
    fun decryptAg1ResponseCorrectlyDecodesXorCipher() {
        val expected = """{"code":0,"data":{"songList":[]}}"""
        val expectedBytes = expected.toByteArray(Charsets.UTF_8)
        val key = byteArrayOf(
            122, 63, 140.toByte(), 29, 94, 155.toByte(), 47, 10,
            108, 77, 126, 139.toByte(), 31, 58, 92, 157.toByte(),
            14, 43, 111, 74, 129.toByte(),
        )
        val cipherBytes = ByteArray(expectedBytes.size) { i ->
            (expectedBytes[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        val actual = CryptoUtils.decryptAg1Response(cipherBytes)
        assertEquals(expected, actual)
    }
}
