package com.storybrain.app.playback

import java.io.File
import kotlin.io.path.createTempDirectory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackManifestParserTest {
    @Test
    fun parsesBlockMappingWhenEveryAudioSegmentExists() {
        val directory = createTempDirectory("playback-manifest-").toFile()
        try {
            val first = File(directory, "0.mp3").apply { writeBytes(byteArrayOf(1)) }
            val second = File(directory, "1.mp3").apply { writeBytes(byteArrayOf(2)) }
            val manifest = manifest(directory, first, second)

            val segments = PlaybackManifestParser.parse(manifest)

            assertEquals(listOf(4, 7), segments.map { it.blockIndex })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsWholeManifestWhenAnyDeclaredSegmentIsMissing() {
        val directory = createTempDirectory("playback-manifest-").toFile()
        try {
            val first = File(directory, "0.mp3").apply { writeBytes(byteArrayOf(1)) }
            val missing = File(directory, "missing.mp3")
            val manifest = manifest(directory, first, missing)

            assertTrue(PlaybackManifestParser.parse(manifest).isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun manifest(directory: File, first: File, second: File): File {
        val segments = JSONArray()
            .put(JSONObject().put("index", 0).put("blockIndex", 4).put("speaker", "旁白").put("path", first.absolutePath))
            .put(JSONObject().put("index", 1).put("blockIndex", 7).put("speaker", "角色").put("path", second.absolutePath))
        return File(directory, "manifest.json").apply {
            writeText(JSONObject().put("segments", segments).toString(), Charsets.UTF_8)
        }
    }
}
