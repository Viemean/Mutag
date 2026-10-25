package org.mutag.provider.qqmusic

import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.mutag.model.AcousticFeature
import org.mutag.model.AlbumMetadata
import org.mutag.model.ArtistMetadata
import org.mutag.model.TrackMetadata

/**
 * 声学指纹云端识别解析结果实体。
 *
 * @param success 是否识别成功
 * @param title 匹配歌曲标题
 * @param artist 匹配艺术家
 * @param album 匹配专辑名称
 * @param songMid 曲目 MID 标识
 * @param songId 曲目数值 ID
 * @param offsetSeconds 片段在原曲中的时间偏移秒数
 * @param trackMetadata 解析转换的统一曲目元数据
 * @param errorMessage 失败错误描述
 */
data class AcousticRecognizeResult(
    val success: Boolean,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val songMid: String = "",
    val songId: Long = 0L,
    val offsetSeconds: Double = 0.0,
    val trackMetadata: TrackMetadata? = null,
    val errorMessage: String = "",
)

/**
 * 听歌识曲云端识别 HTTP 协议客户端。
 *
 * @param client 网络请求客户端
 */
class AcousticRecognizeClient(private val client: OkHttpClient = defaultClient) {
    companion object {
        private const val PROTOCOL_VERSION = 201506
        private const val SOURCE = "spr_xiaomi_speed_android"
        private const val SIGN_SALT = "spr_xiaomi_speed_androida45a1b"
        private const val AES_KEY = "spr_8a2cdeab7b81"
        private const val ENDPOINT_URL = "http://c.y.qq.com/youtu/humming/search"
        private val MEDIA_TYPE_FORM = "application/x-www-form-urlencoded".toMediaType()

        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }

        private fun md5Hex(str: String): String {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(str.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                sb.append(String.format("%02x", b.toInt() and 0xFF))
            }
            return sb.toString()
        }
    }

    suspend fun search(feature: AcousticFeature, sessionId: Long = System.currentTimeMillis()): AcousticRecognizeResult =
        withContext(Dispatchers.IO) {
            if (feature.data.isEmpty()) {
                return@withContext AcousticRecognizeResult(
                    success = false,
                    errorMessage = "特征数据为空",
                )
            }

            try {
                val timestamp = System.currentTimeMillis()
                val fpType = feature.featureType + 1
                val signStr = "$SIGN_SALT$timestamp"
                val veriStr = md5Hex(signStr)

                val header = String.format(
                    Locale.US,
                    "v=%d&source=%s&time=%d&veri_str=%s&cmd=1&info=%.1f,%d,10306&type=0&session_id=%d&feature_type=%d&confidence=%.1f\u0000",
                    PROTOCOL_VERSION,
                    SOURCE,
                    timestamp,
                    veriStr,
                    feature.duration,
                    feature.data.size,
                    sessionId,
                    fpType,
                    feature.confidence,
                )
                val headerBytes = header.toByteArray(Charsets.UTF_8)

                val rawPayload = ByteArray(headerBytes.size + feature.data.size)
                System.arraycopy(headerBytes, 0, rawPayload, 0, headerBytes.size)
                System.arraycopy(feature.data, 0, rawPayload, headerBytes.size, feature.data.size)

                val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
                val keySpec = SecretKeySpec(AES_KEY.toByteArray(Charsets.US_ASCII), "AES")
                cipher.init(Cipher.ENCRYPT_MODE, keySpec)
                val encryptedBody = cipher.doFinal(rawPayload)

                val requestUrl = "$ENDPOINT_URL?sessionid=$sessionId&recognizetype=1&fpType=$fpType"
                val request = Request.Builder()
                    .url(requestUrl)
                    .addHeader("User-Agent", "MusicRecognition 34}(android 10)")
                    .addHeader("AppId", "85")
                    .addHeader("Cookie", "uin=; ct=3003; cv=10306; recognizetype=1")
                    .post(encryptedBody.toRequestBody(MEDIA_TYPE_FORM))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext AcousticRecognizeResult(
                            success = false,
                            errorMessage = "HTTP 请求失败: ${response.code}",
                        )
                    }

                    val bodyStr = response.body.string()
                    parseResponse(bodyStr)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AcousticRecognizeResult(
                    success = false,
                    errorMessage = "网络异常: ${e.message ?: "未知"}",
                )
            }
        }

    /**
     * 解析听歌识曲服务端返回的 JSON 响应。
     *
     * @param jsonString 服务端响应原始 JSON 文本
     * @return 解析结构化的识别结果对象
     */
    fun parseResponse(jsonString: String): AcousticRecognizeResult {
        return try {
            val root = Json.parseToJsonElement(jsonString).jsonObject
            val ret = root["ret"]?.jsonPrimitive?.intOrNull ?: -1
            if (ret != 0) {
                return AcousticRecognizeResult(
                    success = false,
                    errorMessage = "服务器未命中歌曲特征",
                )
            }

            var offset = 0.0
            root["results"]?.jsonArray?.firstOrNull()?.jsonObject?.let { firstRes ->
                firstRes["offset"]?.jsonPrimitive?.doubleOrNull?.let { offset = it }
            }

            val songItem = root["songlist"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: return AcousticRecognizeResult(
                    success = false,
                    offsetSeconds = offset,
                    errorMessage = "返回数据中未包含歌曲信息",
                )

            val id = songItem["id"]?.jsonPrimitive?.intOrNull?.toLong() ?: 0L
            val mid = songItem["mid"]?.jsonPrimitive?.content ?: ""
            val name = songItem["name"]?.jsonPrimitive?.content ?: ""
            val interval = songItem["interval"]?.jsonPrimitive?.intOrNull ?: 0

            val singers = mutableListOf<ArtistMetadata>()
            songItem["singer"]?.jsonArray?.forEach { singerElement ->
                val singerObj = singerElement.jsonObject
                val sId = singerObj["id"]?.jsonPrimitive?.intOrNull?.toString() ?: ""
                val sMid = singerObj["mid"]?.jsonPrimitive?.content ?: ""
                val sName = singerObj["name"]?.jsonPrimitive?.content ?: ""
                singers.add(
                    ArtistMetadata(
                        id = sMid.ifEmpty { sId },
                        name = sName,
                        avatarUrl = QqMusicUrlBuilder.buildSingerAvatarUrl(sMid),
                    ),
                )
            }

            val albumObj = songItem["album"]?.jsonObject
            val albumId = albumObj?.get("id")?.jsonPrimitive?.intOrNull?.toString() ?: ""
            val albumMid = albumObj?.get("mid")?.jsonPrimitive?.content ?: ""
            val albumName = albumObj?.get("name")?.jsonPrimitive?.content ?: ""
            val album = AlbumMetadata(
                id = albumMid.ifEmpty { albumId },
                name = albumName,
                coverUrl = QqMusicUrlBuilder.buildAlbumCoverUrl(albumMid),
            )

            val track = TrackMetadata(
                trackId = mid,
                title = name,
                artists = singers,
                album = album,
                albumArtist = singers.firstOrNull()?.name ?: "",
                durationMs = interval * 1000L,
                coverUrl = album.coverUrl,
                sourceId = "qqmusic",
            )

            AcousticRecognizeResult(
                success = true,
                title = name,
                artist = track.artistName,
                album = albumName,
                songMid = mid,
                songId = id,
                offsetSeconds = offset,
                trackMetadata = track,
                errorMessage = "",
            )
        } catch (e: Exception) {
            AcousticRecognizeResult(
                success = false,
                errorMessage = "解析响应失败: ${e.message}",
            )
        }
    }
}
