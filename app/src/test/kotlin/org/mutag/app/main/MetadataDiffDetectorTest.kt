package org.mutag.app.main

import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mutag.core.tagger.scanner.AudioMetadata
import org.mutag.model.AlbumMetadata
import org.mutag.model.ArtistMetadata
import org.mutag.model.TrackMetadata

/**
 * 元数据幂等比对器单元测试套件。
 */
class MetadataDiffDetectorTest {

    @TempDir
    lateinit var tempDir: File

    private fun createTestClient(expectedContentLength: Long): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request()
            if (req.method == "HEAD") {
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Length", expectedContentLength.toString())
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

    @Test
    fun testMetadataEquivalentAllMatching() {
        val local = AudioMetadata(
            filePath = "/dummy/track.mp3",
            fileName = "track.mp3",
            fileSize = 1000L,
            lastModified = 2000L,
            format = "MP3",
            title = "夜曲",
            artist = "周杰伦",
            album = "十一月的萧邦",
            albumArtist = "周杰伦",
            trackNumber = 1,
            trackTotal = 12,
            discNumber = 1,
            discTotal = 1,
            publishDate = "2005-11-01",
            genre = "Pop",
        )

        val cloud = TrackMetadata(
            trackId = "002V5j041q0V0H",
            title = "夜曲",
            artists = listOf(ArtistMetadata(id = "1", name = "周杰伦")),
            album = AlbumMetadata(id = "000MkMni19ClKG", name = "十一月的萧邦"),
            albumArtist = "周杰伦",
            trackNumber = 1,
            trackTotal = 12,
            discNumber = 1,
            discTotal = 1,
            publishDate = "2005-11-01",
            genre = "Pop",
        )

        assertTrue(MetadataDiffDetector.isMetadataEquivalent(local, cloud))
    }

    @Test
    fun testMetadataEquivalentTitleMismatch() {
        val local = AudioMetadata(
            filePath = "/dummy/track.mp3",
            fileName = "track.mp3",
            fileSize = 1000L,
            lastModified = 2000L,
            format = "MP3",
            title = "夜曲 (Live)",
            artist = "周杰伦",
        )

        val cloud = TrackMetadata(
            trackId = "002V5j041q0V0H",
            title = "夜曲",
            artists = listOf(ArtistMetadata(id = "1", name = "周杰伦")),
        )

        assertFalse(MetadataDiffDetector.isMetadataEquivalent(local, cloud))
    }

    @Test
    fun testMetadataEquivalentYearMismatch() {
        val local = AudioMetadata(
            filePath = "/dummy/track.mp3",
            fileName = "track.mp3",
            fileSize = 1000L,
            lastModified = 2000L,
            format = "MP3",
            title = "夜曲",
            publishDate = "2005",
        )

        val cloud = TrackMetadata(
            trackId = "002V5j041q0V0H",
            title = "夜曲",
            publishDate = "2006-01-01",
        )

        assertFalse(MetadataDiffDetector.isMetadataEquivalent(local, cloud))
    }

    @Test
    fun testCoverEquivalentLevel1FastCheck() {
        val dummyFile = File(tempDir, "song.flac")
        dummyFile.writeBytes(ByteArray(2048))

        val local = AudioMetadata(
            filePath = dummyFile.absolutePath,
            fileName = dummyFile.name,
            fileSize = dummyFile.length(),
            lastModified = dummyFile.lastModified(),
            format = "FLAC",
            hasCover = true,
            coverSize = 1567694L,
            cloudTrackId = "002V5j041q0V0H",
            cloudAlbumMid = "000MkMni19ClKG",
        )

        val cloud = TrackMetadata(
            trackId = "002V5j041q0V0H",
            title = "夜曲",
            album = AlbumMetadata(id = "000MkMni19ClKG", name = "十一月的萧邦"),
        )

        val client = OkHttpClient()
        val isEquivalent = MetadataDiffDetector.isCoverEquivalent(
            local = local,
            cloud = cloud,
            httpClient = client,
            preferMaster = true,
        )
        assertTrue(isEquivalent)
    }

    @Test
    fun testCoverEquivalentLevel1InvalidWhenFileModified() {
        val dummyFile = File(tempDir, "song.flac")
        dummyFile.writeBytes(ByteArray(2048))

        val local = AudioMetadata(
            filePath = dummyFile.absolutePath,
            fileName = dummyFile.name,
            fileSize = 1024L, // 快照大小不一致，模拟外部修改
            lastModified = dummyFile.lastModified(),
            format = "FLAC",
            hasCover = true,
            coverSize = 1567694L,
            cloudTrackId = "002V5j041q0V0H",
            cloudAlbumMid = "000MkMni19ClKG",
        )

        val cloud = TrackMetadata(
            trackId = "002V5j041q0V0H",
            title = "夜曲",
            album = AlbumMetadata(id = "000MkMni19ClKG", name = "十一月的萧邦"),
        )

        // 外部修改后，Level 1 失效；Level 2 检查本地文件内嵌图片
        // 由于测试临时文件不是真实音频未包含 PICTURE 块，故应判定为不等价
        val client = createTestClient(1567694L)
        val isEquivalent = MetadataDiffDetector.isCoverEquivalent(
            local = local,
            cloud = cloud,
            httpClient = client,
            preferMaster = true,
        )
        assertFalse(isEquivalent)
    }

    @Test
    fun testCoverEquivalentLevel2HeadProbeSuccess() {
        val dummyFile = File(tempDir, "song.mp3")
        dummyFile.writeBytes(ByteArray(512))

        val local = AudioMetadata(
            filePath = dummyFile.absolutePath,
            fileName = dummyFile.name,
            fileSize = dummyFile.length(),
            lastModified = dummyFile.lastModified(),
            format = "MP3",
            hasCover = true,
            coverSize = 374653L, // 本地已有封面大小
            cloudTrackId = "", // 无云端历史指纹
            cloudAlbumMid = "",
        )

        val cloud = TrackMetadata(
            trackId = "002V5j041q0V0H",
            title = "夜曲",
            album = AlbumMetadata(id = "000MkMni19ClKG", name = "十一月的萧邦"),
        )

        // HEAD 探测返回 374653 字节，与本地 coverSize 一致
        val client = createTestClient(374653L)
        val isEquivalent = MetadataDiffDetector.isCoverEquivalent(
            local = local,
            cloud = cloud,
            httpClient = client,
            preferMaster = false,
        )
        assertTrue(isEquivalent)
    }

    @Test
    fun testCoverEquivalentLevel2HeadProbeMismatch() {
        val dummyFile = File(tempDir, "song.mp3")
        dummyFile.writeBytes(ByteArray(512))

        val local = AudioMetadata(
            filePath = dummyFile.absolutePath,
            fileName = dummyFile.name,
            fileSize = dummyFile.length(),
            lastModified = dummyFile.lastModified(),
            format = "MP3",
            hasCover = true,
            coverSize = 200000L,
            cloudTrackId = "",
            cloudAlbumMid = "",
        )

        val cloud = TrackMetadata(
            trackId = "002V5j041q0V0H",
            title = "夜曲",
            album = AlbumMetadata(id = "000MkMni19ClKG", name = "十一月的萧邦"),
        )

        // HEAD 探测返回 374653 字节，与本地 200000 字节不同
        val client = createTestClient(374653L)
        val isEquivalent = MetadataDiffDetector.isCoverEquivalent(
            local = local,
            cloud = cloud,
            httpClient = client,
            preferMaster = false,
        )
        assertFalse(isEquivalent)
    }

    @Test
    fun testMetadataEquivalentReplayGainMissingInLocal() {
        val local = AudioMetadata(
            filePath = "/dummy/track.mp3",
            fileName = "track.mp3",
            fileSize = 1000L,
            lastModified = 2000L,
            format = "MP3",
            title = "夜曲",
            artist = "周杰伦",
            album = "十一月的萧邦",
            replayGainTrackGain = null,
            replayGainTrackPeak = null,
        )

        val cloud = TrackMetadata(
            trackId = "002V5j041q0V0H",
            title = "夜曲",
            artists = listOf(ArtistMetadata(id = "1", name = "周杰伦")),
            album = AlbumMetadata(id = "000MkMni19ClKG", name = "十一月的萧邦"),
            replayGain = org.mutag.model.ReplayGainInfo(trackGainDb = -8.52, trackPeak = 0.987654),
        )

        assertFalse(MetadataDiffDetector.isMetadataEquivalent(local, cloud))
    }

    @Test
    fun testMetadataEquivalentReplayGainMatches() {
        val local = AudioMetadata(
            filePath = "/dummy/track.mp3",
            fileName = "track.mp3",
            fileSize = 1000L,
            lastModified = 2000L,
            format = "MP3",
            title = "夜曲",
            artist = "周杰伦",
            album = "十一月的萧邦",
            replayGainTrackGain = -8.52,
            replayGainTrackPeak = 0.987654,
        )

        val cloud = TrackMetadata(
            trackId = "002V5j041q0V0H",
            title = "夜曲",
            artists = listOf(ArtistMetadata(id = "1", name = "周杰伦")),
            album = AlbumMetadata(id = "000MkMni19ClKG", name = "十一月的萧邦"),
            replayGain = org.mutag.model.ReplayGainInfo(trackGainDb = -8.52, trackPeak = 0.987654),
        )

        assertTrue(MetadataDiffDetector.isMetadataEquivalent(local, cloud))
    }

    @Test
    fun testMetadataEquivalentLyricsMissingInLocal() {
        val local = AudioMetadata(
            filePath = "/dummy/track.mp3",
            fileName = "track.mp3",
            fileSize = 1000L,
            lastModified = 2000L,
            format = "MP3",
            title = "夜曲",
            artist = "周杰伦",
            album = "十一月的萧邦",
            lyrics = null,
        )

        val cloud = TrackMetadata(
            trackId = "002V5j041q0V0H",
            title = "夜曲",
            artists = listOf(ArtistMetadata(id = "1", name = "周杰伦")),
            album = AlbumMetadata(id = "000MkMni19ClKG", name = "十一月的萧邦"),
            lyrics = org.mutag.model.SyncedLyrics(
                rawLyricText = "[00:01.00]一群嗜血的蚂蚁被腐肉所吸引",
                mergedLrcText = "[00:01.00]一群嗜血的蚂蚁被腐肉所吸引",
            ),
        )

        assertFalse(MetadataDiffDetector.isMetadataEquivalent(local, cloud))
    }
}
