package com.storybrain.app.tts

import android.media.MediaExtractor
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import com.storybrain.app.network.NetworkClients
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/** Direct Android implementation of the Microsoft Edge online TTS protocol. */
enum class EdgeTransferStage(val label: String) {
    CONNECTING("连接服务"),
    WAITING_FIRST_FRAME("等待首帧"),
    RECEIVING_AUDIO("接收音频"),
    SAVING_FILE("保存文件")
}

class EdgeTtsClient {
    private val client: OkHttpClient = NetworkClients.webSocket

    suspend fun synthesize(text: String, voice: String, output: File) {
        synthesize(text, voice, TtsDirectives(), output) { }
    }

    suspend fun synthesize(text: String, voice: String, directives: TtsDirectives, output: File) {
        synthesize(text, voice, directives, output) { }
    }

    suspend fun synthesize(
        text: String,
        voice: String,
        directives: TtsDirectives,
        output: File,
        onStage: (EdgeTransferStage) -> Unit
    ) {
        require(text.isNotBlank()) { "配音文本不能为空" }
        require(text.length <= MAX_PAYLOAD_CHARS) { "Edge 单段不能超过 $MAX_PAYLOAD_CHARS 字" }
        onStage(EdgeTransferStage.CONNECTING)
        val bytes = try {
            withTimeout(TOTAL_TIMEOUT_MS) { requestAudio(text, voice, directives, onStage) }
        } catch (timeout: TimeoutCancellationException) {
            throw EdgeTtsException(EdgeTransferStage.RECEIVING_AUDIO, "Edge TTS 单段总时限 30 秒已到")
        }
        if (!looksLikeMp3(bytes)) throw EdgeTtsException(EdgeTransferStage.RECEIVING_AUDIO, "Edge TTS 返回了空文件或不可识别音频")
        onStage(EdgeTransferStage.SAVING_FILE)
        output.parentFile?.mkdirs()
        val temporary = File(output.parentFile, "${output.name}.part")
        runCatching { temporary.writeBytes(bytes) }.getOrElse {
            temporary.delete()
            throw EdgeTtsException(EdgeTransferStage.SAVING_FILE, "无法写入配音临时文件：${it.message}")
        }
        if (!isDecodableAudio(temporary)) {
            temporary.delete()
            throw EdgeTtsException(EdgeTransferStage.RECEIVING_AUDIO, "音频文件无法解码，可能在传输中被截断")
        }
        if (output.exists() && !output.delete()) {
            temporary.delete()
            throw EdgeTtsException(EdgeTransferStage.SAVING_FILE, "无法替换旧配音缓存")
        }
        if (!temporary.renameTo(output)) {
            temporary.delete()
            throw EdgeTtsException(EdgeTransferStage.SAVING_FILE, "无法保存配音文件")
        }
    }

