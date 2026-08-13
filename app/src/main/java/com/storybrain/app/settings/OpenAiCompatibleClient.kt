package com.storybrain.app.settings

import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject


data class LlmMessage(val role: String, val content: String)

enum class UsageQuality { COMPLETE, PARTIAL, MISSING }

data class ChatCompletionUsage(
    val prompt: Int?,
    val completion: Int?,
    val total: Int?,
    val quality: UsageQuality
) {
    val promptTokens: Int? get() = prompt
    val completionTokens: Int? get() = completion
    val totalTokens: Int? get() = total

    companion object {
        fun from(prompt: Int?, completion: Int?, total: Int?) = ChatCompletionUsage(
            prompt,
            completion,
            total,
            when {
                prompt != null && completion != null && total != null -> UsageQuality.COMPLETE
                prompt == null && completion == null && total == null -> UsageQuality.MISSING
                else -> UsageQuality.PARTIAL
            }
        )
    }
}

data class ChatCompletionResult(
    val content: String,
    val usage: ChatCompletionUsage,
    val responseModel: String?,
    val requestId: String
)

class OpenAiCompatibleClient(
    private val callFactory: Call.Factory = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
        .readTimeout(ANALYSIS_READ_TIMEOUT_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()
) {
    fun listModels(baseUrl: String, apiKey: String): List<String> {
        val connection = openConnection(
            "${normalizeBaseUrl(baseUrl)}/models",
            apiKey,
            "GET",
            readTimeoutMillis = MODEL_READ_TIMEOUT_MS
        )
        return try {
            connection.useResponse { body ->
                val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
                buildList {
                    for (index in 0 until data.length()) {
                        data.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }?.let(::add)
                    }
                }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
            }
        } catch (error: Throwable) {
            if (error is LlmDomainException) throw error
            throw LlmDomainException(
                NetworkFailureClassifier.classify(
                    error,
                    stage = RequestStage.RESPONSE,
                    requestId = UUID.randomUUID().toString()
                ),
                error
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<LlmMessage>,
        temperature: Double = 0.2,
        jsonMode: Boolean = false
    ): String = chatCompletionResult(baseUrl, apiKey, model, messages, temperature, jsonMode).content

    fun chatCompletionBlocking(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<LlmMessage>,
        temperature: Double = 0.2,
        jsonMode: Boolean = false
    ): String = chatCompletionResultBlocking(baseUrl, apiKey, model, messages, temperature, jsonMode).content

    suspend fun chatCompletionResult(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<LlmMessage>,
        temperature: Double = 0.2,
        jsonMode: Boolean = false
    ): ChatCompletionResult = executeWithRetry(
        createRequest(baseUrl, apiKey, model, messages, temperature, jsonMode)
    )

    fun chatCompletionResultBlocking(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<LlmMessage>,
        temperature: Double = 0.2,
        jsonMode: Boolean = false
    ): ChatCompletionResult = runBlocking {
        chatCompletionResult(baseUrl, apiKey, model, messages, temperature, jsonMode)
    }

    private fun createRequest(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<LlmMessage>,
        temperature: Double,
        jsonMode: Boolean
    ): CompletionRequest {
        val payload = JSONObject()
            .put("model", model)
            .put("temperature", temperature)
            .put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().put("role", message.role).put("content", message.content))
                }
            })
        if (jsonMode) payload.put("response_format", JSONObject().put("type", "json_object"))
        val payloadText = payload.toString()
        val requestId = UUID.randomUUID().toString()
        val request = Request.Builder()
            .url("${normalizeBaseUrl(baseUrl)}/chat/completions")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
            .post(payloadText.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return CompletionRequest(request, payloadText.toByteArray(Charsets.UTF_8).size, requestId)
    }

    private suspend fun executeWithRetry(request: CompletionRequest): ChatCompletionResult {
        var attempt = 1
        while (true) {
            try {
                val response = callFactory.newCall(request.request).await()
                return response.use { parseResponse(it, request, attempt) }
            } catch (error: LlmDomainException) {
                if (!RetryPolicy.shouldRetry(attempt, error.failure)) throw error
                delay(RetryPolicy.delayMillis(attempt, error.failure))
                attempt++
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                val failure = NetworkFailureClassifier.classify(
                    error,
                    stage = RequestStage.REQUEST,
                    requestId = request.requestId,
                    payloadBytes = request.payloadBytes
                ).copy(attempt = attempt)
                if (!RetryPolicy.shouldRetry(attempt, failure)) throw LlmDomainException(failure, error)
                delay(RetryPolicy.delayMillis(attempt, failure))
                attempt++
            }
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: java.io.IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response)
                else response.close()
            }
        })
    }

    private fun parseResponse(
        response: Response,
        request: CompletionRequest,
        attempt: Int
    ): ChatCompletionResult {
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw LlmDomainException(
                NetworkFailureClassifier.classifyHttp(
                    statusCode = response.code,
                    body = body,
                    headers = response.headers.toMultimap().flatMap { (key, values) ->
                        values.map { key to it }
                    }.toMap(),
                    requestId = request.requestId,
                    payloadBytes = request.payloadBytes,
                    stage = RequestStage.RESPONSE
                ).copy(attempt = attempt)
            )
        }
        try {
            val root = JSONObject(body)
            val usageObject = root.optJSONObject("usage")
            return ChatCompletionResult(
                content = root.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content"),
                usage = ChatCompletionUsage.from(
                    prompt = usageObject?.intOrNull("prompt_tokens"),
                    completion = usageObject?.intOrNull("completion_tokens"),
                    total = usageObject?.intOrNull("total_tokens")
                ),
                responseModel = root.optString("model").takeIf { it.isNotBlank() },
                requestId = root.optString("id").takeIf { it.isNotBlank() }
                    ?: response.header("x-request-id")?.takeIf { it.isNotBlank() }
                    ?: request.requestId
            )
        } catch (error: JSONException) {
            throw LlmDomainException(
                NetworkFailureClassifier.classifyMalformedJson(
                    requestId = request.requestId,
                    payloadBytes = request.payloadBytes
                ).copy(attempt = attempt),
                error
            )
        }
    }

    private fun openConnection(
        endpoint: String,
        apiKey: String,
        method: String,
        readTimeoutMillis: Int
    ): HttpURLConnection {
        return (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = readTimeoutMillis
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
    }

    private inline fun <T> HttpURLConnection.useResponse(
        bodyBytes: Int = 0,
        requestId: String? = null,
        attempt: Int = 1,
        block: (String) -> T
    ): T {
        return try {
            val code = responseCode
            val body = (if (code in 200..299) inputStream else errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw LlmDomainException(
                    NetworkFailureClassifier.classifyHttp(
                        statusCode = code,
                        body = body,
                        headers = headerFields.orEmpty().flatMap { (key, values) -> values.map { key.orEmpty() to it } }.toMap(),
                        requestId = requestId,
                        payloadBytes = bodyBytes,
                        stage = RequestStage.RESPONSE
                    ).copy(attempt = attempt)
                )
            }
            try {
                block(body)
            } catch (error: JSONException) {
                throw LlmDomainException(
                    NetworkFailureClassifier.classifyMalformedJson(
                        requestId = requestId,
                        payloadBytes = bodyBytes
                    ).copy(attempt = attempt),
                    error
                )
            }
        } finally {
            disconnect()
        }
    }

    private fun JSONObject.intOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private data class CompletionRequest(
        val request: Request,
        val payloadBytes: Int,
        val requestId: String
    )

    companion object {
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val MODEL_READ_TIMEOUT_MS = 30_000
        private const val ANALYSIS_READ_TIMEOUT_MS = 180_000
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun normalizeBaseUrl(value: String): String = ApiEndpointPolicy.normalize(value)
    }
}

class LlmDomainException(
    val failure: NetworkFailure,
    cause: Throwable? = null
) : Exception(failure.message, cause)

class LlmConnectionException(val statusCode: Int, message: String) : Exception("HTTP $statusCode：$message")

class LlmTimeoutException(message: String, cause: Throwable) : Exception(message, cause)
