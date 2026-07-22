package com.storybrain.app.settings

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.SocketTimeoutException
import org.json.JSONArray
import org.json.JSONObject

data class LlmMessage(val role: String, val content: String)

class OpenAiCompatibleClient {
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
        } catch (error: SocketTimeoutException) {
            throw LlmTimeoutException("模型检测超过 30 秒。请检查网络或 API 地址后重试。", error)
        } finally {
            connection.disconnect()
        }
    }

    fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<LlmMessage>,
        temperature: Double = 0.2,
        jsonMode: Boolean = false
    ): String {
        val payload = JSONObject()
            .put("model", model)
            .put("temperature", temperature)
            .put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().put("role", message.role).put("content", message.content))
                }
            })
        if (jsonMode) payload.put("response_format", JSONObject().put("type", "json_object"))
        val connection = openConnection(
            "${normalizeBaseUrl(baseUrl)}/chat/completions",
            apiKey,
            "POST",
            readTimeoutMillis = ANALYSIS_READ_TIMEOUT_MS
        )
        return try {
            connection.doOutput = true
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            connection.useResponse { body ->
                JSONObject(body)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        } catch (error: SocketTimeoutException) {
            throw LlmTimeoutException(
                "LLM 分析超过 3 分钟。服务器可能繁忙，请稍后再次点击分析；已完成的批次不会丢失。",
                error
            )
        } finally {
            connection.disconnect()
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

    private inline fun <T> HttpURLConnection.useResponse(block: (String) -> T): T {
        return try {
            val code = responseCode
            val body = (if (code in 200..299) inputStream else errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull().takeUnless { it.isNullOrBlank() } ?: body.take(300)
                throw LlmConnectionException(code, message.ifBlank { "服务返回空错误信息" })
            }
            block(body)
        } finally {
            disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val MODEL_READ_TIMEOUT_MS = 30_000
        private const val ANALYSIS_READ_TIMEOUT_MS = 180_000

        fun normalizeBaseUrl(value: String): String {
            var url = value.trim().trimEnd('/')
            require(url.isNotBlank()) { "请输入 API URL" }
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            val uri = URI(url)
            require(uri.host != null) { "API URL 格式不正确" }
            if (uri.host.equals("api.openai.com", ignoreCase = true) && uri.path.trim('/').isBlank()) {
                url += "/v1"
            }
            return url
        }
    }
}

class LlmConnectionException(val statusCode: Int, message: String) : Exception("HTTP $statusCode：$message")

class LlmTimeoutException(message: String, cause: Throwable) : Exception(message, cause)
