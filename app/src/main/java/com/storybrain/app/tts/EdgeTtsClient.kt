package com.storybrain.app.tts

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/** Direct Android implementation of the Microsoft Edge online TTS protocol. */
class EdgeTtsClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun synthesize(text: String, voice: String, output: File): TtsAudioArtifact {
        return synthesize(text, voice, TtsDirectives(), output)
    }

    fun synthesize(text: String, voice: String, directives: TtsDirectives, output: File): TtsAudioArtifact {
        require(text.isNotBlank()) { "配音文本不能为空" }
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
        val latch = CountDownLatch(1)
        val audio = ByteArrayOutputStream()
        var failure: Throwable? = null
        var finished = false

        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
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
                    webSocket.close(1000, "completed")
                    latch.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val frame = bytes.toByteArray()
                if (frame.size < 2) return
                val headerLength = ((frame[0].toInt() and 0xff) shl 8) or (frame[1].toInt() and 0xff)
                if (headerLength <= 0 || frame.size < headerLength + 2) return
                val header = String(frame, 2, headerLength, Charsets.UTF_8)
                if (header.contains("Path:audio", ignoreCase = true) && frame.size > headerLength + 2) {
                    audio.write(frame, headerLength + 2, frame.size - headerLength - 2)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failure = EdgeTtsException(
                    if (response != null) "Edge TTS 连接失败（HTTP ${response.code}）" else
                        (t.message ?: "Edge TTS 网络连接失败")
                )
                latch.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                latch.countDown()
            }
        })

        if (!latch.await(45, TimeUnit.SECONDS)) {
            socket.cancel()
            throw EdgeTtsException("Edge TTS 请求超时，请检查网络后重试")
        }
        failure?.let { throw it }
        val bytes = audio.toByteArray()
        if (!finished || bytes.isEmpty()) throw EdgeTtsException("Edge TTS 未返回有效音频")
        output.parentFile?.mkdirs()
        val temporary = File(output.parentFile, "${output.name}.part")
        temporary.writeBytes(bytes)
        if (output.exists()) output.delete()
        if (!temporary.renameTo(output)) {
            temporary.delete()
            throw EdgeTtsException("无法保存配音文件")
        }
        return TtsAudioArtifact(output, "audio/mpeg", "mp3")
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
    message: String,
    val retryable: Boolean = true
) : Exception(message)
