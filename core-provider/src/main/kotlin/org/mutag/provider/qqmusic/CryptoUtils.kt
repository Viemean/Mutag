package org.mutag.provider.qqmusic

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * QQ 音乐网关通信签名计算与对称加解密工具。
 */
object CryptoUtils {
    private val AG1_REQUEST_KEY =
        byteArrayOf(
            189.toByte(),
            48,
            95,
            16,
            208.toByte(),
            255.toByte(),
            116,
            182.toByte(),
            239.toByte(),
            84,
            218.toByte(),
            184.toByte(),
            53,
            181.toByte(),
            225.toByte(),
            207.toByte(),
        )

    private val AG1_RESPONSE_KEY =
        byteArrayOf(
            122,
            63,
            140.toByte(),
            29,
            94,
            155.toByte(),
            47,
            10,
            108,
            77,
            126,
            139.toByte(),
            31,
            58,
            92,
            157.toByte(),
            14,
            43,
            111,
            74,
            129.toByte(),
        )

    private val PART1_INDEXES = intArrayOf(23, 14, 6, 36, 16, 40, 7, 19)
    private val PART2_INDEXES = intArrayOf(16, 1, 32, 12, 19, 27, 8, 5)
    private val SCRAMBLE_VALUES =
        byteArrayOf(
            89,
            39,
            179.toByte(),
            150.toByte(),
            218.toByte(),
            82,
            58,
            252.toByte(),
            177.toByte(),
            52,
            186.toByte(),
            123,
            120,
            64,
            242.toByte(),
            133.toByte(),
            143.toByte(),
            161.toByte(),
            121,
            179.toByte(),
        )

    /**
     * 计算搜索与网关接口所需的 zzc 校验签名。
     *
     * @param text 待签名的请求体明文字符串
     * @return 编码生成的十六进制签名串
     */
    fun computeZzcSign(text: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
        val hashBytes = sha1.digest(text.toByteArray(Charsets.UTF_8))
        val hex = hashBytes.joinToString("") { "%02X".format(it) }

        val p1 =
            CharArray(PART1_INDEXES.size) { i ->
                val idx = PART1_INDEXES[i]
                if (idx < hex.length) hex[idx] else '0'
            }

        val p2 =
            CharArray(PART2_INDEXES.size) { i ->
                val idx = PART2_INDEXES[i]
                if (idx < hex.length) hex[idx] else '0'
            }

        val part3 =
            ByteArray(SCRAMBLE_VALUES.size) { i ->
                val b =
                    if (i * 2 + 1 < hex.length) {
                        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                    } else {
                        0.toByte()
                    }
                (SCRAMBLE_VALUES[i].toInt() xor b.toInt()).toByte()
            }

        val b64Part =
            Base64
                .getEncoder()
                .encodeToString(part3)
                .replace("/", "")
                .replace("+", "")
                .replace("=", "")

        return "zzc${String(p1)}$b64Part${String(p2)}".lowercase()
    }

    /**
     * 基于哈希种子计算 Cookie 鉴权 g_tk 数值。
     *
     * @param key 鉴权 Cookie 原始文本
     * @param seed 计算起始哈希种子（默认 5381）
     * @return 32 位整型哈希校验值
     */
    fun computeGTk(key: String, seed: Long = 5381L): Long {
        if (key.isBlank()) return seed
        var hash = seed
        for (ch in key) {
            hash = (hash shl 5) + hash + ch.code.toLong()
        }
        return hash and 0x7fffffffL
    }

    /**
     * 使用 AES-GCM-128 算法加密 ag1 专有网关请求体。
     *
     * @param jsonPayload 明文 JSON 请求负载
     * @return 包含 12 字节 Nonce 与密文的 Base64 编码字符串
     */
    fun encryptAg1Request(jsonPayload: String): String {
        val plainBytes = jsonPayload.toByteArray(Charsets.UTF_8)
        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)

        val keySpec = SecretKeySpec(AG1_REQUEST_KEY, "AES")
        val gcmSpec = GCMParameterSpec(128, nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val cipherBytes = cipher.doFinal(plainBytes)

        val finalData = ByteArray(nonce.size + cipherBytes.size)
        System.arraycopy(nonce, 0, finalData, 0, nonce.size)
        System.arraycopy(cipherBytes, 0, finalData, nonce.size, cipherBytes.size)

        return Base64.getEncoder().encodeToString(finalData)
    }

    /**
     * 使用循环异或密钥解密 ag1 专有网关响应字节流。
     *
     * @param responseBytes 响应原始密文字节数组
     * @return 解密还原的 UTF-8 明文字符串
     */
    fun decryptAg1Response(responseBytes: ByteArray): String {
        val decrypted =
            ByteArray(responseBytes.size) { i ->
                (responseBytes[i].toInt() xor AG1_RESPONSE_KEY[i % AG1_RESPONSE_KEY.size].toInt()).toByte()
            }
        return String(decrypted, Charsets.UTF_8)
    }
}
