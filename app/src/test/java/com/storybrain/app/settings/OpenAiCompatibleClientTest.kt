package com.storybrain.app.settings

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleClientTest {
    @Test
    fun cancellingSuspendCompletionCancelsActiveOkHttpCall() = runBlocking {
        // The request URL below is pinned to the "localhost" hostname so the
        // self-signed certificate's DNS SAN always matches. MockWebServer's own
        // url() can expose 127.0.0.1 or the machine name depending on the OS,
        // which makes hostname verification fail on some hosts.
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory(), false)
            enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            start()
        }
        var activeCall: Call? = null
        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .build()
        val callFactory = Call.Factory { request -> okHttpClient.newCall(request).also { activeCall = it } }
        val api = OpenAiCompatibleClient(callFactory)

        try {
            val request = launch(Dispatchers.IO) {
                api.chatCompletionResult(
                    baseUrl = "https://localhost:${server.port}/v1",
                    apiKey = "test-key",
                    model = "test-model",
                    messages = listOf(LlmMessage("user", "hello"))
                )
            }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))

            request.cancelAndJoin()

            assertTrue("coroutine cancellation must cancel the in-flight transport", activeCall?.isCanceled() == true)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun structuredCompletionCarriesRealUsageAndRequestMetadata() {
        val result = ChatCompletionResult(
            content = "{\"ok\":true}",
            usage = ChatCompletionUsage(prompt = 12, completion = 7, total = 19, quality = UsageQuality.COMPLETE),
            responseModel = "model-from-response",
            requestId = "request-42"
        )

        assertEquals(19, result.usage.total)
        assertEquals("model-from-response", result.responseModel)
        assertEquals("request-42", result.requestId)
        assertEquals("{\"ok\":true}", result.content)
    }

    @Test
    fun usageQualityDistinguishesCompletePartialAndMissingProviderData() {
        assertEquals(UsageQuality.COMPLETE, ChatCompletionUsage.from(12, 7, 19).quality)
        assertEquals(UsageQuality.PARTIAL, ChatCompletionUsage.from(12, null, 19).quality)
        assertEquals(UsageQuality.MISSING, ChatCompletionUsage.from(null, null, null).quality)
    }

    @Test
    fun optionsOmitTemperatureAndAcceptContentParts() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory(), false)
            enqueue(
                MockResponse().setBody(
                    JSONObject()
                        .put("id", "req-parts")
                        .put("model", "gateway-model")
                        .put(
                            "choices",
                            org.json.JSONArray().put(
                                JSONObject()
                                    .put("finish_reason", "stop")
                                    .put(
                                        "message",
                                        JSONObject().put(
                                            "content",
                                            org.json.JSONArray().put(
                                                JSONObject()
                                                    .put("type", "text")
                                                    .put("text", "{\"ok\":true}")
                                            )
                                        )
                                    )
                            )
                        )
                        .toString()
                ).setHeader("Content-Type", "application/json")
            )
            start()
        }
        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .build()
        try {
            val result = OpenAiCompatibleClient(okHttpClient).chatCompletionResult(
                baseUrl = "https://localhost:${server.port}/v1",
                apiKey = "test-key",
                model = "test-model",
                messages = listOf(LlmMessage("user", "hello")),
                options = ChatRequestOptions(
                    responseFormat = ResponseFormatMode.JSON_OBJECT,
                    temperature = null
                )
            )
            val request = server.takeRequest(5, TimeUnit.SECONDS)
            val body = JSONObject(request!!.body.readUtf8())

            assertTrue(!body.has("temperature"))
            assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
            assertEquals("{\"ok\":true}", result.content)
            assertEquals("message.content.parts", result.contentKind)
            assertEquals("stop", result.finishReason)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun normalizesCommonEndpointForms() {
        assertEquals("https://api.openai.com/v1", OpenAiCompatibleClient.normalizeBaseUrl("api.openai.com"))
        assertEquals("https://api.deepseek.com", OpenAiCompatibleClient.normalizeBaseUrl("https://api.deepseek.com"))
        assertEquals("https://example.com/openai", OpenAiCompatibleClient.normalizeBaseUrl("https://example.com/openai"))
        assertThrows(IllegalArgumentException::class.java) {
            OpenAiCompatibleClient.normalizeBaseUrl("http://10.0.2.2:8000/v1/")
        }
    }
}
