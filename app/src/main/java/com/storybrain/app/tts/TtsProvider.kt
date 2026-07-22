package com.storybrain.app.tts

import java.io.File

data class TtsVoice(
    val id: String,
    val name: String,
    val language: String = "zh",
    val gender: String = "UNKNOWN",
    val ageGroup: String = "UNKNOWN",
    val tags: List<String> = emptyList(),
    val source: String = "PUBLIC",
    val state: String = "trained"
)

data class TtsDirectives(
    val emotion: String = "",
    val delivery: String = "",
    val pauseBeforeMs: Int = 0,
    val pauseAfterMs: Int = 0,
    val rate: Float = 1f,
    val volume: Float = 0f
)

data class TtsSynthesisRequest(
    val text: String,
    val voice: String,
    val model: String,
    val profileId: String = "",
    val language: String = "zh",
    val format: String = "mp3",
    val speed: Float = 1f,
    val directives: TtsDirectives = TtsDirectives(),
    val supportsInstructions: Boolean = false,
    val idempotencyKey: String? = null
)

interface TtsProvider {
    val id: String
    fun synthesize(request: TtsSynthesisRequest, output: File)
}

class EdgeTtsProvider(private val client: EdgeTtsClient = EdgeTtsClient()) : TtsProvider {
    override val id = "edge"
    override fun synthesize(request: TtsSynthesisRequest, output: File) {
        client.synthesize(request.text, request.voice.removePrefix("edge:"), request.directives, output)
    }
}

class FishAudioProvider(
    private val client: FishAudioClient,
    private val apiKey: String
) : TtsProvider {
    override val id = "fish"
    override fun synthesize(request: TtsSynthesisRequest, output: File) {
        client.synthesize(request, apiKey, output)
    }
}

class OpenAiCompatibleTtsProvider(
    private val client: OpenAiTtsClient,
    private val apiKey: String
) : TtsProvider {
    override val id = "openai-compatible"
    override fun synthesize(request: TtsSynthesisRequest, output: File) {
        client.synthesize(request, apiKey, output)
    }
}

class TtsProviderException(
    val statusCode: Int?,
    val retryable: Boolean,
    message: String
) : Exception(message)

object TtsDirectiveRenderer {
    private val supportedEmotions = setOf(
        "happy", "sad", "angry", "excited", "calm", "nervous", "surprised", "moved",
        "curious", "scared", "worried", "hopeful", "mysterious"
    )
    private val supportedDeliveries = setOf("soft tone", "whispering", "shouting", "in a hurry tone")

    fun fishText(text: String, directives: TtsDirectives): String = buildString {
        directives.emotion.trim().lowercase().takeIf(supportedEmotions::contains)?.let { append("[$it]") }
        directives.delivery.trim().lowercase().takeIf(supportedDeliveries::contains)?.let { append("[$it]") }
        if (directives.pauseBeforeMs >= 700) append("[long-break]")
        else if (directives.pauseBeforeMs >= 180) append("[break]")
        append(text)
        if (directives.pauseAfterMs >= 700) append("[long-break]")
        else if (directives.pauseAfterMs >= 180) append("[break]")
    }

    fun instructions(directives: TtsDirectives): String = buildList {
        directives.emotion.takeIf(String::isNotBlank)?.let { add("情绪：$it") }
        directives.delivery.takeIf(String::isNotBlank)?.let { add("语气：$it") }
        if (directives.pauseBeforeMs > 0) add("开头停顿${directives.pauseBeforeMs}毫秒")
        if (directives.pauseAfterMs > 0) add("结尾停顿${directives.pauseAfterMs}毫秒")
        if (directives.rate != 1f) add("语速${"%.2f".format(directives.rate)}倍")
    }.joinToString("；")
}
