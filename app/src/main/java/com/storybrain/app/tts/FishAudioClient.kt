package com.storybrain.app.tts

import com.storybrain.app.settings.ApiEndpointPolicy
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class FishVoicePage(val total: Int, val voices: List<TtsVoice>)

class FishAudioClient(
    baseUrl: String = "https://api.fish.audio",
    allowInsecureForTests: Boolean = false,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val baseUrl = if (allowInsecureForTests) {
        baseUrl.trim().trimEnd('/')
    } else {
        ApiEndpointPolicy.normalize(baseUrl)
    }

    fun listModels(): List<String> = listOf("s2.1-pro-free", "s2.1-pro", "s2-pro", "s1")

    fun listVoices(
        apiKey: String,
        self: Boolean,
        query: String = "",
        language: String = "zh",
        page: Int = 1,
        pageSize: Int = 20
    ): FishVoicePage {
        val url = "$baseUrl/model".toHttpUrl().newBuilder()
            .addQueryParameter("page_size", pageSize.coerceIn(1, 50).toString())
            .addQueryParameter("page_number", page.coerceAtLeast(1).toString())
            .addQueryParameter("self", self.toString())
            .addQueryParameter("sort_by", if (self) "created_at" else "task_count")
            .apply {
                if (query.isNotBlank()) addQueryParameter("title", query.trim())
                if (language.isNotBlank()) addQueryParameter("language", language)
            }.build()
        val body = executeJson(authorize(Request.Builder().url(url), apiKey).get().build())
        val data = body.optJSONArray("items") ?: JSONArray()
        val voices = (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val state = item.optString("state")
            if (state != "trained") return@mapNotNull null
            val tags = item.optJSONArray("tags") ?: JSONArray()
            val languages = item.optJSONArray("languages") ?: JSONArray()
            val tagList = (0 until tags.length()).map(tags::optString).filter(String::isNotBlank)
            TtsVoice(
                id = item.optString("_id"),
                name = item.optString("title").ifBlank { "未命名音色" },
                language = (0 until languages.length()).map(languages::optString).firstOrNull().orEmpty().ifBlank { language },
                gender = when {
                    tagList.any { it.equals("female", true) } -> "FEMALE"
                    tagList.any { it.equals("male", true) } -> "MALE"
                    else -> "UNKNOWN"
                },
                ageGroup = tagList.firstOrNull { it.contains("young", true) || it.contains("middle", true) || it.contains("old", true) }.orEmpty(),
                tags = tagList,
                source = if (self) "OWNED" else "PUBLIC",
                state = state
            )
        }.filter { it.id.isNotBlank() }
        return FishVoicePage(body.optInt("total", voices.size), voices)
    }

    fun test(apiKey: String): Int = listVoices(apiKey, self = true, pageSize = 1).total

    fun synthesize(request: TtsSynthesisRequest, apiKey: String, output: File): TtsAudioArtifact {
        require(request.voice.isNotBlank()) { "Fish Audio 需要选择音色" }
        val directedText = TtsDirectiveRenderer.fishText(request.text, request.directives)
        val body = JSONObject()
            .put("text", directedText)
            .put("reference_id", request.voice.removePrefix("fish:"))
            .put("format", request.format.takeUnless { it.equals("auto", ignoreCase = true) }.orEmpty().ifBlank { "mp3" })
            .put("normalize", true)
            .put("latency", "normal")
            .put("prosody", JSONObject()
                .put("speed", (request.speed * request.directives.rate).coerceIn(0.5f, 2f))
                .put("volume", request.directives.volume.coerceIn(-20f, 20f))
                .put("normalize_loudness", true))
        val httpRequest = authorize(Request.Builder().url("$baseUrl/v1/tts"), apiKey)
            .header("model", request.model)
            .post(body.toString().toRequestBody(JSON))
            .build()
        return download(httpRequest, output)
    }

    private fun download(request: Request, output: File): TtsAudioArtifact =
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw error(response.code, response.body?.string())
            val contentType = response.header("Content-Type").orEmpty()
            if (!contentType.startsWith("audio/")) throw TtsProviderException(response.code, false, "Fish Audio 未返回音频")
            val part = File(output.parentFile, "${output.name}.part").apply { parentFile?.mkdirs() }
            response.body?.byteStream()?.use { input -> part.outputStream().use(input::copyTo) }
                ?: throw TtsProviderException(response.code, true, "Fish Audio 返回空音频")
            if (part.length() == 0L) { part.delete(); throw TtsProviderException(response.code, true, "Fish Audio 返回空音频") }
            if (output.exists()) output.delete()
            if (!part.renameTo(output)) { part.delete(); throw TtsProviderException(null, false, "无法保存 Fish Audio 音频") }
            artifactForAudio(output, contentType)
        }

    private fun executeJson(request: Request): JSONObject = client.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw error(response.code, text)
        JSONObject(text.ifBlank { "{}" })
    }

    private fun authorize(builder: Request.Builder, apiKey: String) =
        builder.header("Authorization", "Bearer $apiKey")

    private fun error(code: Int, text: String?): TtsProviderException {
        val message = runCatching { JSONObject(text.orEmpty()).optString("message") }.getOrNull().orEmpty()
        val display = when (code) {
            401 -> "Fish Audio API Key 无效"
            402 -> "Fish Audio 余额不足"
            429 -> "Fish Audio 请求过多，请稍后重试"
            422 -> "Fish Audio 拒绝了配音参数"
            else -> message.ifBlank { "Fish Audio 请求失败（HTTP $code）" }
        }
        return TtsProviderException(code, code == 429 || code >= 500, display)
    }

    private companion object { val JSON = "application/json; charset=utf-8".toMediaType() }
}
