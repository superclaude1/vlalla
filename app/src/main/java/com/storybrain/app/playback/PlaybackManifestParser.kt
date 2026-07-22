package com.storybrain.app.playback

import java.io.File
import org.json.JSONObject

internal data class PlaybackManifestSegment(
    val index: Int,
    val blockIndex: Int,
    val speaker: String,
    val path: String
)

/** Accepts a manifest only when every declared segment has a non-empty local audio file. */
internal object PlaybackManifestParser {
    fun parse(file: File): List<PlaybackManifestSegment> = runCatching {
        val array = JSONObject(file.readText(Charsets.UTF_8)).getJSONArray("segments")
        if (array.length() == 0) return emptyList()
        buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                val path = item.optString("path")
                val audio = path.takeIf(String::isNotBlank)?.let(::File)
                if (audio?.isFile != true || audio.length() <= 0) return emptyList()
                add(
                    PlaybackManifestSegment(
                        index = item.optInt("index", index),
                        blockIndex = item.optInt("blockIndex", index),
                        speaker = item.optString("speaker"),
                        path = audio.absolutePath
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}
