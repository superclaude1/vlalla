package com.storybrain.app.tts

import java.io.File
import java.io.IOException
import com.storybrain.app.network.NetworkClients
import com.storybrain.app.network.awaitResponse
import com.storybrain.app.settings.EndpointPolicy
import com.storybrain.app.settings.OpenAiCompatibleClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException

class OpenAiTtsClient(
    baseUrl: String,
    private val client: OkHttpClient = NetworkClients.longRunning,
    allowInsecureHttp: Boolean = false
) {
    private val baseUrl = EndpointPolicy.requireAllowed(baseUrl, allowInsecureHttp)

    suspend fun listModels(apiKey: String): List<String> {
        val request = authorize(Request.Builder().url("$baseUrl/models"), apiKey).get().build()
        return client.newCall(request).awaitResponse().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw error(response.code, text)
            val data = JSONObject(text).optJSONArray("data") ?: JSONArray()
            (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id") }.filter(String::isNotBlank)
        }
    }

    suspend fun synthesize(request: TtsSynthesisRequest, apiKey: String, output: File) {
        require(request.text.length <= 4_096) { "兼容 TTS 单段不能超过4096字符" }
        require(request.voice.isNotBlank()) { "兼容 TTS 需要配置音色 ID" }
        val body = JSONObject()
            .put("model", request.model)
            .put("input", request.text)
            .put("voice", request.voice.removePrefix("openai:"))
            .put("response_format", request.format)
            .put("speed", (request.speed * request.directives.rate).coerceIn(0.25f, 4f))
        if (request.supportsInstructions) {
            TtsDirectiveRenderer.instructions(request.directives).takeIf(String::isNotBlank)?.let { body.put("instructions", it) }
        }
        val httpRequest = authorize(Request.Builder().url("$baseUrl/audio/speech"), apiKey)
            .apply { request.idempotencyKey?.let { header("Idempotency-Key", it) } }
            .post(body.toString().toRequestBody(JSON)).build()
        val response = try {
            client.newCall(httpRequest).awaitResponse()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            throw TtsProviderException(null, true, error.message ?: "兼容 TTS 网络连接失败")
        }
        response.use {
            if (!it.isSuccessful) throw error(it.code, it.body?.string(), it.header("Retry-After"))
            val contentType = it.header("Content-Type").orEmpty()
            if (!contentType.startsWith("audio/") && contentType != "application/octet-stream") {
                throw TtsProviderException(it.code, false, "兼容 TTS 未返回音频")
            }
            val part = File(output.parentFile, "${output.name}.part").apply { parentFile?.mkdirs() }
            it.body?.byteStream()?.use { input -> part.outputStream().use(input::copyTo) }
                ?: throw TtsProviderException(it.code, true, "兼容 TTS 返回空音频")
            if (part.length() == 0L) { part.delete(); throw TtsProviderException(it.code, true, "兼容 TTS 返回空音频") }
            if (output.exists()) output.delete()
            if (!part.renameTo(output)) { part.delete(); throw TtsProviderException(null, false, "无法保存兼容 TTS 音频") }
        }
    }

    private fun authorize(builder: Request.Builder, apiKey: String) =
        if (apiKey.isBlank()) builder else builder.header("Authorization", "Bearer $apiKey")

    private fun error(code: Int, text: String?, retryAfter: String? = null): TtsProviderException {
        val message = runCatching {
            JSONObject(text.orEmpty()).optJSONObject("error")?.optString("message")
                ?: JSONObject(text.orEmpty()).optString("message")
        }.getOrNull().orEmpty()
        return TtsProviderException(
            code,
            code == 408 || code == 429 || code >= 500,
            message.ifBlank { "兼容 TTS 请求失败（HTTP $code）" },
            OpenAiCompatibleClient.retryAfterMillis(retryAfter)
        )
    }

    private companion object { val JSON = "application/json; charset=utf-8".toMediaType() }
}
