package com.storybrain.app.settings

import com.storybrain.app.network.NetworkClients
import com.storybrain.app.network.ProviderFailure
import com.storybrain.app.network.awaitResponse
import java.net.URI
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class LlmMessage(val role: String, val content: String)

class OpenAiCompatibleClient(
    private val modelClient: OkHttpClient = NetworkClients.standard,
    private val completionClient: OkHttpClient = NetworkClients.longRunning
) {
    suspend fun listModels(baseUrl: String, apiKey: String, allowInsecureHttp: Boolean = false): List<String> {
        val endpoint = EndpointPolicy.requireAllowed(baseUrl, allowInsecureHttp)
        val request = authorize(Request.Builder().url("$endpoint/models"), apiKey).get().build()
        return execute(modelClient, request, "模型检测超过 30 秒。请检查网络或 API 地址后重试。") { body ->
            val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
            buildList {
                for (index in 0 until data.length()) {
                    data.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
    }

    suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<LlmMessage>,
        temperature: Double = 0.2,
        jsonMode: Boolean = false,
        allowInsecureHttp: Boolean = false
    ): String {
        val endpoint = EndpointPolicy.requireAllowed(baseUrl, allowInsecureHttp)
        val payload = JSONObject()
            .put("model", model)
            .put("temperature", temperature)
            .put("messages", JSONArray().apply {
                messages.forEach { message -> put(JSONObject().put("role", message.role).put("content", message.content)) }
            })
        if (jsonMode) payload.put("response_format", JSONObject().put("type", "json_object"))
        val request = authorize(Request.Builder().url("$endpoint/chat/completions"), apiKey)
            .post(payload.toString().toRequestBody(JSON))
            .build()
        return execute(completionClient, request, "LLM 分析超过 3 分钟。服务器可能繁忙，请稍后重试。") { body ->
            runCatching {
                JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            }.getOrElse { throw ProviderFailure.InvalidResponse("服务响应中缺少有效的对话内容") }
        }
    }

    private suspend fun <T> execute(client: OkHttpClient, request: Request, timeoutMessage: String, parse: (String) -> T): T {
        val response = try {
            client.newCall(request).awaitResponse()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (timeout: SocketTimeoutException) {
            throw LlmTimeoutException(timeoutMessage, timeout)
        } catch (error: java.io.IOException) {
            throw ProviderFailure.Network(error.message ?: "网络连接失败", error)
        }
        return response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val message = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }
                    .getOrNull().takeUnless { value -> value.isNullOrBlank() } ?: body.take(300)
                throw LlmConnectionException(it.code, message.ifBlank { "服务返回空错误信息" }, retryAfterMillis(it.header("Retry-After")))
            }
            parse(body)
        }
    }

    private fun authorize(builder: Request.Builder, apiKey: String) =
        if (apiKey.isBlank()) builder else builder.header("Authorization", "Bearer $apiKey")

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun normalizeBaseUrl(value: String): String {
            var url = value.trim().trimEnd('/')
            require(url.isNotBlank()) { "请输入 API URL" }
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            val uri = URI(url)
            require(uri.host != null) { "API URL 格式不正确" }
            if (uri.host.equals("api.openai.com", ignoreCase = true) && uri.path.trim('/').isBlank()) url += "/v1"
            return url
        }

        internal fun retryAfterMillis(value: String?): Long? = value?.trim()?.toLongOrNull()?.times(1_000L)
    }
}

class LlmConnectionException(
    val statusCode: Int,
    message: String,
    val retryAfterMillis: Long? = null
) : ProviderFailure(
    "HTTP $statusCode：$message",
    retryable = statusCode == 408 || statusCode == 429 || statusCode >= 500
)

class LlmTimeoutException(message: String, cause: Throwable) : ProviderFailure.Timeout(message, cause)
