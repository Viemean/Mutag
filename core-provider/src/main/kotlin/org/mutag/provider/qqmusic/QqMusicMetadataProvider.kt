package org.mutag.provider.qqmusic

import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
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
import org.mutag.model.ReplayGainInfo
import org.mutag.model.SyncedLyrics
import org.mutag.model.TrackMetadata
import org.mutag.provider.api.CoverResolution
import org.mutag.provider.api.MusicMetadataProvider
import org.mutag.provider.lyric.LyricCreditsParser
import org.mutag.provider.lyric.LyricMerger

/**
 * 基于 QQ 音乐官方网关 API 与听歌识曲协议的在线元数据提供商实现。
 *
 * @param client 网络请求客户端
 */
class QqMusicMetadataProvider(private val client: OkHttpClient = defaultClient) : MusicMetadataProvider {

    override val sourceId: String = "qqmusic"
    override val displayName: String = "QQ 音乐"

    private val acousticClient = AcousticRecognizeClient(client)
    private val albumDetailCache = java.util.concurrent.ConcurrentHashMap<String, AlbumMetadata>()

    companion object {
        private const val STANDARD_GATEWAY = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        private const val AG1_GATEWAY = "https://u6.y.qq.com/cgi-bin/musics.fcg"
        private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()

        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun search(query: String, page: Int, pageSize: Int): Result<List<TrackMetadata>> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonPayload = """
                {
                  "music.search.SearchCgiService": {
                    "module": "music.search.SearchCgiService",
                    "method": "DoSearchForQQMusicDesktop",
                    "param": {
                      "query": ${Json.encodeToString(kotlinx.serialization.serializer(), query)},
                      "page_num": $page,
                      "num_per_page": $pageSize,
                      "search_type": 0
                    }
                  }
                }
            """.trimIndent()

            val timestamp = System.currentTimeMillis()
            val sign = CryptoUtils.computeZzcSign(jsonPayload)
            val encryptedBase64 = CryptoUtils.encryptAg1Request(jsonPayload)
            val url = "$AG1_GATEWAY?_=$timestamp&encoding=ag-1&sign=$sign"

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; MuTag) AppleWebKit/537.36")
                .addHeader("Referer", "https://y.qq.com/")
                .post(encryptedBase64.toByteArray(Charsets.UTF_8).toRequestBody("text/plain".toMediaType()))
                .build()

