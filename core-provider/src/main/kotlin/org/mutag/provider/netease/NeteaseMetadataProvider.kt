package org.mutag.provider.netease

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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
import org.mutag.model.SyncedLyrics
import org.mutag.model.TrackMetadata
import org.mutag.provider.api.CoverResolution
import org.mutag.provider.api.MusicMetadataProvider
import org.mutag.provider.lyric.LyricCreditsParser
import org.mutag.provider.lyric.LyricMerger

/**
 * 基于网易云音公开 API 的在线元数据提供方实现。
 *
 * @param client 网络请求客户端
 */
class NeteaseMetadataProvider(private val client: OkHttpClient = defaultClient) : MusicMetadataProvider {

    override val sourceId: String = "netease"
    override val displayName: String = "网易云音乐"

    private val albumCache = ConcurrentHashMap<String, AlbumMetadata>()
    private val acousticClient = NeteaseAcousticRecognizeClient(client)

    companion object {
        private const val BASE_URL = "https://music.163.com"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val REFERER = "https://music.163.com/"
        private const val COOKIE = "os=pc; osver=Microsoft-Windows-10-Professional-build-19042-64bit; appver=2.9.7.199837;"

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

    override suspend fun search(query: String, page: Int, pageSize: Int): Result<List<TrackMetadata>> = withContext(Dispatchers.IO) {
        runCatching {
            val offset = (page.coerceAtLeast(1) - 1) * pageSize
            val formBody = FormBody.Builder()
                .add("s", query)
                .add("type", "1")
                .add("offset", offset.toString())
                .add("limit", pageSize.toString())
                .add("total", "true")
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/api/cloudsearch/pc")
                .post(formBody)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Cookie", COOKIE)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@runCatching emptyList()
            }

            val bodyString = response.body.string()
            if (bodyString.isBlank()) return@runCatching emptyList()

            val root = json.parseToJsonElement(bodyString).jsonObject
            val songsArray = root["result"]?.jsonObject?.get("songs")?.jsonArray ?: return@runCatching emptyList()

            songsArray.mapNotNull { element ->
                val songObj = element.jsonObject
                parseTrackFromSongObject(songObj)
            }
        }
    }

