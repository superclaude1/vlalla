package com.storybrain.app.analysis

import com.storybrain.app.data.ChapterCharacterMentionEntity
import com.storybrain.app.data.ChapterEntity
import com.storybrain.app.data.StoryCharacterEntity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject

/** Pure parser for chapter-scoped character evidence emitted by the LLM. */
internal object ChapterCharacterMentionParser {
    fun parse(
        bookId: String,
        raw: String,
        characters: List<StoryCharacterEntity>,
        chapters: List<ChapterEntity>,
        sourceHashByChapterIndex: Map<Int, String>,
        analysisVersion: Int
    ): List<ChapterCharacterMentionEntity> {
        val index = CharacterIdentityMatcher.unambiguousNameIndex(characters)
        val chaptersByIndex = chapters.associateBy { it.chapterIndex }
        val root = JSONObject(extractJsonObject(raw))
        return root.optJSONArray("characters").objects().flatMap { characterJson ->
            val character = index[characterJson.optString("name").trim()] ?: return@flatMap emptyList()
            characterJson.optJSONArray("chapterMentions").objects().mapNotNull { mentionJson ->
                val chapterIndex = mentionJson.optInt("chapterIndex", Int.MIN_VALUE)
                val chapter = chaptersByIndex[chapterIndex] ?: return@mapNotNull null
                val evidence = mentionJson.optString("evidence").trim().take(500)
                if (evidence.isBlank()) return@mapNotNull null
                val sourceHash = sourceHashByChapterIndex[chapterIndex]
                    ?: sha256(chapter.content)
                ChapterCharacterMentionEntity(
                    id = stableId(bookId, chapter.id, character.id, sourceHash, analysisVersion.toString()),
                    chapterId = chapter.id,
                    characterId = character.id,
                    evidence = evidence,
                    confidence = mentionJson.optDouble("confidence", 0.5).toFloat().coerceIn(0f, 1f),
                    sourceHash = sourceHash,
                    analysisVersion = analysisVersion
                )
            }
        }.distinctBy { it.id }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun stableId(vararg parts: String): String = UUID.nameUUIDFromBytes(
        parts.joinToString(":").toByteArray(StandardCharsets.UTF_8)
    ).toString()
}

private fun org.json.JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}
