package com.storybrain.app.tts

import android.media.MediaPlayer
import java.io.File
import org.json.JSONObject

class ChapterAudioPlayer {
    private var player: MediaPlayer? = null
    private var paths: List<String> = emptyList()
    private var currentIndex = 0

    fun play(manifestPath: String, onState: (playing: Boolean, error: String?) -> Unit) {
        stop()
        val manifest = File(manifestPath)
        if (!manifest.exists()) {
            onState(false, "配音清单不存在，请重新生成")
            return
        }
        paths = runCatching {
            val segments = JSONObject(manifest.readText()).getJSONArray("segments")
            (0 until segments.length()).map { segments.getJSONObject(it).getString("path") }
                .filter { File(it).exists() }
        }.getOrElse {
            onState(false, "配音清单损坏，请重新生成")
            return
        }
        if (paths.isEmpty()) {
            onState(false, "没有可播放的配音片段")
            return
        }
        currentIndex = 0
        playCurrent(onState)
    }

    private fun playCurrent(onState: (Boolean, String?) -> Unit) {
        if (currentIndex >= paths.size) {
            stop()
            onState(false, null)
            return
        }
        player = MediaPlayer().apply {
            setDataSource(paths[currentIndex])
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
        paths = emptyList()
        currentIndex = 0
    }
}
