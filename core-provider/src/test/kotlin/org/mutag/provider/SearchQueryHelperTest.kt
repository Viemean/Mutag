package org.mutag.provider

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mutag.model.AlbumMetadata
import org.mutag.model.TrackMetadata

class SearchQueryHelperTest {

    @Test
    fun sanitizeRemovesClutterAndNormalizesWhitespace() {
        val input1 = "Darkness Brightness [C90]"
        assertEquals("Darkness Brightness", SearchQueryHelper.sanitize(input1))

        val input2 = "Again (Disc 1) [FLAC 24bit-96kHz]"
        assertEquals("Again", SearchQueryHelper.sanitize(input2))

        val input3 = "Hello_World   Track_01"
        assertEquals("Hello World Track 01", SearchQueryHelper.sanitize(input3))
    }

    @Test
    fun buildPlanGeneratesAppropriateQueriesAndChips() {
        val plan = SearchQueryHelper.buildPlan(
            title = "Again",
            artist = "Vivienne",
            album = "Darkness Brightness",
            albumArtist = "FELT",
        )

        assertEquals("Again Vivienne", plan.primaryQuery)
        assertTrue(plan.secondaryQueries.contains("Again Darkness Brightness"))
        assertTrue(plan.secondaryQueries.contains("Again FELT"))

        val chipLabels = plan.quickChips.map { it.first }
        assertTrue(chipLabels.contains("歌曲 + 歌手"))
        assertTrue(chipLabels.contains("歌曲 + 专辑"))
        assertTrue(chipLabels.contains("歌曲 + 专辑艺术家"))
        assertTrue(chipLabels.contains("仅歌曲"))
    }

    @Test
    fun rankAndDeduplicatePrioritizesDurationAndAlbumMatch() {
        val targetDuration = 240_000L // 4:00
        val targetAlbum = "Darkness Brightness"

        val cand1 = TrackMetadata(
            trackId = "1",
            title = "Again",
            album = AlbumMetadata(id = "a1", name = "Random Album"),
            durationMs = 241_000L, // +1s duration match
            trackNumber = 2,
        )

        val cand2 = TrackMetadata(
            trackId = "2",
            title = "Again",
            album = AlbumMetadata(id = "a2", name = "Darkness Brightness"), // album match
            durationMs = 240_500L, // duration match
            trackNumber = 2,
        )

        val cand3 = TrackMetadata(
            trackId = "3",
            title = "Again",
            album = AlbumMetadata(id = "a3", name = "Unrelated"),
            durationMs = 120_000L, // 2:00 mismatch
            trackNumber = 1,
        )

        val ranked = SearchQueryHelper.rankAndDeduplicate(
            candidates = listOf(cand1, cand3, cand2),
            targetDurationMs = targetDuration,
            targetTrackNumber = 2,
            targetAlbum = targetAlbum,
        )

        assertEquals("2", ranked[0].trackId) // cand2 has both album and duration match
        assertEquals("1", ranked[1].trackId)
        assertEquals("3", ranked[2].trackId)
    }

    @Test
    fun buildPlanStripsTrackNumberForSecondaryQueries() {
        val plan = SearchQueryHelper.buildPlan(
            title = "01. Test Song - covered by Artist",
            artist = "",
            album = "",
            albumArtist = "",
        )
        assertTrue(plan.secondaryQueries.contains("Test Song - covered by Artist"))
    }

    @Test
    fun evaluateMatchDowngradesAcousticWhenCoverMismatch() {
        val candidate = TrackMetadata(
            trackId = "orig_1",
            title = "Test Song",
            artists = listOf(org.mutag.model.ArtistMetadata(id = "art_1", name = "Original Singer")),
            albumArtist = "Original Singer",
            durationMs = 240_000L,
        )

        // 1. 本地标题含 covered 标记，而云端仅为原曲 -> UNCERTAIN
        val resultCover = SearchQueryHelper.evaluateMatch(
            targetDurationMs = 240_000L,
            targetTitle = "01. Test Song - covered by Singer B",
            targetArtist = "",
            targetAlbum = "",
            candidate = candidate,
            isAcoustic = true,
        )
        assertEquals(MatchConfidence.UNCERTAIN, resultCover.confidence)
        assertTrue(resultCover.reason.contains("翻唱"))

        // 2. 本地元数据与云端全部无交集 -> UNCERTAIN
        val resultAllMismatch = SearchQueryHelper.evaluateMatch(
            targetDurationMs = 240_000L,
            targetTitle = "Unrelated Title",
            targetArtist = "Different Artist",
            targetAlbum = "Different Album",
            candidate = candidate,
            isAcoustic = true,
        )
        assertEquals(MatchConfidence.UNCERTAIN, resultAllMismatch.confidence)
        assertTrue(resultAllMismatch.reason.contains("歌手不符"))

        // 3. 歌手不符但标题吻合 -> HIGH
        val resultTitleMatch = SearchQueryHelper.evaluateMatch(
            targetDurationMs = 240_000L,
            targetTitle = "Test Song",
            targetArtist = "Different Artist",
            targetAlbum = "",
            candidate = candidate,
            isAcoustic = true,
        )
        assertEquals(MatchConfidence.HIGH, resultTitleMatch.confidence)

        // 4. 歌手不符但专辑艺术家吻合 -> HIGH
        val resultAlbumArtistMatch = SearchQueryHelper.evaluateMatch(
            targetDurationMs = 240_000L,
            targetTitle = "Different Title",
            targetArtist = "Different Artist",
            targetAlbum = "",
            candidate = candidate,
            isAcoustic = true,
            targetAlbumArtist = "Original Singer",
        )
        assertEquals(MatchConfidence.HIGH, resultAlbumArtistMatch.confidence)

        // 5. 时长差异超过 5 秒 -> UNCERTAIN
        val resultDurationMismatch = SearchQueryHelper.evaluateMatch(
            targetDurationMs = 246_000L,
            targetTitle = "Test Song",
            targetArtist = "Original Singer",
            targetAlbum = "",
            candidate = candidate,
            isAcoustic = true,
        )
        assertEquals(MatchConfidence.UNCERTAIN, resultDurationMismatch.confidence)
        assertTrue(resultDurationMismatch.reason.contains("时长差异较大"))

        // 6. 裸音频无标签且时长 5 秒内 -> HIGH
        val resultBareFile = SearchQueryHelper.evaluateMatch(
            targetDurationMs = 240_000L,
            targetTitle = "",
            targetArtist = "",
            targetAlbum = "",
            candidate = candidate,
            isAcoustic = true,
        )
        assertEquals(MatchConfidence.HIGH, resultBareFile.confidence)
    }
}
