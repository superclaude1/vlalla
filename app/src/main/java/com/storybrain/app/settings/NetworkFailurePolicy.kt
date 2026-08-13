package com.storybrain.app.settings

import java.io.EOFException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Stages deliberately contain no URL, API key, request body, or response body. */
enum class RequestStage { REQUEST, RESPONSE, PARSE }

enum class NetworkFailureKind {
    CONNECTION_ABORTED,
    EOF,
    TIMEOUT,
    UNAUTHORIZED,
    FORBIDDEN,
    RATE_LIMITED,
    SERVER,
    MALFORMED_JSON,
    NETWORK
}

data class NetworkFailure(
    val kind: NetworkFailureKind,
    val retryable: Boolean,
    val message: String,
    val statusCode: Int? = null,
    val retryAfterMillis: Long? = null,
    val requestId: String? = null,
    val stage: RequestStage,
    val payloadBytes: Int = 0,
    val attempt: Int = 1
)

/** Pure classification rules. It never logs or includes request/response contents. */
object NetworkFailureClassifier {
    fun classify(
        error: Throwable,
        stage: RequestStage,
        requestId: String? = null,
        payloadBytes: Int = 0
    ): NetworkFailure {
        val cause = error.causes().firstOrNull { it is SocketTimeoutException || it is SocketException || it is EOFException }
        val kind = when {
            cause is SocketTimeoutException -> NetworkFailureKind.TIMEOUT
            cause is SocketException && cause.message.orEmpty().contains("Software caused connection abort", ignoreCase = true) -> NetworkFailureKind.CONNECTION_ABORTED
            cause is SocketException -> NetworkFailureKind.NETWORK
            cause is EOFException -> NetworkFailureKind.EOF
            else -> NetworkFailureKind.NETWORK
        }
        return NetworkFailure(
            kind = kind,
            retryable = kind == NetworkFailureKind.TIMEOUT || kind == NetworkFailureKind.CONNECTION_ABORTED ||
                kind == NetworkFailureKind.EOF || kind == NetworkFailureKind.NETWORK,
            message = kind.message(),
            requestId = requestId,
            stage = stage,
            payloadBytes = payloadBytes
        )
    }

    fun classifyHttp(
        statusCode: Int,
        body: String,
        headers: Map<String, String> = emptyMap(),
        requestId: String? = null,
        stage: RequestStage = RequestStage.RESPONSE,
        payloadBytes: Int = 0
    ): NetworkFailure {
        val kind = when (statusCode) {
            401 -> NetworkFailureKind.UNAUTHORIZED
            403 -> NetworkFailureKind.FORBIDDEN
            429 -> NetworkFailureKind.RATE_LIMITED
            in 500..599 -> NetworkFailureKind.SERVER
            else -> NetworkFailureKind.NETWORK
        }
        val retryAfter = if (kind == NetworkFailureKind.RATE_LIMITED) parseRetryAfter(headers) else null
        return NetworkFailure(
            kind = kind,
            retryable = kind == NetworkFailureKind.RATE_LIMITED || kind == NetworkFailureKind.SERVER,
            message = kind.message(),
            statusCode = statusCode,
            retryAfterMillis = retryAfter,
            requestId = requestId,
            stage = stage,
            payloadBytes = payloadBytes
        )
    }

    fun classifyMalformedJson(
        stage: RequestStage = RequestStage.PARSE,
        requestId: String? = null,
        payloadBytes: Int = 0
    ): NetworkFailure = NetworkFailure(
        kind = NetworkFailureKind.MALFORMED_JSON,
        retryable = false,
        message = NetworkFailureKind.MALFORMED_JSON.message(),
        requestId = requestId,
        stage = stage,
        payloadBytes = payloadBytes
    )

    private fun parseRetryAfter(headers: Map<String, String>): Long? {
        val value = headers.entries.firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }?.value?.trim() ?: return null
        value.toLongOrNull()?.let { return (it * 1_000L).coerceAtLeast(0L) }
        return runCatching {
            ChronoUnit.MILLIS.between(ZonedDateTime.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)).coerceAtLeast(0L)
        }.getOrNull()
    }

    private fun NetworkFailureKind.message(): String = when (this) {
        NetworkFailureKind.CONNECTION_ABORTED -> "网络连接被中止，请检查网络后重试。"
        NetworkFailureKind.EOF -> "网络响应提前结束，请重试。"
        NetworkFailureKind.TIMEOUT -> "网络请求超时，请检查网络或服务状态后重试。"
        NetworkFailureKind.UNAUTHORIZED -> "API Key 无效或已过期，请检查设置。"
        NetworkFailureKind.FORBIDDEN -> "API Key 没有权限访问该模型或服务。"
        NetworkFailureKind.RATE_LIMITED -> "请求过于频繁，请稍后重试。"
        NetworkFailureKind.SERVER -> "LLM 服务暂时不可用，请稍后重试。"
        NetworkFailureKind.MALFORMED_JSON -> "LLM 返回内容不是有效 JSON，本批次未保存。"
        NetworkFailureKind.NETWORK -> "网络请求失败，请检查 API 地址和网络后重试。"
    }

    private fun Throwable.causes(): Sequence<Throwable> = sequence {
        var current: Throwable? = this@causes
        val seen = mutableSetOf<Throwable>()
        while (current != null && seen.add(current)) {
            yield(current)
            current = current.cause
        }
    }
}

object RetryPolicy {
    const val maxAttempts: Int = 3

    fun shouldRetry(attempt: Int, failure: NetworkFailure?): Boolean =
        failure?.retryable == true && attempt in 1 until maxAttempts

    fun delayMillis(attempt: Int, failure: NetworkFailure): Long =
        (failure.retryAfterMillis ?: (500L * (1L shl (attempt - 1).coerceIn(0, 3)))).coerceIn(0L, 10_000L)
}
