package com.storybrain.app.analysis

import com.storybrain.app.data.ChapterEntity
import com.storybrain.app.data.DialogueAnnotationEntity
import com.storybrain.app.data.StoryCharacterEntity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class DialogueDraft(
    val chapterIndex: Int,
    val speaker: String?,
    val dialogue: String,
    val sourceText: String,
    val speakerEvidence: String,
    val confidence: Float
)

internal data class DialogueValidationResult(
    val annotations: List<DialogueAnnotationEntity>,
    val issues: List<String>
)

/** Converts model-proposed dialogue into source-proven annotations. */
internal object DialogueAnnotationParser {
    private val actionMarkers = listOf(
        "面露", "狐疑", "低声", "大声", "冷笑", "皱眉", "疑惑", "说道", "回答道", "问道", "喊道"
    )

    fun parseAndValidate(
        bookId: String,
        root: JSONObject,
        chapters: List<ChapterEntity>,
        characters: List<StoryCharacterEntity>,
        analysisVersion: Int
    ): DialogueValidationResult {
        val drafts = root.optJSONArray("dialogues").objects().mapNotNull { item ->
            val chapterIndex = item.optInt("chapterIndex", Int.MIN_VALUE)
            val dialogue = item.optString("dialogue").trim()
            val sourceText = item.optString("sourceText").trim()
            if (dialogue.isBlank() || sourceText.isBlank()) return@mapNotNull null
            DialogueDraft(
                chapterIndex = chapterIndex,
                speaker = item.optString("speaker").trim().takeIf(String::isNotBlank),
                dialogue = dialogue,
                sourceText = sourceText,
                speakerEvidence = item.optString("speakerEvidence").trim(),
                confidence = item.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f)
            )
        }
        if (drafts.isEmpty()) return DialogueValidationResult(emptyList(), emptyList())

        val chapterByIndex = chapters.associateBy { it.chapterIndex }
        val nameIndex = buildMap<String, StoryCharacterEntity> {
            characters.forEach { character ->
                put(character.canonicalName, character)
                runCatching { JSONArray(character.aliasesJson) }.getOrNull()?.let { aliases ->
                    for (index in 0 until aliases.length()) {
                        aliases.optString(index).trim().takeIf(String::isNotBlank)?.let { put(it, character) }
                    }
                }
            }
        }
        val issues = mutableListOf<String>()
        val occupiedByChapter = mutableMapOf<String, MutableList<IntRange>>()
        val annotations = drafts.mapIndexedNotNull { index, draft ->
            val chapter = chapterByIndex[draft.chapterIndex]
            if (chapter == null) {
                issues += "dialogues[$index] 引用了不存在的章节"
                return@mapIndexedNotNull null
            }
            val start = findUnoccupiedSource(
                chapter.content,
                draft.sourceText,
                occupiedByChapter.getOrPut(chapter.id) { mutableListOf() }
            )
            if (start < 0) {
                issues += "dialogues[$index].sourceText 不存在于原文或与已有标注重叠"
                return@mapIndexedNotNull null
            }
            if (!draft.sourceText.contains(draft.dialogue)) {
                issues += "dialogues[$index].dialogue 不存在于 sourceText"
                return@mapIndexedNotNull null
            }
            val speaker = draft.speaker
            if (speaker != null && actionMarkers.any(speaker::contains)) {
                issues += "dialogues[$index] 动作描写被识别为角色：$speaker"
                return@mapIndexedNotNull null
            }
            val character = speaker?.let(nameIndex::get)
            if (speaker != null && character == null) {
                issues += "dialogues[$index] 说话人不在角色库：$speaker"
            }
            val end = start + draft.sourceText.length
            occupiedByChapter.getValue(chapter.id) += start until end
            DialogueAnnotationEntity(
                id = stableId(bookId, chapter.id, start.toString(), end.toString()),
                bookId = bookId,
                chapterId = chapter.id,
                chapterIndex = chapter.chapterIndex,
                speakerCharacterId = character?.id,
                speakerName = character?.canonicalName ?: speaker,
                dialogueText = draft.dialogue,
                sourceText = draft.sourceText,
                speakerEvidence = draft.speakerEvidence,
                sourceStart = start,
                sourceEnd = end,
                confidence = draft.confidence,
                validationStatus = if (character != null || speaker == null) "VALIDATED" else "UNKNOWN_SPEAKER",
                validationIssuesJson = "[]",
                sourceHash = sourceHash(chapter.content),
                analysisVersion = analysisVersion
            )
        }
        return DialogueValidationResult(annotations, issues)
    }

    private fun findUnoccupiedSource(source: String, needle: String, occupied: List<IntRange>): Int {
        var start = source.indexOf(needle)
        while (start >= 0) {
            val range = start until (start + needle.length)
            if (occupied.none { it.first < range.last + 1 && range.first < it.last + 1 }) return start
            start = source.indexOf(needle, start + 1)
        }
        return -1
    }

    private fun stableId(vararg parts: String): String = UUID.nameUUIDFromBytes(
        parts.joinToString(":").toByteArray(StandardCharsets.UTF_8)
    ).toString()

    private fun sourceHash(content: String): String = MessageDigest.getInstance("SHA-256")
        .digest(content.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else
        (0 until length()).mapNotNull(::optJSONObject)
}
