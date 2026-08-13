package com.storybrain.app.settings

import org.json.JSONException
import java.net.SocketException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleClientFailureTest {
    @Test
    fun domainExceptionRetainsSafeRequestDiagnosticsForMalformedJson() {
        val failure = NetworkFailureClassifier.classifyMalformedJson(
            requestId = "request-42",
            payloadBytes = 2048
        )
        val error = LlmDomainException(failure, JSONException("secret response body"))

        assertEquals(NetworkFailureKind.MALFORMED_JSON, error.failure.kind)
        assertEquals("request-42", error.failure.requestId)
        assertEquals(RequestStage.PARSE, error.failure.stage)
        assertEquals(2048, error.failure.payloadBytes)
        assertTrue(error.message!!.contains("有效 JSON"))
        assertTrue(!error.message!!.contains("secret"))
    }

    @Test
    fun transportAbortIsRepresentedAsChineseDomainFailure() {
        val failure = NetworkFailureClassifier.classify(
            SocketException("Software caused connection abort"),
            RequestStage.RESPONSE,
            requestId = "request-transport",
            payloadBytes = 10
        )
        val error = LlmDomainException(failure)

        assertEquals("网络连接被中止，请检查网络后重试。", error.message)
        assertEquals("request-transport", error.failure.requestId)
    }
}