    override suspend fun getTrackDetail(trackId: String): Result<TrackMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE_URL/api/song/detail/?id=$trackId&ids=%5B$trackId%5D"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Cookie", COOKIE)
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body.string()
            if (bodyString.isBlank()) {
                throw NoSuchElementException("未找到对应歌曲详情: $trackId")
            }

            val root = json.parseToJsonElement(bodyString).jsonObject
            val songs = root["songs"]?.jsonArray
            val songObj = songs?.firstOrNull()?.jsonObject
                ?: throw NoSuchElementException("歌曲详情列表为空: $trackId")

            val baseTrack = parseTrackFromSongObject(songObj)
                ?: throw IllegalStateException("解析歌曲详情失败: $trackId")

            val lyricsResult = getLyrics(
                trackId = trackId,
                songIdLong = trackId.toLongOrNull() ?: 0L,
                title = baseTrack.title,
                artist = baseTrack.artists.firstOrNull()?.name.orEmpty(),
                durationSec = (baseTrack.durationMs / 1000).toInt(),
            ).getOrNull()

            var lyricist = ""
            var composer = ""
            var arranger = ""
            var producer = ""
            if (lyricsResult != null && lyricsResult.rawLyricText.isNotBlank()) {
                val credits = LyricCreditsParser.parse(lyricsResult.rawLyricText)
                lyricist = credits.lyricist
                composer = credits.composer
                arranger = credits.arranger
                producer = credits.producer
            }

            val albumId = baseTrack.album.id
            val albumResult = if (albumId.isNotBlank()) getAlbumDetail(albumId).getOrNull() else null
            val cover = baseTrack.coverUrl.ifBlank { albumResult?.coverUrl.orEmpty() }
            val company = albumResult?.company?.ifBlank { baseTrack.company } ?: baseTrack.company
            val trackTotal = if ((albumResult?.trackCount ?: 0) > 0) albumResult!!.trackCount else baseTrack.trackTotal

            baseTrack.copy(
                company = company,
                trackTotal = trackTotal,
                coverUrl = cover,
                lyricist = lyricist,
                composer = composer,
                arranger = arranger,
                producer = producer,
                lyrics = lyricsResult,
            )
        }
    }

    override suspend fun getAlbumDetail(albumId: String): Result<AlbumMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            albumCache[albumId]?.let { return@runCatching it }

            val url = "$BASE_URL/api/album/$albumId"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Cookie", COOKIE)
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body.string()
            if (bodyString.isBlank()) {
                throw NoSuchElementException("未找到对应专辑详情: $albumId")
            }

            val root = json.parseToJsonElement(bodyString).jsonObject
            val albumObj = root["album"]?.jsonObject
                ?: throw NoSuchElementException("专辑节点为空: $albumId")

            val name = albumObj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val company = albumObj["company"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val picUrl = albumObj["picUrl"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val size = albumObj["size"]?.jsonPrimitive?.intOrNull ?: 0
            val publishTimeMs = albumObj["publishTime"]?.jsonPrimitive?.longOrNull ?: 0L
            val publishDate = formatPublishTime(publishTimeMs)

            val albumMetadata = AlbumMetadata(
                id = albumId,
                name = name,
                coverUrl = picUrl,
                publishDate = publishDate,
                trackCount = size,
                company = company,
            )

            albumCache[albumId] = albumMetadata
            albumMetadata
        }
    }

    override suspend fun getLyrics(
        trackId: String,
        songIdLong: Long,
        title: String,
        artist: String,
        durationSec: Int,
    ): Result<SyncedLyrics> = withContext(Dispatchers.IO) {
        runCatching {
            val targetId = if (trackId.isNotBlank()) trackId else songIdLong.toString()
            val url = "$BASE_URL/api/song/lyric?os=pc&id=$targetId&lv=-1&kv=-1&tv=-1"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Cookie", COOKIE)
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body.string()
            if (bodyString.isBlank()) {
                return@runCatching LyricMerger.merge("", null)
            }

            val root = json.parseToJsonElement(bodyString).jsonObject
            val lrcText = root["lrc"]?.jsonObject?.get("lyric")?.jsonPrimitive?.contentOrNull.orEmpty()
            val tlyricText = root["tlyric"]?.jsonObject?.get("lyric")?.jsonPrimitive?.contentOrNull.orEmpty()

            LyricMerger.merge(lrcText, tlyricText.ifBlank { null })
        }
    }

    override suspend fun recognizeByAcoustic(feature: AcousticFeature): Result<TrackMetadata?> = withContext(Dispatchers.IO) {
        runCatching {
            val recognized = acousticClient.recognize(feature).getOrNull() ?: return@runCatching null
            val fullDetail = getTrackDetail(recognized.trackId).getOrNull()
            fullDetail ?: recognized
        }
    }

    private fun parseTrackFromSongObject(songObj: kotlinx.serialization.json.JsonObject): TrackMetadata? {
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
        val picUrl = albumObj?.get("picUrl")?.jsonPrimitive?.contentOrNull.orEmpty().replace("http://", "https://")

        val trackNumber = songObj["no"]?.jsonPrimitive?.intOrNull ?: 0
        val cdRaw = songObj["cd"]?.jsonPrimitive?.contentOrNull
            ?: songObj["disc"]?.jsonPrimitive?.contentOrNull
            ?: ""
        val discNumber = cdRaw.trimStart('0').toIntOrNull() ?: cdRaw.toIntOrNull() ?: 1

        val publishTimeMs = songObj["publishTime"]?.jsonPrimitive?.longOrNull ?: 0L
        val publishDate = formatPublishTime(publishTimeMs)

        val albumMetadata = AlbumMetadata(
            id = albumId,
            name = albumName,
            coverUrl = picUrl,
            publishDate = publishDate,
        )

        val firstArtistName = artists.firstOrNull()?.name.orEmpty()

        return TrackMetadata(
            trackId = id,
            title = name,
            artists = artists,
            album = albumMetadata,
            albumArtist = firstArtistName,
            durationMs = durationMs,
            trackNumber = trackNumber,
            discNumber = discNumber,
            publishDate = publishDate,
            coverUrl = picUrl,
            sourceId = sourceId,
        )
    }

    override fun resolveCoverUrl(track: TrackMetadata, resolution: CoverResolution): String {
        val base = track.coverUrl.replace("http://", "https://")
        if (base.isBlank()) return ""
        val param = when (resolution) {
            CoverResolution.THUMBNAIL -> "200y200"
            CoverResolution.MEDIUM -> "500y500"
            CoverResolution.LARGE, CoverResolution.ORIGINAL_MASTER -> null
        }
        return if (param != null) {
            if (base.contains("?")) "$base&param=$param" else "$base?param=$param"
        } else {
            base
        }
    }

    private fun formatPublishTime(timestampMs: Long): String {
        if (timestampMs <= 0L) return ""
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("GMT+8")
        }
        return sdf.format(Date(timestampMs))
    }
}
