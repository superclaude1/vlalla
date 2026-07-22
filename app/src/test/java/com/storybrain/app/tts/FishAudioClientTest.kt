package com.storybrain.app.tts

import java.io.File
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FishAudioClientTest {
    private lateinit var server: MockWebServer

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listsOnlyTrainedVoicesAndSendsSearchParameters() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{
              "total":2,
              "items":[
                {"_id":"voice-1","title":"测试女声","state":"trained","tags":["female","young"],"languages":["zh"]},
                {"_id":"voice-2","title":"训练中","state":"processing","tags":[],"languages":["zh"]}
              ]
            }""".trimIndent()
        ))

        val result = FishAudioClient(server.url("/").toString()).listVoices(
            apiKey = "secret",
            self = false,
            query = "女声",
            language = "zh",
            page = 2,
            pageSize = 80
        )

        assertEquals(2, result.total)
        assertEquals(listOf("voice-1"), result.voices.map(TtsVoice::id))
        assertEquals("FEMALE", result.voices.single().gender)
        assertEquals("PUBLIC", result.voices.single().source)
        val request = server.takeRequest()
        assertEquals("Bearer secret", request.getHeader("Authorization"))
        assertEquals("50", request.requestUrl?.queryParameter("page_size"))
        assertEquals("2", request.requestUrl?.queryParameter("page_number"))
        assertEquals("false", request.requestUrl?.queryParameter("self"))
        assertEquals("女声", request.requestUrl?.queryParameter("title"))
    }

    @Test
    fun synthesizesDirectedAudioAndWritesAtomically() {
        val audio = "ID3-fish-audio".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mpeg")
                .setBody(okio.Buffer().write(audio))
        )
        val output = File(temporaryFolder.root, "fish.mp3")

        FishAudioClient(server.url("/").toString()).synthesize(
            TtsSynthesisRequest(
                text = "你好",
                voice = "fish:voice-1",
                model = "s2.1-pro-free",
                speed = 1.1f,
                directives = TtsDirectives(emotion = "happy", pauseAfterMs = 180, rate = 1.2f)
            ),
            "secret",
            output
        )

        assertTrue(output.exists())
        assertTrue(audio.contentEquals(output.readBytes()))
        val request = server.takeRequest()
        assertEquals("/v1/tts", request.path)
        assertEquals("Bearer secret", request.getHeader("Authorization"))
        assertEquals("s2.1-pro-free", request.getHeader("model"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("[happy]你好[break]", body.getString("text"))
        assertEquals("voice-1", body.getString("reference_id"))
        assertEquals(1.32, body.getJSONObject("prosody").getDouble("speed"), 0.001)
    }
}
