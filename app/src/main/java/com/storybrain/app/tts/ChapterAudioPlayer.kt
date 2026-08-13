package com.storybrain.app.tts

import android.media.MediaPlayer
import java.io.File
import org.json.JSONObject

class ChapterAudioPlayer {
    private var player: MediaPlayer? = null
    private var segments: List<ChapterAudioSegment> = emptyList()
    private var currentIndex = 0

    fun play(manifestPath: String, onState: (playing: Boolean, error: String?) -> Unit) {
        stop()
        val manifest = File(manifestPath)
        if (!manifest.exists()) {
            onState(false, "配音清单不存在，请重新生成")
            return
        }
        segments = runCatching {
            parseChapterAudioSegments(manifest).filter { File(it.path).exists() }
        }.getOrElse {
            onState(false, "配音清单损坏，请重新生成")
            return
        }
        if (segments.isEmpty()) {
            onState(false, "没有可播放的配音片段")
            return
        }
        currentIndex = 0
        playCurrent(onState)
    }

    private fun playCurrent(onState: (Boolean, String?) -> Unit) {
        if (currentIndex >= segments.size) {
            stop()
            onState(false, null)
            return
        }
        player = MediaPlayer().apply {
            setDataSource(segments[currentIndex].path)
            setOnPreparedListener {
                onState(true, null)
                it.start()
            }
            setOnCompletionListener {
                it.release()
                player = null
                currentIndex++
                playCurrent(onState)
            }
            setOnErrorListener { _, _, _ ->
                stop()
                onState(false, "音频播放失败")
                true
            }
            prepareAsync()
        }
    }

    fun stop() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        segments = emptyList()
        currentIndex = 0
    }
}

data class ChapterAudioSegment(
    val path: String,
    val mimeType: String?,
    val format: String?
)

fun parseChapterAudioSegments(manifest: File): List<ChapterAudioSegment> {
    val values = JSONObject(manifest.readText()).getJSONArray("segments")
    return (0 until values.length()).map { index ->
        val item = values.getJSONObject(index)
        ChapterAudioSegment(
            path = item.getString("path"),
            mimeType = item.optString("mimeType").takeIf(String::isNotBlank),
            format = item.optString("format").takeIf(String::isNotBlank)
        )
    }
}
