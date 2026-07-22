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

class OpenAiTtsClientTest {
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
    fun detectsModelsAndSynthesizesWithInstructions() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"data":[{"id":"tts-model"},{"id":""}]}"""
        ))
        val audio = "ID3-openai-audio".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setBody(okio.Buffer().write(audio))
        )
        val client = OpenAiTtsClient(server.url("/v1/").toString())

        assertEquals(listOf("tts-model"), client.listModels("secret"))
        val modelRequest = server.takeRequest()
        assertEquals("/v1/models", modelRequest.path)
        assertEquals("Bearer secret", modelRequest.getHeader("Authorization"))

        val output = File(temporaryFolder.root, "openai.mp3")
        client.synthesize(
            TtsSynthesisRequest(
                text = "晚上好",
                voice = "openai:alloy",
                model = "tts-model",
                speed = 0.9f,
                supportsInstructions = true,
                directives = TtsDirectives(emotion = "calm", delivery = "soft tone", rate = 1.1f)
            ),
            "secret",
            output
        )

        assertTrue(audio.contentEquals(output.readBytes()))
        val speechRequest = server.takeRequest()
        assertEquals("/v1/audio/speech", speechRequest.path)
        val body = JSONObject(speechRequest.body.readUtf8())
        assertEquals("tts-model", body.getString("model"))
        assertEquals("晚上好", body.getString("input"))
        assertEquals("alloy", body.getString("voice"))
        assertEquals(0.99, body.getDouble("speed"), 0.001)
        assertEquals("情绪：calm；语气：soft tone；语速1.10倍", body.getString("instructions"))
    }
}
