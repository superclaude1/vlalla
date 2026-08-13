package com.storybrain.app.settings

import java.io.EOFException
import java.net.SocketException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkFailurePolicyTest {
    @Test
    fun classifiesConnectionAbortWithoutExposingCauseText() {
        val failure = NetworkFailureClassifier.classify(
            SocketException("Software caused connection abort"),
            stage = RequestStage.RESPONSE,
            requestId = "req-1",
            payloadBytes = 321
        )

        assertEquals(NetworkFailureKind.CONNECTION_ABORTED, failure.kind)
        assertTrue(failure.retryable)
        assertEquals("req-1", failure.requestId)
        assertEquals(RequestStage.RESPONSE, failure.stage)
        assertEquals(321, failure.payloadBytes)
        assertFalse(failure.message.contains("Software caused connection abort"))
    }

    @Test
    fun classifiesEofAndTimeoutAsRetryableTransportFailures() {
        assertEquals(
            NetworkFailureKind.EOF,
            NetworkFailureClassifier.classify(EOFException(), RequestStage.RESPONSE).kind
        )
        assertEquals(
            NetworkFailureKind.TIMEOUT,
            NetworkFailureClassifier.classify(SocketTimeoutException(), RequestStage.RESPONSE).kind
        )
    }

    @Test
    fun classifiesAuthenticationAndAuthorizationResponsesAsNonRetryable() {
        assertFalse(NetworkFailureClassifier.classifyHttp(401, "").retryable)
        assertEquals(NetworkFailureKind.UNAUTHORIZED, NetworkFailureClassifier.classifyHttp(401, "").kind)
        assertFalse(NetworkFailureClassifier.classifyHttp(403, "").retryable)
        assertEquals(NetworkFailureKind.FORBIDDEN, NetworkFailureClassifier.classifyHttp(403, "").kind)
    }

    @Test
    fun honorsRetryAfterForRateLimitedResponse() {
        val failure = NetworkFailureClassifier.classifyHttp(
            statusCode = 429,
            body = "{\"error\":{\"message\":\"slow down\"}}",
            headers = mapOf("Retry-After" to "7")
        )

        assertEquals(NetworkFailureKind.RATE_LIMITED, failure.kind)
        assertTrue(failure.retryable)
        assertEquals(7_000L, failure.retryAfterMillis)
        assertFalse(failure.message.contains("slow down"))
    }

    @Test
    fun classifiesServerErrorsAsRetryableButMalformedJsonAsNonRetryable() {
        assertTrue(NetworkFailureClassifier.classifyHttp(500, "oops").retryable)
        assertEquals(NetworkFailureKind.SERVER, NetworkFailureClassifier.classifyHttp(503, "oops").kind)

        val malformed = NetworkFailureClassifier.classifyMalformedJson(
            stage = RequestStage.PARSE,
            requestId = "req-json",
            payloadBytes = 88
        )
        assertEquals(NetworkFailureKind.MALFORMED_JSON, malformed.kind)
        assertFalse(malformed.retryable)
        assertEquals("req-json", malformed.requestId)
        assertEquals(88, malformed.payloadBytes)
    }

    @Test
    fun retryPolicyIsBoundedAndDoesNotRetrySuccessfulAttempt() {
        assertFalse(RetryPolicy.shouldRetry(attempt = 1, failure = null))
        assertTrue(RetryPolicy.shouldRetry(attempt = 1, failure = NetworkFailureClassifier.classifyHttp(500, "")))
        assertFalse(RetryPolicy.shouldRetry(attempt = RetryPolicy.maxAttempts, failure = NetworkFailureClassifier.classifyHttp(500, "")))
        assertFalse(RetryPolicy.shouldRetry(attempt = 1, failure = NetworkFailureClassifier.classifyHttp(401, "")))
        assertEquals(7_000L, RetryPolicy.delayMillis(attempt = 1, failure = NetworkFailureClassifier.classifyHttp(429, "", mapOf("Retry-After" to "7"))))
    }
}
