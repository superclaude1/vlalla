package com.storybrain.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.storybrain.app.data.StoryCharacterEntity
import com.storybrain.app.data.StoryRepository
import com.storybrain.app.data.TtsProviderKind
import com.storybrain.app.settings.TtsSettingsStore
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * 单块点读：对话块点击 → 按说话人解析音色 → 合成（内容哈希缓存 + 引擎降级）→ 播放。
 * 同一时间只播一块；新块自动停止旧块。
 */
class BlockSpeakController(
    private val context: Context,
    private val repository: StoryRepository,
    private val settings: TtsSettingsStore,
    private val resolver: VoiceResolver,
    private val edgeProvider: TtsProvider = EdgeTtsProvider(),
    private val androidSystemProvider: TtsProvider = AndroidSystemTtsProvider(context)
) {
    private var player: MediaPlayer? = null

    suspend fun speak(
        bookId: String,
        speaker: String?,
        character: StoryCharacterEntity?,
        text: String,
        onFinished: () -> Unit
    ): Result<Unit> = runCatching {
        stop()
        val resolved = resolver.resolve(bookId, speaker, character)
        val cacheKey = sha256("${resolved.profile.id}|${resolved.voiceId}|$text|block-v1")
        val cache = File(context.filesDir, "tts/block-cache").apply { mkdirs() }
        val cached = cache.listFiles()?.firstOrNull { it.name.startsWith("$cacheKey.") && it.length() > 0L }
        val artifact = if (cached != null) {
            TtsAudioArtifact(cached)
        } else {
            synthesizeWithFallback(resolved, text, File(cache, "$cacheKey.audio"))
        }
        playArtifact(normalizeCacheFile(artifact, cacheKey, cache), onFinished)
    }

    private fun normalizeCacheFile(artifact: TtsAudioArtifact, cacheKey: String, cache: File): File {
        val destination = File(cache, "$cacheKey.${artifact.fileExtension()}")
        if (artifact.file.absolutePath != destination.absolutePath) {
            artifact.file.copyTo(destination, overwrite = true)
            artifact.file.delete()
        }
        return destination
    }

    private suspend fun synthesizeWithFallback(
        resolved: ResolvedTtsVoice,
        text: String,
        output: File
    ): TtsAudioArtifact {
        val kind = TtsProviderKind.valueOf(resolved.profile.kind)
        val primary = try {
            synthesizeWithRetry(providerFor(kind, resolved), resolved, text, output)
        } catch (primaryError: Throwable) {
            if (primaryError is CancellationException) throw primaryError
            val networkFailure = primaryError is IOException ||
                (primaryError is TtsProviderException && primaryError.retryable) ||
                (primaryError is EdgeTtsException && primaryError.retryable)
            if (!networkFailure || kind == TtsProviderKind.ANDROID_SYSTEM) throw primaryError
            // 网络失败降级到 Android 系统 TTS（离线可用）
            synthesizeCancellably(
                androidSystemProvider,
                TtsSynthesisRequest(
                    text = text,
                    voice = VoiceResolver.ANDROID_SYSTEM_VOICE_ID,
                    model = "",
                    profileId = com.storybrain.app.data.TtsProfileIds.ANDROID_SYSTEM
                ),
                output
            )
        }
        return primary
    }

    private suspend fun synthesizeWithRetry(
        provider: TtsProvider,
        resolved: ResolvedTtsVoice,
        text: String,
        output: File
    ): TtsAudioArtifact {
        val request = TtsSynthesisRequest(
            text = text,
            voice = resolved.voiceId,
            model = resolved.profile.model,
            profileId = resolved.profile.id,
            supportsInstructions = resolved.profile.supportsInstructions
        )
        var last: Throwable? = null
        repeat(2) { attempt ->
            currentCoroutineContext().ensureActive()
            try {
                return synthesizeCancellably(provider, request, output)
            } catch (error: Throwable) {
                last = error
                val retryable = when (error) {
                    is TtsProviderException -> error.retryable
                    is EdgeTtsException -> error.retryable
                    else -> false
                }
                if (!retryable || attempt == 1) throw error
                delay(400L * (1 shl attempt))
            }
        }
        throw last ?: error("配音生成失败")
    }

    private fun providerFor(kind: TtsProviderKind, resolved: ResolvedTtsVoice): TtsProvider = when (kind) {
        TtsProviderKind.EDGE -> edgeProvider
        TtsProviderKind.ANDROID_SYSTEM -> androidSystemProvider
        TtsProviderKind.FISH_AUDIO -> FishAudioProvider(
            FishAudioClient(resolved.profile.baseUrl),
            settings.readApiKey(resolved.profile.id)
        )
        TtsProviderKind.OPENAI_COMPATIBLE -> OpenAiCompatibleTtsProvider(
            OpenAiTtsClient(resolved.profile.baseUrl),
            settings.readApiKey(resolved.profile.id)
        )
    }

    private fun playArtifact(file: File, onFinished: () -> Unit) {
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(file.absolutePath)
            setOnPreparedListener { it.start() }
            setOnCompletionListener {
                it.release()
                player = null
                onFinished()
            }
            setOnErrorListener { mediaPlayer, _, _ ->
                mediaPlayer.release()
                player = null
                onFinished()
                true
            }
            prepareAsync()
        }
    }

    fun stop() {
        player?.runCatching { stop() }
        player?.release()
        player = null
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
