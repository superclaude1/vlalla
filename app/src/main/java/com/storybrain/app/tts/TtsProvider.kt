package com.storybrain.app.tts

import android.annotation.TargetApi
import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

object AndroidSystemTtsVoiceSupport {
    fun isChinese(locale: Locale): Boolean = locale.language.equals("zh", ignoreCase = true)

    fun selectChineseVoice(voices: List<AndroidTtsVoiceDescriptor>, requestedId: String): AndroidTtsVoiceDescriptor? =
        voices.firstOrNull { it.id == requestedId && isChinese(Locale.forLanguageTag(it.localeTag)) }
            ?: voices.firstOrNull { isChinese(Locale.forLanguageTag(it.localeTag)) }
}

data class AndroidTtsVoiceDescriptor(val id: String, val localeTag: String)

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
    /** Requested provider encoding. Network providers must never receive the legacy `auto` value. */
    val format: String = "mp3",
    val speed: Float = 1f,
    val directives: TtsDirectives = TtsDirectives(),
    val supportsInstructions: Boolean = false,
    val idempotencyKey: String? = null
)

interface TtsProvider {
    val id: String
    fun synthesize(request: TtsSynthesisRequest, output: File): TtsAudioArtifact
}

/** The provider owns the encoded bytes. A destination filename is not a format declaration. */
data class TtsAudioArtifact(
    val file: File,
    val mimeType: String? = null,
    val format: String? = null
) {
    fun fileExtension(): String = format?.trim()?.lowercase()?.removePrefix(".")?.takeIf(String::isNotBlank)
        ?: mimeType?.substringAfter('/', "")?.lowercase()?.let {
            when (it) {
                "mpeg", "mp3" -> "mp3"
                "wav", "x-wav", "wave" -> "wav"
                "ogg", "opus" -> "ogg"
                "mp4", "m4a", "aac" -> "m4a"
                "flac" -> "flac"
                "webm" -> "webm"
                "l16", "pcm" -> "pcm"
                else -> null
            }
        }
        ?: "bin"
}

/**
 * Runs legacy synchronous providers off the caller thread. Cancellation is observed before and
 * after the call; an already executing synchronous provider cannot be interrupted until it returns.
 */
internal suspend fun synthesizeCancellably(
    provider: TtsProvider,
    request: TtsSynthesisRequest,
    output: File
): TtsAudioArtifact = withContext(Dispatchers.IO) {
    coroutineContext.ensureActive()
    try {
        provider.synthesize(request, output).also { coroutineContext.ensureActive() }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        output.delete()
        File(output.parentFile, "${output.name}.part").delete()
        throw cancelled
    }
}

internal fun ttsCacheIdentity(apiProfileId: String, baseUrl: String, model: String): String =
    listOf(apiProfileId.trim(), baseUrl.trim().trimEnd('/'), model.trim()).joinToString("|")

internal fun artifactForAudio(file: File, contentType: String?): TtsAudioArtifact {
    val mimeType = contentType?.substringBefore(';')?.trim()?.lowercase()?.takeIf(String::isNotBlank)
    return TtsAudioArtifact(file, mimeType, when (mimeType) {
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
        "audio/ogg", "audio/opus" -> "ogg"
        "audio/mp4", "audio/m4a", "audio/aac" -> "m4a"
        "audio/flac" -> "flac"
        "audio/webm" -> "webm"
        "audio/l16", "audio/pcm" -> "pcm"
        else -> null
    })
}

class EdgeTtsProvider(private val client: EdgeTtsClient = EdgeTtsClient()) : TtsProvider {
    override val id = "edge"
    override fun synthesize(request: TtsSynthesisRequest, output: File): TtsAudioArtifact {
        client.synthesize(request.text, request.voice.removePrefix("edge:"), request.directives, output)
        return TtsAudioArtifact(output, "audio/mpeg", "mp3")
    }
}

class FishAudioProvider(
    private val client: FishAudioClient,
    private val apiKey: String
) : TtsProvider {
    override val id = "fish"
    override fun synthesize(request: TtsSynthesisRequest, output: File): TtsAudioArtifact =
        client.synthesize(request, apiKey, output)
}

class OpenAiCompatibleTtsProvider(
    private val client: OpenAiTtsClient,
    private val apiKey: String
) : TtsProvider {
    override val id = "openai-compatible"
    override fun synthesize(request: TtsSynthesisRequest, output: File): TtsAudioArtifact =
        client.synthesize(request, apiKey, output)
}

class TtsProviderException(
    val statusCode: Int?,
    val retryable: Boolean,
    message: String
) : Exception(message)

/** Android's installed TTS engine. The output is the WAV produced by synthesizeToFile. */
class AndroidSystemTtsProvider(
    private val context: Context,
    private val timeoutSeconds: Long = 90L
) : TtsProvider {
    override val id = "android-system"

    @TargetApi(30)
    override fun synthesize(request: TtsSynthesisRequest, output: File): TtsAudioArtifact {
        require(request.text.isNotBlank()) { "配音文本不能为空" }
        output.parentFile?.mkdirs()
        val temporary = File(output.parentFile, ".${output.name}.${UUID.randomUUID()}.wav")
        val init = CountDownLatch(1)
        var initStatus = TextToSpeech.ERROR
        val tts = TextToSpeech(context.applicationContext) { status ->
            initStatus = status
            init.countDown()
        }
        try {
            check(init.await(timeoutSeconds, TimeUnit.SECONDS)) { "Android 系统 TTS 初始化超时" }
            check(initStatus == TextToSpeech.SUCCESS) { "Android 系统 TTS 初始化失败" }
            val voices = tts.voices.orEmpty()
            val selected = AndroidSystemTtsVoiceSupport.selectChineseVoice(
                voices.map { AndroidTtsVoiceDescriptor(it.name, it.locale.toLanguageTag()) },
                request.voice.removePrefix("android:")
            ) ?: error("系统未安装中文 TTS 音色")
            val androidVoice = voices.first { it.name == selected.id }
            check(tts.setVoice(androidVoice) == TextToSpeech.SUCCESS) { "系统中文 TTS 音色不可用" }
            tts.setSpeechRate((request.speed * request.directives.rate).coerceIn(0.1f, 4f))
            val done = CountDownLatch(1)
            var failure: String? = null
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit
                override fun onDone(utteranceId: String) { done.countDown() }
                override fun onError(utteranceId: String) {
                    failure = "系统 TTS 合成失败"
                    done.countDown()
                }
                override fun onError(utteranceId: String, errorCode: Int) {
                    failure = "系统 TTS 合成失败（$errorCode）"
                    done.countDown()
                }
            })
            val descriptor = ParcelFileDescriptor.open(
                temporary,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY
            )
            descriptor.use {
                val status = tts.synthesizeToFile(request.text, Bundle(), it, "tts-${UUID.randomUUID()}")
                check(status == TextToSpeech.SUCCESS) { "系统 TTS 拒绝合成请求" }
            }
            check(done.await(timeoutSeconds, TimeUnit.SECONDS)) { "Android 系统 TTS 合成超时" }
            check(failure == null) { failure!! }
            check(temporary.exists() && temporary.length() > 0L) { "系统 TTS 返回空音频" }
            if (output.exists()) check(output.delete()) { "无法替换系统 TTS 音频" }
            check(temporary.renameTo(output)) { "无法保存系统 TTS 音频" }
            return artifactForAudio(output, "audio/wav")
        } finally {
            tts.shutdown()
            if (temporary.exists()) temporary.delete()
        }
    }
}

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