    private suspend fun requestAudio(
        text: String,
        voice: String,
        directives: TtsDirectives,
        onStage: (EdgeTransferStage) -> Unit
    ): ByteArray =
        suspendCancellableCoroutine { continuation ->
        val requestId = connectionId()
        val url = "$WSS_URL&ConnectionId=${connectionId()}" +
            "&Sec-MS-GEC=${generateSecMsGec()}&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Origin", ORIGIN)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Cookie", "muid=${randomMuid()};")
            .build()
        val audio = ByteArrayOutputStream()
        var finished = false
        val receivedFirstFrame = AtomicBoolean(false)
        var firstFrameTimeout: Job? = null
        val timeoutScope = CoroutineScope(Dispatchers.IO)

        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStage(EdgeTransferStage.WAITING_FIRST_FRAME)
                firstFrameTimeout = timeoutScope.launch {
                    delay(FIRST_FRAME_TIMEOUT_MS)
                    if (!receivedFirstFrame.get() && continuation.isActive) {
                        webSocket.cancel()
                        continuation.resumeWithException(
                            EdgeTtsException(EdgeTransferStage.WAITING_FIRST_FRAME, "连接成功，但 12 秒内没有收到首个音频帧")
                        )
                    }
                }
                val timestamp = javascriptDate()
                webSocket.send(
                    "X-Timestamp:$timestamp\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":" +
                        "{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"}," +
                        "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n"
                )
                val ssml = ssml(text, voice, directives)
                webSocket.send(
                    "X-RequestId:$requestId\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${timestamp}Z\r\nPath:ssml\r\n\r\n$ssml"
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end", ignoreCase = true)) {
                    finished = true
                    firstFrameTimeout?.cancel()
                    webSocket.close(1000, "completed")
                    val result = audio.toByteArray()
                    if (continuation.isActive) {
                        if (looksLikeMp3(result)) continuation.resume(result)
                        else continuation.resumeWithException(
                            EdgeTtsException(EdgeTransferStage.RECEIVING_AUDIO, "服务正常结束，但没有可解码的音频")
                        )
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val frame = bytes.toByteArray()
                if (frame.size < 2) return
                val headerLength = ((frame[0].toInt() and 0xff) shl 8) or (frame[1].toInt() and 0xff)
                if (headerLength <= 0 || frame.size < headerLength + 2) return
                val header = String(frame, 2, headerLength, Charsets.UTF_8)
                if (header.contains("Path:audio", ignoreCase = true) && frame.size > headerLength + 2) {
                    if (receivedFirstFrame.compareAndSet(false, true)) {
                        firstFrameTimeout?.cancel()
                        onStage(EdgeTransferStage.RECEIVING_AUDIO)
                    }
                    audio.write(frame, headerLength + 2, frame.size - headerLength - 2)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                firstFrameTimeout?.cancel()
                val failure = EdgeTtsException(
                    if (receivedFirstFrame.get()) EdgeTransferStage.RECEIVING_AUDIO else EdgeTransferStage.CONNECTING,
                    if (response != null) "Edge TTS 连接失败（HTTP ${response.code}）" else
                        (t.message ?: "Edge TTS 网络连接失败")
                )
                if (continuation.isActive) continuation.resumeWithException(failure)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                firstFrameTimeout?.cancel()
                if (!finished && continuation.isActive) {
                    val result = audio.toByteArray()
                    if (looksLikeMp3(result)) {
                        continuation.resume(result)
                    } else {
                        continuation.resumeWithException(
                            EdgeTtsException(EdgeTransferStage.RECEIVING_AUDIO, "Edge TTS 连接提前关闭，未收到完整音频")
                        )
                    }
                }
            }
        })
        continuation.invokeOnCancellation {
            firstFrameTimeout?.cancel()
            socket.cancel()
        }
    }

    private fun looksLikeMp3(bytes: ByteArray): Boolean {
        if (bytes.size < MIN_AUDIO_BYTES) return false
        if (bytes.size >= 3 && bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) return true
        val scanLimit = minOf(bytes.size - 1, 4_096)
        return (0 until scanLimit).any { index ->
            (bytes[index].toInt() and 0xff) == 0xff && (bytes[index + 1].toInt() and 0xe0) == 0xe0
        }
    }

    private fun isDecodableAudio(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            extractor.trackCount > 0 && (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
        } catch (_: Throwable) {
            false
        } finally {
            extractor.release()
        }
    }

    private fun generateSecMsGec(): String {
        val windowsEpoch = 11_644_473_600L
        val seconds = System.currentTimeMillis() / 1000L + windowsEpoch
        val roundedSeconds = seconds - seconds % 300L
        val ticks = roundedSeconds * 10_000_000L
        return MessageDigest.getInstance("SHA-256")
            .digest("$ticks$TRUSTED_CLIENT_TOKEN".toByteArray(Charsets.US_ASCII))
            .joinToString("") { "%02X".format(it) }
    }

    private fun javascriptDate(): String = SimpleDateFormat(
        "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'",
        Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    private fun connectionId() = UUID.randomUUID().toString().replace("-", "")
    private fun randomMuid() = UUID.randomUUID().toString().replace("-", "").uppercase(Locale.US)
    private fun xml(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    internal fun ssml(text: String, voice: String, directives: TtsDirectives): String {
        val delivery = directives.delivery.lowercase()
        val rate = when (delivery) {
            "in a hurry tone" -> maxOf(directives.rate, 1.18f)
            "whispering", "soft tone" -> minOf(directives.rate, 0.92f)
            else -> directives.rate
        }.coerceIn(0.5f, 2f)
        val volume = when (delivery) {
            "whispering" -> -25
            "soft tone" -> -12
            "shouting" -> 20
            else -> directives.volume.toInt().coerceIn(-30, 30)
        }
        val pitch = when (directives.emotion.lowercase()) {
            "happy", "excited", "surprised" -> "+6Hz"
            "sad", "moved", "mysterious" -> "-5Hz"
            else -> "+0Hz"
        }
        val ratePercent = ((rate - 1f) * 100).toInt().coerceIn(-50, 100)
        val before = directives.pauseBeforeMs.coerceIn(0, 3_000).takeIf { it > 0 }
            ?.let { "<break time='${it}ms'/>" }.orEmpty()
        val after = directives.pauseAfterMs.coerceIn(0, 3_000).takeIf { it > 0 }
            ?.let { "<break time='${it}ms'/>" }.orEmpty()
        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' " +
            "xml:lang='zh-CN'><voice name='${xml(voice)}'>$before<prosody pitch='$pitch' " +
            "rate='${if (ratePercent >= 0) "+" else ""}$ratePercent%' volume='${if (volume >= 0) "+" else ""}$volume%'>" +
            "${xml(text)}</prosody>$after</voice></speak>"
    }

    private companion object {
        const val MAX_PAYLOAD_CHARS = 240
        const val FIRST_FRAME_TIMEOUT_MS = 12_000L
        const val TOTAL_TIMEOUT_MS = 30_000L
        const val MIN_AUDIO_BYTES = 512
        const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
        const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_FULL_VERSION"
        const val WSS_URL = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=$TRUSTED_CLIENT_TOKEN"
        const val ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"
    }
}

class EdgeTtsException(
    val stage: EdgeTransferStage,
    message: String
) : TtsProviderException(null, true, "${stage.label}：$message")
