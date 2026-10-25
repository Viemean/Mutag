package org.mutag.provider.netease

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.mutag.model.AcousticFeature
import org.mutag.model.AlbumMetadata
import org.mutag.model.ArtistMetadata
import org.mutag.model.TrackMetadata

/**
 * 网易云音乐听歌识曲云端识别 HTTP 协议客户端。
 *
 * @param client 网络请求客户端
 */
class NeteaseAcousticRecognizeClient(private val client: OkHttpClient = defaultClient) {
    companion object {
        private const val MATCH_URL = "https://interface.music.163.com/api/music/audio/match"
        private const val CHROME_EXT_ORIGIN = "chrome-extension://pgphbbekcgpfaekhcbjamjjkegcclhhd"
        private const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private val AES_KEY_BYTES = "4B97221F27F02907".toByteArray(Charsets.US_ASCII)

        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    /**
     * 将网易云特征载荷通过 Deflate 压缩、AES 加密后提交至云端进行匹配。
     *
     * @param feature 网易云音频指纹特征载荷
     * @return 识别出的首选歌曲元数据，无匹配或失败返回 null
     */
    suspend fun recognize(feature: AcousticFeature): Result<TrackMetadata?> = withContext(Dispatchers.IO) {
        runCatching {
            if (feature.data.isEmpty()) return@runCatching null

            // 1. zlib deflate 压缩 (level 6)
            val compressed = compressDeflate(feature.data, 6)

            // 2. AES-128-ECB 加密 (PKCS5/PKCS7 填充)
            val keySpec = SecretKeySpec(AES_KEY_BYTES, "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            val encrypted = cipher.doFinal(compressed)

            // 3. Base64 编码
            val rawdataB64 = Base64.getEncoder().encodeToString(encrypted)

            val durationSec = if (feature.duration > 0f) feature.duration.toInt().toString() else "6"
            val sessionId = UUID.randomUUID().toString()

            val formBody = FormBody.Builder()
                .add("sessionId", sessionId)
                .add("algorithmCode", "shazam_v2")
                .add("duration", durationSec)
                .add("rawdata", rawdataB64)
                .add("times", "2")
                .add("decrypt", "1")
                .build()

            val request = Request.Builder()
                .url(MATCH_URL)
                .addHeader("Origin", CHROME_EXT_ORIGIN)
                .addHeader("User-Agent", USER_AGENT)
                .post(formBody)
                .build()

            val responseBody = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body.string()
            }

            if (responseBody.isBlank()) return@runCatching null

            parseMatchResponse(responseBody)
        }
    }

    private fun parseMatchResponse(jsonStr: String): TrackMetadata? {
        val root = json.parseToJsonElement(jsonStr).jsonObject
        val code = root["code"]?.jsonPrimitive?.intOrNull ?: return null
        if (code != 200) return null

        val dataObj = root["data"]?.jsonObject ?: return null
        val resultArray = dataObj["result"]?.jsonArray ?: return null
        if (resultArray.isEmpty()) return null

        val firstMatch = resultArray[0].jsonObject
        val songObj = firstMatch["song"]?.jsonObject ?: return null

        val id = songObj["id"]?.jsonPrimitive?.contentOrNull
            ?: songObj["id"]?.jsonPrimitive?.longOrNull?.toString()
            ?: return null

        val name = songObj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val durationMs = songObj["dt"]?.jsonPrimitive?.longOrNull
            ?: songObj["duration"]?.jsonPrimitive?.longOrNull
            ?: 0L

        val artistsArray = songObj["ar"]?.jsonArray ?: songObj["artists"]?.jsonArray
        val artists = artistsArray?.mapNotNull { item ->
            val aObj = item.jsonObject
            val aId = aObj["id"]?.jsonPrimitive?.contentOrNull
                ?: aObj["id"]?.jsonPrimitive?.longOrNull?.toString()
                ?: ""
            val aName = aObj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (aName.isNotBlank()) ArtistMetadata(id = aId, name = aName) else null
        } ?: emptyList()

        val albumObj = songObj["al"]?.jsonObject ?: songObj["album"]?.jsonObject
        val albumId = albumObj?.get("id")?.jsonPrimitive?.contentOrNull
            ?: albumObj?.get("id")?.jsonPrimitive?.longOrNull?.toString()
            ?: ""
        val albumName = albumObj?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
        val picUrl = albumObj?.get("picUrl")?.jsonPrimitive?.contentOrNull.orEmpty()

        val albumMetadata = AlbumMetadata(
            id = albumId,
            name = albumName,
            coverUrl = picUrl,
        )

        return TrackMetadata(
            trackId = id,
            title = name,
            artists = artists,
            album = albumMetadata,
            albumArtist = artists.firstOrNull()?.name.orEmpty(),
            durationMs = durationMs,
            coverUrl = picUrl,
            sourceId = "netease",
        )
    }

    private fun compressDeflate(input: ByteArray, level: Int): ByteArray {
        val deflater = Deflater(level)
        deflater.setInput(input)
        deflater.finish()
        val outputStream = ByteArrayOutputStream(input.size)
        val buffer = ByteArray(1024)
        try {
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                outputStream.write(buffer, 0, count)
            }
        } finally {
            deflater.end()
        }
        return outputStream.toByteArray()
    }
}
