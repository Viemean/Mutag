package org.mutag.provider.qqmusic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AcousticRecognizeClientTest {

    @Test
    fun parseResponseSuccessfullyParsesValidServerJson() {
        val client = AcousticRecognizeClient()
        val mockJson = """
            {
                "code": 0,
                "ret": 0,
                "results": [
                    {
                        "offset": 15.2
                    }
                ],
                "songlist": [
                    {
                        "id": 97773,
                        "mid": "0039MnYb0qxYAc",
                        "name": "晴天",
                        "singer": [
                            {
                                "id": 4286,
                                "mid": "0025NhlN2yWrP4",
                                "name": "周杰伦"
                            }
                        ],
                        "album": {
                            "id": 8220,
                            "mid": "000MkMni19ClKG",
                            "name": "叶惠美"
                        },
                        "interval": 269
                    }
                ]
            }
        """.trimIndent()

        val result = client.parseResponse(mockJson)
        assertTrue(result.success)
        assertEquals("晴天", result.title)
        assertEquals("周杰伦", result.artist)
        assertEquals("叶惠美", result.album)
        assertEquals("0039MnYb0qxYAc", result.songMid)
        assertEquals(15.2, result.offsetSeconds, 0.001)
        assertNotNull(result.trackMetadata)
        assertEquals(269000L, result.trackMetadata?.durationMs)
    }

    @Test
    fun parseResponseHandlesFailureResult() {
        val client = AcousticRecognizeClient()
        val mockFail = """{"code":0,"ret":-100}"""
        val result = client.parseResponse(mockFail)
        assertEquals(false, result.success)
    }
}
