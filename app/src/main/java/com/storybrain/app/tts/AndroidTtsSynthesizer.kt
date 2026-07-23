package com.storybrain.app.tts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine

class SystemTtsException(
    message: String,
    val missingVoiceData: Boolean = false
) : IllegalStateException(message)

/**
 * Cancellable Android TTS file synthesizer used as the immediate, offline-safe narration path.
 * Media3 still owns playback, audio focus, lock-screen controls and the final queue.
 */
class AndroidTtsSynthesizer(context: Context) {
    private val appContext = context.applicationContext
    private val initMutex = Mutex()
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
    @Volatile private var engine: TextToSpeech? = null
    @Volatile private var chineseVoiceReady = false

    /** Starts the platform engine before the user presses play, avoiding cold-start silence. */
    suspend fun warmUp() {
        configureChineseVoice(getEngine())
    }

    suspend fun synthesize(text: String, output: File) {
        require(text.isNotBlank()) { "朗读文本不能为空" }
        if (output.isFile && output.length() > MIN_AUDIO_BYTES) return
        val tts = getEngine()
        configureChineseVoice(tts)

        output.parentFile?.mkdirs()
        val temporary = File(output.parentFile, "${output.name}.part")
        temporary.delete()
        val utteranceId = "zhangjing-${UUID.randomUUID()}"
        suspendCancellableCoroutine { continuation ->
            pending[utteranceId] = continuation
            continuation.invokeOnCancellation {
                pending.remove(utteranceId)
                tts.stop()
                temporary.delete()
            }
            val result = tts.synthesizeToFile(text, Bundle(), temporary, utteranceId)
            if (result != TextToSpeech.SUCCESS && pending.remove(utteranceId) != null) {
                temporary.delete()
                continuation.resumeWithException(SystemTtsException("系统朗读引擎拒绝了合成请求"))
            }
        }
        if (!temporary.isFile || temporary.length() <= MIN_AUDIO_BYTES) {
            temporary.delete()
            throw SystemTtsException("系统朗读没有生成可播放音频")
        }
        if (output.exists() && !output.delete()) {
            temporary.delete()
            throw SystemTtsException("无法替换系统朗读缓存")
        }
        if (!temporary.renameTo(output)) {
            temporary.delete()
            throw SystemTtsException("无法保存系统朗读缓存")
        }
    }

    @Synchronized
    private fun configureChineseVoice(tts: TextToSpeech) {
        if (chineseVoiceReady) return
        val languageResult = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA) {
            throw SystemTtsException("缺少中文语音数据，请安装后重试", missingVoiceData = true)
        }
        if (languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            throw SystemTtsException("当前系统朗读引擎不支持中文", missingVoiceData = true)
        }
        tts.voices
            ?.filter { it.locale.language.equals("zh", ignoreCase = true) }
            ?.sortedBy { it.isNetworkConnectionRequired }
            ?.firstOrNull()
            ?.let { tts.voice = it }
        chineseVoiceReady = true
    }

    fun shutdown() {
        pending.values.forEach { it.cancel() }
        pending.clear()
        engine?.shutdown()
        engine = null
        chineseVoiceReady = false
    }

    fun installVoiceDataIntent(): Intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)

    private suspend fun getEngine(): TextToSpeech = initMutex.withLock {
        engine ?: createEngine().also { created ->
            created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let(pending::remove)?.let { continuation ->
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = fail(utteranceId, "系统朗读合成失败")

                override fun onError(utteranceId: String?, errorCode: Int) =
                    fail(utteranceId, "系统朗读合成失败（$errorCode）")

                private fun fail(utteranceId: String?, message: String) {
                    utteranceId?.let(pending::remove)?.let { continuation ->
                        if (continuation.isActive) continuation.resumeWithException(SystemTtsException(message))
                    }
                }
            })
            engine = created
        }
    }

    private suspend fun createEngine(): TextToSpeech = suspendCancellableCoroutine { continuation ->
        var candidate: TextToSpeech? = null
        candidate = TextToSpeech(appContext) { status ->
            val created = candidate
            if (!continuation.isActive) {
                created?.shutdown()
            } else if (status == TextToSpeech.SUCCESS && created != null) {
                continuation.resume(created)
            } else {
                created?.shutdown()
                continuation.resumeWithException(SystemTtsException("无法启动系统朗读引擎", missingVoiceData = true))
            }
        }
        continuation.invokeOnCancellation { candidate?.shutdown() }
    }

    private companion object {
        const val MIN_AUDIO_BYTES = 128L
    }
}