            val responseBytes = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Search HTTP failed: ${resp.code}")
                resp.body.bytes()
            }

            val decryptedJson = CryptoUtils.decryptAg1Response(responseBytes)
            parseSearchResults(decryptedJson)
        }.recoverCatching { fallbackEx ->
            // Fallback search via standard musicu gateway if AG-1 failed
            searchFallback(query, page, pageSize)
        }
    }

    private fun searchFallback(query: String, page: Int, pageSize: Int): List<TrackMetadata> {
        val payload = """
            {
              "comm": { "ct": "19", "cv": "1859", "uin": "0" },
              "req": {
                "module": "music.search.SearchCgiService",
                "method": "DoSearchForQQMusicDesktop",
                "param": {
                  "query": ${Json.encodeToString(kotlinx.serialization.serializer(), query)},
                  "page_num": $page,
                  "num_per_page": $pageSize,
                  "search_type": 0
                }
              }
            }
        """.trimIndent()

        val sign = CryptoUtils.computeZzcSign(payload)
        val url = "$STANDARD_GATEWAY?_=$sign"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; MuTag) AppleWebKit/537.36")
            .addHeader("Referer", "https://y.qq.com/")
            .post(payload.toRequestBody(MEDIA_TYPE_JSON))
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body.string()
            return parseSearchResults(body)
        }
    }

    private fun parseSearchResults(jsonString: String): List<TrackMetadata> {
        val root = Json.parseToJsonElement(jsonString).jsonObject
        val searchService = root["music.search.SearchCgiService"]?.jsonObject
            ?: root["req"]?.jsonObject
            ?: return emptyList()

        val body = searchService["data"]?.jsonObject?.get("body")?.jsonObject ?: return emptyList()
        val songObj = body["song"]?.jsonObject ?: body["item_song"]?.jsonObject ?: return emptyList()
        val list = songObj["list"]?.jsonArray ?: return emptyList()

        val results = mutableListOf<TrackMetadata>()
        for (item in list) {
            val songObjItem = item.jsonObject
            val mid = songObjItem["mid"]?.jsonPrimitive?.contentOrNull
                ?: songObjItem["songmid"]?.jsonPrimitive?.contentOrNull ?: ""
            if (mid.isBlank()) continue

            val name = songObjItem["name"]?.jsonPrimitive?.contentOrNull
                ?: songObjItem["title"]?.jsonPrimitive?.contentOrNull ?: ""
            val interval = songObjItem["interval"]?.jsonPrimitive?.intOrNull ?: 0

            val singers = mutableListOf<ArtistMetadata>()
            songObjItem["singer"]?.jsonArray?.forEach { sElement ->
                val sObj = sElement.jsonObject
                val sMid = sObj["mid"]?.jsonPrimitive?.contentOrNull ?: ""
                val sName = sObj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                singers.add(
                    ArtistMetadata(
                        id = sMid,
                        name = sName,
                        avatarUrl = QqMusicUrlBuilder.buildSingerAvatarUrl(sMid),
                    ),
                )
            }

            val albumObj = songObjItem["album"]?.jsonObject
            val albumMid = albumObj?.get("mid")?.jsonPrimitive?.contentOrNull ?: ""
            val albumName = albumObj?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
            val album = AlbumMetadata(
                id = albumMid,
                name = albumName,
                coverUrl = QqMusicUrlBuilder.buildAlbumCoverUrl(albumMid),
            )

            results.add(
                TrackMetadata(
                    trackId = mid,
                    title = name,
                    artists = singers,
                    album = album,
                    albumArtist = singers.firstOrNull()?.name ?: "",
                    durationMs = interval * 1000L,
                    coverUrl = album.coverUrl,
                    sourceId = sourceId,
                ),
            )
        }

        return results
    }

    override suspend fun getTrackDetail(trackId: String): Result<TrackMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = """
                {
                  "comm": {
                    "uin": "0",
                    "format": "json",
                    "ct": 28,
                    "cv": 1,
                    "authst": ""
                  },
                  "songinfo": {
                    "module": "music.pf_song_detail_svr",
                    "method": "get_song_detail_yqq",
                    "param": {
                      "song_mid": "$trackId"
                    }
                  }
                }
            """.trimIndent()

            val sign = CryptoUtils.computeZzcSign(payload)
            val url = "$STANDARD_GATEWAY?_=$sign"

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; MuTag) AppleWebKit/537.36")
                .addHeader("Referer", "https://y.qq.com/")
                .post(payload.toRequestBody(MEDIA_TYPE_JSON))
                .build()

            val body = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("TrackDetail HTTP error: ${resp.code}")
                resp.body.string()
            }

            val root = Json.parseToJsonElement(body).jsonObject
            val songData = root["songinfo"]?.jsonObject?.get("data")?.jsonObject
            val trackInfo = songData?.get("track_info")?.jsonObject
                ?: throw IllegalStateException("track_info not found in response")
            val infoObj = songData["info"]?.jsonObject

            fun extractInfoValue(key: String): String = infoObj?.get(key)?.jsonObject
                ?.get("content")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("value")?.jsonPrimitive?.contentOrNull ?: ""

            val directCompany = extractInfoValue("company")
            val directGenre = extractInfoValue("genre")
            val directIntro = extractInfoValue("intro")
            val directPubTime = extractInfoValue("pub_time")
            val directLan = extractInfoValue("lan")

            val idLong = trackInfo["id"]?.jsonPrimitive?.intOrNull?.toLong() ?: 0L
            val mid = trackInfo["mid"]?.jsonPrimitive?.contentOrNull ?: trackId
            val name = trackInfo["name"]?.jsonPrimitive?.contentOrNull
                ?: trackInfo["title"]?.jsonPrimitive?.contentOrNull ?: ""
            val subtitle = trackInfo["subtitle"]?.jsonPrimitive?.contentOrNull ?: ""
            val interval = trackInfo["interval"]?.jsonPrimitive?.intOrNull ?: 0
            val trackNumber = trackInfo["index_album"]?.jsonPrimitive?.intOrNull ?: 0
            val discNumber = trackInfo["index_cd"]?.jsonPrimitive?.intOrNull ?: 1

            val volumeObj = trackInfo["volume"]?.jsonObject
            val replayGain = if (volumeObj != null) {
                val gain = volumeObj["gain"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val peak = volumeObj["peak"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val lra = volumeObj["lra"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
                if (gain != null && peak != null) {
                    ReplayGainInfo(trackGainDb = gain, trackPeak = peak, loudnessRange = lra)
                } else {
                    null
                }
            } else {
                null
            }

            val tryBegin = trackInfo["file"]?.jsonObject?.get("try_begin")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L

            val singers = mutableListOf<ArtistMetadata>()
            trackInfo["singer"]?.jsonArray?.forEach { sElement ->
                val sObj = sElement.jsonObject
                val sMid = sObj["mid"]?.jsonPrimitive?.contentOrNull ?: ""
                val sName = sObj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                singers.add(
                    ArtistMetadata(
                        id = sMid,
                        name = sName,
                        avatarUrl = QqMusicUrlBuilder.buildSingerAvatarUrl(sMid),
                    ),
                )
            }

            val albumObj = trackInfo["album"]?.jsonObject
            val albumMid = albumObj?.get("mid")?.jsonPrimitive?.contentOrNull ?: ""
            val albumName = albumObj?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
            val albumPubDate = albumObj?.get("time_public")?.jsonPrimitive?.contentOrNull ?: ""
            val effectivePubDate = directPubTime.ifBlank { albumPubDate }

            // Fallback visual cover if album cover is missing
            val vsArray = trackInfo["vs"]?.jsonArray
            val visualMid = vsArray?.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: ""
            val coverUrl = if (albumMid.isNotBlank()) {
                QqMusicUrlBuilder.buildAlbumCoverUrl(albumMid)
            } else {
                QqMusicUrlBuilder.buildVisualCoverUrl(visualMid)
            }

            val album = AlbumMetadata(
                id = albumMid,
                name = albumName,
                coverUrl = coverUrl,
                publishDate = effectivePubDate,
            )

            // Extended album detail via memory cache to prevent redundant network calls
            var company = directCompany
            var fullDesc = directIntro
            var trackTotal = 0
            if (albumMid.isNotBlank()) {
                val cachedAlbum = albumDetailCache[albumMid]
                if (cachedAlbum != null) {
                    if (company.isBlank()) company = cachedAlbum.company
                    trackTotal = cachedAlbum.trackCount
                    if (fullDesc.isBlank()) fullDesc = cachedAlbum.description
                } else {
                    val albumDetailResult = getAlbumDetail(albumMid)
                    albumDetailResult.getOrNull()?.let { enrichedAlbum ->
                        albumDetailCache[albumMid] = enrichedAlbum
                        if (company.isBlank()) company = enrichedAlbum.company
                        trackTotal = enrichedAlbum.trackCount
                        if (fullDesc.isBlank()) fullDesc = enrichedAlbum.description
                    }
                }
            }

            val lyricsResult = getLyrics(
                trackId = mid,
                songIdLong = idLong,
                title = name,
                artist = singers.firstOrNull()?.name ?: "",
                durationSec = interval,
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

            TrackMetadata(
                trackId = mid,
                title = name,
                artists = singers,
                album = album.copy(trackCount = trackTotal, description = fullDesc, company = company),
                albumArtist = singers.firstOrNull()?.name ?: "",
                durationMs = interval * 1000L,
                trackNumber = trackNumber,
                trackTotal = trackTotal,
                discNumber = discNumber,
                discTotal = 1,
                publishDate = effectivePubDate,
                genre = directGenre,
                comment = subtitle,
                description = fullDesc,
                language = directLan,
                chorusStartMs = tryBegin,
                replayGain = replayGain,
                company = company,
                lyricist = lyricist,
                composer = composer,
                arranger = arranger,
                producer = producer,
                coverUrl = coverUrl,
                lyrics = lyricsResult,
                sourceId = sourceId,
            )
        }
    }

    override suspend fun getAlbumDetail(albumId: String): Result<AlbumMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = """
                {
                  "comm": {
                    "ct": 20,
                    "cv": 1770,
                    "platform": "wk_v17",
                    "format": "json"
                  },
                  "albumInfo": {
                    "module": "music.musichallAlbum.AlbumInfoServer",
                    "method": "GetAlbumDetail",
                    "param": { "albumMid": "$albumId" }
                  },
                  "songList": {
                    "module": "music.musichallAlbum.AlbumSongList",
                    "method": "GetAlbumSongList",
                    "param": { "albumMid": "$albumId", "begin": 0, "num": 100, "order": 2 }
                  }
                }
            """.trimIndent()

            val sign = CryptoUtils.computeZzcSign(payload)
            val url = "$STANDARD_GATEWAY?_=$sign"

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; MuTag) AppleWebKit/537.36")
                .addHeader("Referer", "https://y.qq.com/")
                .post(payload.toRequestBody(MEDIA_TYPE_JSON))
                .build()

            val body = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("AlbumDetail HTTP error: ${resp.code}")
                resp.body.string()
            }

            val root = Json.parseToJsonElement(body).jsonObject
            val albumData = root["albumInfo"]?.jsonObject?.get("data")?.jsonObject
            val basicInfo = albumData?.get("basicInfo")?.jsonObject
            val companyObj = albumData?.get("company")?.jsonObject

            val name = basicInfo?.get("albumName")?.jsonPrimitive?.contentOrNull
                ?: basicInfo?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
            val pubDate = basicInfo?.get("publishDate")?.jsonPrimitive?.contentOrNull ?: ""
            val desc = basicInfo?.get("desc")?.jsonPrimitive?.contentOrNull ?: ""
            val companyName = companyObj?.get("name")?.jsonPrimitive?.contentOrNull ?: ""

            val songListData = root["songList"]?.jsonObject?.get("data")?.jsonObject
            val totalNum = songListData?.get("totalNum")?.jsonPrimitive?.intOrNull ?: 0

            AlbumMetadata(
                id = albumId,
                name = name,
                coverUrl = QqMusicUrlBuilder.buildAlbumCoverUrl(albumId),
                publishDate = pubDate,
                trackCount = totalNum,
                description = desc,
                company = companyName,
            )
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
            var (rawLrc, transLrc) = fetchDirectLyrics(trackId, songIdLong)

            if (rawLrc.isBlank() && title.isNotBlank()) {
                val alternative = searchAlternativeLyrics(trackId, title, artist, durationSec)
                if (alternative != null) {
                    rawLrc = alternative.first
                    transLrc = alternative.second
                }
            }

            LyricMerger.merge(rawLrc, transLrc)
        }
    }

    private fun fetchDirectLyrics(trackId: String, songIdLong: Long): Pair<String, String> {
        val payload = """
            {
              "comm": { "ct": 24, "cv": 0 },
              "playLyricInfo": {
                "module": "music.musichallSong.PlayLyricInfo",
                "method": "GetPlayLyricInfo",
                "param": {
                  "songMID": "$trackId",
                  "songID": $songIdLong,
                  "qrc": 0,
                  "trans": 1,
                  "roma": 1,
                  "isHQ": 1
                }
              }
            }
        """.trimIndent()

        val sign = CryptoUtils.computeZzcSign(payload)
        val url = "$STANDARD_GATEWAY?_=$sign"

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; MuTag) AppleWebKit/537.36")
            .addHeader("Referer", "https://y.qq.com/")
            .post(payload.toRequestBody(MEDIA_TYPE_JSON))
            .build()

        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return Pair("", "")
            resp.body.string()
        }

        val root = Json.parseToJsonElement(body).jsonObject
        val lyricData = root["playLyricInfo"]?.jsonObject?.get("data")?.jsonObject
        val rawLyricB64 = lyricData?.get("lyric")?.jsonPrimitive?.contentOrNull ?: ""
        val transB64 = lyricData?.get("trans")?.jsonPrimitive?.contentOrNull ?: ""

        var rawLrc = decodeBase64Safe(rawLyricB64)
        var transLrc = decodeBase64Safe(transB64)

        if (rawLrc.isBlank()) {
            val fallback = getLyricsFallback(trackId)
            if (fallback != null) {
                rawLrc = fallback.first
                transLrc = fallback.second
            }
        }

        return Pair(rawLrc, transLrc)
    }

    private suspend fun searchAlternativeLyrics(trackId: String, title: String, artist: String, durationSec: Int): Pair<String, String>? {
        val cleanTitle = title.replace(Regex("""\s*[\(\[（【].*?[\)\]）】]"""), "").trim().ifBlank { title.trim() }
        if (cleanTitle.isBlank()) return null

        val cleanSinger = artist
            .substringBefore('/')
            .substringBefore('&')
            .substringBefore(',')
            .trim()

        val queries = mutableListOf<String>()
        if (cleanSinger.isNotBlank() && cleanSinger != "未知歌手" && cleanSinger != "Unknown") {
            queries.add("$cleanTitle $cleanSinger")
        }
        queries.add(cleanTitle)

        for (q in queries) {
            val searchResult = search(q, page = 1, pageSize = 5).getOrNull() ?: continue
            for (cand in searchResult) {
                if (cand.trackId == trackId || cand.trackId.isBlank()) continue

                val candClean = cand.title.replace(Regex("""\s*[\(\[（【].*?[\)\]）】]"""), "").trim()
                val isTitleMatch = candClean.equals(cleanTitle, ignoreCase = true) ||
                    cand.title.contains(cleanTitle, ignoreCase = true) ||
                    cleanTitle.contains(candClean, ignoreCase = true)
                if (!isTitleMatch) continue

                if (durationSec > 0 && cand.durationMs > 0) {
                    val candSec = (cand.durationMs / 1000).toInt()
                    val maxDiffSec = maxOf(35, (durationSec * 0.15).toInt())
                    if (kotlin.math.abs(candSec - durationSec) > maxDiffSec) continue
                }

                val candLyrics = fetchDirectLyrics(cand.trackId, 0L)
                if (candLyrics.first.isNotBlank()) {
                    return candLyrics
                }
            }
        }
        return null
    }

    private fun getLyricsFallback(trackId: String): Pair<String, String>? {
        return try {
            val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$trackId&g_tk=5381&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf8&notice=0&platform=yqq.json&needNewCode=0"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; MuTag) AppleWebKit/537.36")
                .addHeader("Referer", "https://y.qq.com/")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val str = resp.body.string()
                val root = Json.parseToJsonElement(str).jsonObject
                val rawB64 = root["lyric"]?.jsonPrimitive?.contentOrNull ?: ""
                val transB64 = root["trans"]?.jsonPrimitive?.contentOrNull ?: ""
                Pair(decodeBase64Safe(rawB64), decodeBase64Safe(transB64))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeBase64Safe(base64Str: String): String {
        if (base64Str.isBlank()) return ""
        return try {
            val bytes = Base64.getDecoder().decode(base64Str)
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    override suspend fun recognizeByAcoustic(feature: AcousticFeature): Result<TrackMetadata?> = withContext(Dispatchers.IO) {
        runCatching {
            val result = acousticClient.search(feature)
            if (!result.success || result.songMid.isBlank()) {
                return@runCatching null
            }

            // Retrieve complete enriched metadata using recognized songMid
            val trackDetailResult = getTrackDetail(result.songMid)
            trackDetailResult.getOrNull() ?: result.trackMetadata
        }
    }

    override fun resolveCoverUrl(track: TrackMetadata, resolution: CoverResolution): String {
        val albumMid = track.album.id
        if (albumMid.isNotBlank() && !albumMid.all { it.isDigit() }) {
            val quality = when (resolution) {
                CoverResolution.THUMBNAIL -> CoverQuality.SMALL_500
                CoverResolution.MEDIUM -> CoverQuality.MEDIUM_800
                CoverResolution.LARGE -> CoverQuality.HD_1200
                CoverResolution.ORIGINAL_MASTER -> CoverQuality.ORIGINAL_MASTER
            }
            return QqMusicUrlBuilder.buildAlbumCoverUrl(albumMid, quality)
        }
        val cover = track.coverUrl.replace("http://", "https://")
        if (cover.contains("gtimg.cn") && resolution == CoverResolution.THUMBNAIL) {
            return cover.replace(Regex("R\\d+x\\d+"), "R500x500")
        }
        return cover
    }
}
