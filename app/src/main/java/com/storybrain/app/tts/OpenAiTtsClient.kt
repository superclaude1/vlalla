package com.storybrain.app.tts

import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OpenAiTtsClient(
    baseUrl: String,
    allowInsecureForTests: Boolean = false,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val baseUrl = baseUrl.trim().trimEnd('/').also {
        require(allowInsecureForTests || it.startsWith("https://", true)) { "兼容 TTS URL 必须使用 HTTPS" }
    }

    fun listModels(apiKey: String): List<String> {
        val request = authorize(Request.Builder().url("$baseUrl/models"), apiKey).get().build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw error(response.code, text)
            val data = JSONObject(text).optJSONArray("data") ?: JSONArray()
            (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id") }.filter(String::isNotBlank)
        }
    }

    fun synthesize(request: TtsSynthesisRequest, apiKey: String, output: File): TtsAudioArtifact {
        require(request.text.length <= 4_096) { "兼容 TTS 单段不能超过4096字符" }
        require(request.voice.isNotBlank()) { "兼容 TTS 需要配置音色 ID" }
        val body = JSONObject()
            .put("model", request.model)
            .put("input", request.text)
            .put("voice", request.voice.removePrefix("openai:"))
            .put("response_format", request.format.takeUnless { it.equals("auto", ignoreCase = true) }.orEmpty().ifBlank { "mp3" })
            .put("speed", (request.speed * request.directives.rate).coerceIn(0.25f, 4f))
        if (request.supportsInstructions) {
            TtsDirectiveRenderer.instructions(request.directives).takeIf(String::isNotBlank)?.let { body.put("instructions", it) }
        }
        val httpRequest = authorize(Request.Builder().url("$baseUrl/audio/speech"), apiKey)
            .post(body.toString().toRequestBody(JSON)).build()
        return client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) throw error(response.code, response.body?.string())
            val contentType = response.header("Content-Type").orEmpty()
            if (!contentType.startsWith("audio/") && contentType != "application/octet-stream") {
                throw TtsProviderException(response.code, false, "兼容 TTS 未返回音频")
            }
            val part = File(output.parentFile, "${output.name}.part").apply { parentFile?.mkdirs() }
            response.body?.byteStream()?.use { input -> part.outputStream().use(input::copyTo) }
                ?: throw TtsProviderException(response.code, true, "兼容 TTS 返回空音频")
            if (part.length() == 0L) { part.delete(); throw TtsProviderException(response.code, true, "兼容 TTS 返回空音频") }
            if (output.exists()) output.delete()
            if (!part.renameTo(output)) { part.delete(); throw TtsProviderException(null, false, "无法保存兼容 TTS 音频") }
            if (contentType.substringBefore(';').trim().equals("application/octet-stream", ignoreCase = true)) {
                artifactForAudio(output, mimeForFormat(request.format))
            } else {
                artifactForAudio(output, contentType)
            }
        }
    }

    private fun authorize(builder: Request.Builder, apiKey: String) =
        if (apiKey.isBlank()) builder else builder.header("Authorization", "Bearer $apiKey")

    private fun error(code: Int, text: String?): TtsProviderException {
        val message = runCatching {
            JSONObject(text.orEmpty()).optJSONObject("error")?.optString("message")
                ?: JSONObject(text.orEmpty()).optString("message")
        }.getOrNull().orEmpty()
        return TtsProviderException(code, code == 429 || code >= 500, message.ifBlank { "兼容 TTS 请求失败（HTTP $code）" })
    }

    private fun effectiveFormat(format: String): String =
        format.takeUnless { it.equals("auto", ignoreCase = true) }.orEmpty().ifBlank { "mp3" }

    private fun mimeForFormat(format: String): String = when (effectiveFormat(format).lowercase()) {
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        "aac", "m4a" -> "audio/aac"
        "flac" -> "audio/flac"
        "webm" -> "audio/webm"
        "pcm" -> "audio/pcm"
        else -> "audio/mpeg"
    }

    private companion object { val JSON = "application/json; charset=utf-8".toMediaType() }
}
