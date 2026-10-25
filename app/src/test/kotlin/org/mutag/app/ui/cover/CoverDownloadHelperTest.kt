package org.mutag.app.ui.cover

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mutag.model.AlbumMetadata
import org.mutag.model.TrackMetadata

class CoverDownloadHelperTest {

    @Test
    fun resolveTargetCoverUrlPrefersMasterResolutionWhenRequested() {
        val track = TrackMetadata(
            trackId = "002V5j041q0V0H",
            title = "夜曲",
            album = AlbumMetadata(id = "000MkMni19ClKG", name = "十一月的萧邦"),
        )

        val masterUrl = CoverDownloadHelper.resolveTargetCoverUrl(track, preferMaster = true)
        assertEquals("https://y.gtimg.cn/music/photo_new/T002R800x800M000000MkMni19ClKG.jpg", masterUrl)

        val largeUrl = CoverDownloadHelper.resolveTargetCoverUrl(track, preferMaster = false)
        assertEquals("https://y.gtimg.cn/music/photo_new/T002R1200x1200M000000MkMni19ClKG.jpg", largeUrl)
    }

    @Test
    fun resolveTargetCoverUrlNormalizesHttpToHttpsWhenOnlyCoverUrlPresent() {
        val track = TrackMetadata(
            trackId = "12345",
            title = "未知曲目",
            coverUrl = "http://example.com/cover.jpg",
        )

        val resolved = CoverDownloadHelper.resolveTargetCoverUrl(track, preferMaster = false)
        assertEquals("https://example.com/cover.jpg", resolved)
    }

    @Test
    fun probeCoverLengthReturnsContentLengthOnSuccessfulHead() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.method == "HEAD") {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Length", "524288")
                        .body("".toResponseBody("image/jpeg".toMediaTypeOrNull()))
                        .build()
                } else {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .build()
                }
            }
            .build()

        val length = CoverDownloadHelper.probeCoverLength(client, "https://example.com/test.jpg")
        assertEquals(524288L, length)
    }

    @Test
    fun probeCoverLengthReturnsNullOnBlankUrlOrFailure() {
        val client = OkHttpClient()
        assertNull(CoverDownloadHelper.probeCoverLength(client, ""))

        val failingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Error")
                    .build()
            }
            .build()
        assertNull(CoverDownloadHelper.probeCoverLength(failingClient, "https://example.com/test.jpg"))
    }

    @Test
    fun obtainOrCompressCoverReusesLocalBytesWhenKnownCloudMatches() {
        val localBytes = ByteArray(128) { 0x33.toByte() }
        val targetTrack = TrackMetadata(
            trackId = "002V5j041q0V0H",
            title = "夜曲",
            album = AlbumMetadata(id = "000MkMni19ClKG", name = "十一月的萧邦"),
        )

        val client = OkHttpClient()
        val result = CoverDownloadHelper.obtainOrCompressCover(
            httpClient = client,
            targetTrack = targetTrack,
            localCoverBytes = localBytes,
            localCloudAlbumMid = "000MkMni19ClKG",
        )

        // 本地封面大小未超出阈值，安全放行直接复用原字节
        assertArrayEquals(localBytes, result)
    }
}
