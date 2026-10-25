package org.mutag.provider.netease

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mutag.model.AcousticFeature
import org.mutag.provider.lyric.LyricCreditsParser

class NeteaseMetadataProviderTest {

    private fun createMockClient(handler: (String, String) -> String): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val postBody = runCatching {
                val buffer = okio.Buffer()
                request.body?.writeTo(buffer)
                buffer.readUtf8()
            }.getOrDefault("")

            val responseJson = handler(url, postBody)
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
    fun searchReturnsParsedTracks() = runBlocking {
        val mockSearchJson = """
            {
                "result": {
                    "songs": [
                        {
                            "id": 1859245776,
                            "name": "孤勇者",
                            "artists": [
                                {
                                    "id": 2116,
                                    "name": "陈奕迅"
                                }
                            ],
                            "album": {
                                "id": 135939238,
                                "name": "孤勇者",
                                "picUrl": "https://p1.music.126.net/dummy_cover.jpg",
                                "publishTime": 1636243200000
                            },
                            "duration": 256000,
                            "no": 1,
                            "cd": "01",
                            "publishTime": 1636243200000
                        }
                    ],
                    "songCount": 1
                },
                "code": 200
            }
        """.trimIndent()

        val client = createMockClient { url, _ ->
            if (url.contains("cloudsearch")) mockSearchJson else "{}"
        }
        val provider = NeteaseMetadataProvider(client)

        val result = provider.search("孤勇者", page = 1, pageSize = 5)
        assertTrue(result.isSuccess)
        val list = result.getOrNull()
        assertNotNull(list)
        assertEquals(1, list?.size)

        val track = list?.first()
        assertEquals("1859245776", track?.trackId)
        assertEquals("孤勇者", track?.title)
        assertEquals("陈奕迅", track?.albumArtist)
        assertEquals(1, track?.artists?.size)
        assertEquals("陈奕迅", track?.artists?.firstOrNull()?.name)
        assertEquals("孤勇者", track?.album?.name)
        assertEquals("135939238", track?.album?.id)
        assertEquals("https://p1.music.126.net/dummy_cover.jpg", track?.coverUrl)
        assertEquals(256000L, track?.durationMs)
        assertEquals(1, track?.trackNumber)
        assertEquals(1, track?.discNumber)
        assertEquals("2021-11-07", track?.publishDate)
    }

    @Test
    fun getTrackDetailMergesAlbumDetail() = runBlocking {
        val mockSongDetailJson = """
            {
                "songs": [
                    {
                        "id": 1859245776,
                        "name": "孤勇者",
                        "ar": [
                            {
                                "id": 2116,
                                "name": "陈奕迅"
                            }
                        ],
                        "al": {
                            "id": 135939238,
                            "name": "孤勇者",
                            "picUrl": "https://p1.music.126.net/dummy_cover.jpg"
                        },
                        "dt": 256000,
                        "no": 1,
                        "cd": "1",
                        "publishTime": 1636243200000
                    }
                ],
                "code": 200
            }
        """.trimIndent()

        val mockAlbumDetailJson = """
            {
                "album": {
                    "id": 135939238,
                    "name": "孤勇者",
                    "company": "EAS Music Ltd.",
                    "picUrl": "https://p1.music.126.net/dummy_cover.jpg",
                    "size": 1,
                    "publishTime": 1636243200000,
                    "artist": {
                        "name": "陈奕迅"
                    }
                },
                "code": 200
            }
        """.trimIndent()

        val mockLyricJson = """
            {
                "lrc": {
                    "lyric": "[00:00.000] 作词 : 唐恬\n[00:00.500] 作曲 : 钱雷\n"
                },
                "code": 200
            }
        """.trimIndent()

        val client = createMockClient { url, _ ->
            when {
                url.contains("song/detail") -> mockSongDetailJson
                url.contains("album/135939238") -> mockAlbumDetailJson
                url.contains("song/lyric") -> mockLyricJson
                else -> "{}"
            }
        }
        val provider = NeteaseMetadataProvider(client)

        val result = provider.getTrackDetail("1859245776")
        assertTrue(result.isSuccess)
        val track = result.getOrNull()
        assertNotNull(track)
        assertEquals("1859245776", track?.trackId)
        assertEquals("EAS Music Ltd.", track?.company)
        assertEquals(1, track?.trackTotal)
        assertEquals("陈奕迅", track?.albumArtist)
        assertEquals("唐恬", track?.lyricist)
        assertEquals("钱雷", track?.composer)
        assertNotNull(track?.lyrics)
    }

    @Test
    fun getAlbumDetailCachesResult() = runBlocking {
        var networkCallCount = 0
        val mockAlbumDetailJson = """
            {
                "album": {
                    "id": 12345,
                    "name": "测试专辑",
                    "company": "测试唱片公司",
                    "picUrl": "https://example.com/cover.jpg",
                    "size": 10,
                    "publishTime": 1636243200000,
                    "artist": {
                        "name": "测试艺术家"
                    }
                },
                "code": 200
            }
        """.trimIndent()

        val client = createMockClient { _, _ ->
            networkCallCount++
            mockAlbumDetailJson
        }
        val provider = NeteaseMetadataProvider(client)

        val firstCall = provider.getAlbumDetail("12345")
        assertTrue(firstCall.isSuccess)
        assertEquals("测试专辑", firstCall.getOrNull()?.name)
        assertEquals(1, networkCallCount)

        val secondCall = provider.getAlbumDetail("12345")
        assertTrue(secondCall.isSuccess)
        assertEquals("测试专辑", secondCall.getOrNull()?.name)
        assertEquals(1, networkCallCount)
    }

    @Test
    fun getLyricsParsesLrcAndTranslationAndExtractsCredits() = runBlocking {
        val mockLyricJson = """
            {
                "lrc": {
                    "lyric": "[00:00.000] 作词 : 唐恬\n[00:00.500] 作曲 : 钱雷\n[00:01.000] 编曲 : 钱雷\n[00:04.000]都是勇敢的\n[00:06.000]你额头的伤口 你的不同 犯的错\n"
                },
                "tlyric": {
                    "lyric": "[00:04.000]All are brave\n[00:06.000]The wounds on your forehead, your differences, your mistakes\n"
                },
                "code": 200
            }
        """.trimIndent()

        val client = createMockClient { _, _ -> mockLyricJson }
        val provider = NeteaseMetadataProvider(client)

        val lyricResult = provider.getLyrics("1859245776")
        assertTrue(lyricResult.isSuccess)
        val lyrics = lyricResult.getOrNull()
        assertNotNull(lyrics)
        assertTrue(lyrics?.rawLyricText?.contains("都是勇敢的") == true)
        assertTrue(lyrics?.hasTranslation == true)
        assertTrue(lyrics?.mergedLrcText?.contains("All are brave") == true)

        val credits = LyricCreditsParser.parse(lyrics?.rawLyricText.orEmpty())
        assertEquals("唐恬", credits.lyricist)
        assertEquals("钱雷", credits.composer)
        assertEquals("钱雷", credits.arranger)
    }

    @Test
    fun recognizeByAcousticReturnsSuccessNullBeforeEndpointAttached() = runBlocking {
        val provider = NeteaseMetadataProvider()
        val dummyFeature = AcousticFeature(
            data = byteArrayOf(1, 2, 3),
            duration = 12.0f,
        )
        val result = provider.recognizeByAcoustic(dummyFeature)
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }
}
