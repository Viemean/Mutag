package org.mutag.provider.netease

import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.Base64
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mutag.model.AcousticFeature
import org.mutag.model.AcousticFeatureType

class NeteaseAcousticRecognizeClientTest {

    private fun createMockClient(handler: (String, String, okhttp3.Headers) -> String): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val postBody = runCatching {
                val buffer = okio.Buffer()
                request.body?.writeTo(buffer)
                buffer.readUtf8()
            }.getOrDefault("")

            val responseJson = handler(url, postBody, request.headers)
            Response.Builder()
                .code(200)
                .message("OK")
                .protocol(Protocol.HTTP_1_1)
                .request(request)
                .body(responseJson.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    @Test
    fun recognizeSubmitsValidEncryptedPayloadAndParsesResponse() = runBlocking {
        var capturedOrigin = ""
        var capturedAlgorithmCode = ""
        var capturedRawdata = ""

        val mockResponseJson = """
            {
              "code": 200,
              "data": {
                "result": [
                  {
                    "song": {
                      "id": 1827600686,
                      "name": "夜曲",
                      "artists": [
                        { "id": 6452, "name": "周杰伦" }
                      ],
                      "album": {
                        "id": 124386766,
                        "name": "十一月的萧邦",
                        "picUrl": "https://p1.music.126.net/dummy_cover.jpg"
                      },
                      "duration": 226000
                    },
                    "startTime": 15000
                  }
                ]
              }
            }
        """.trimIndent()

        val mockClient = createMockClient { _, postBody, headers ->
            capturedOrigin = headers["Origin"].orEmpty()
            val params = postBody.split("&").associate { pair ->
                val kv = pair.split("=", limit = 2)
                val k = URLDecoder.decode(kv[0], "UTF-8")
                val v = if (kv.size > 1) URLDecoder.decode(kv[1], "UTF-8") else ""
                k to v
            }
            capturedAlgorithmCode = params["algorithmCode"].orEmpty()
            capturedRawdata = params["rawdata"].orEmpty()
            mockResponseJson
        }

        val dummyData = "hyai_1.2.0_client_1.0.0_dummy_binary_payload".toByteArray(Charsets.US_ASCII)
        val feature = AcousticFeature(
            data = dummyData,
            duration = 6.0f,
            featureType = AcousticFeatureType.NETEASE,
        )

        val client = NeteaseAcousticRecognizeClient(mockClient)
        val result = client.recognize(feature).getOrNull()

        assertEquals("chrome-extension://pgphbbekcgpfaekhcbjamjjkegcclhhd", capturedOrigin)
        assertEquals("shazam_v2", capturedAlgorithmCode)
        assertTrue(capturedRawdata.isNotBlank())

        // 解密并解压密文校验协议对称性
        val encrypted = Base64.getDecoder().decode(capturedRawdata)
        val keySpec = SecretKeySpec("4B97221F27F02907".toByteArray(Charsets.US_ASCII), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        val decryptedCompressed = cipher.doFinal(encrypted)

        val inflater = Inflater()
        inflater.setInput(decryptedCompressed)
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(512)
        while (!inflater.finished()) {
            val count = inflater.inflate(buf)
            if (count <= 0) break
            baos.write(buf, 0, count)
        }
        inflater.end()
        val restoredData = baos.toByteArray()
        assertTrue(restoredData.contentEquals(dummyData))

        assertNotNull(result)
        assertEquals("1827600686", result!!.trackId)
        assertEquals("夜曲", result.title)
        assertEquals("周杰伦", result.artistName)
        assertEquals("十一月的萧邦", result.album.name)
        assertEquals("netease", result.sourceId)
    }
}
