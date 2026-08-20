package com.storybrain.app.analysis

import com.storybrain.app.data.ChapterEntity
import com.storybrain.app.data.StoryCharacterEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogueAnnotationParserTest {
    private val chapter = ChapterEntity(
        id = "chapter",
        bookId = "book",
        chapterIndex = 0,
        title = "第一章",
        content = "宋沁面露狐疑道：“我先回去探探爹的口风。”",
        charCount = 24
    )
    private val character = StoryCharacterEntity(
        id = "character",
        bookId = "book",
        canonicalName = "宋沁",
        aliasesJson = JSONArray(listOf("沁儿")).toString(),
        firstChapterIndex = 0,
        lastChapterIndex = 0
    )

    @Test
    fun derivesSourceOffsetsAndCanonicalSpeakerFromExactOriginalText() {
        val root = JSONObject().put(
            "dialogues",
            JSONArray().put(
                JSONObject()
                    .put("chapterIndex", 0)
                    .put("speaker", "宋沁")
                    .put("dialogue", "我先回去探探爹的口风。")
                    .put("sourceText", chapter.content)
                    .put("speakerEvidence", "宋沁面露狐疑道")
                    .put("confidence", 0.95)
            )
        )

        val result = DialogueAnnotationParser.parseAndValidate(
            "book", root, listOf(chapter), listOf(character), 2
        )

        assertTrue(result.issues.isEmpty())
        assertEquals(1, result.annotations.size)
        assertEquals(0, result.annotations.single().sourceStart)
        assertEquals(chapter.content.length, result.annotations.single().sourceEnd)
        assertEquals("character", result.annotations.single().speakerCharacterId)
    }

    @Test
    fun rejectsActionPhraseAsSpeakerAndMissingSource() {
        val root = JSONObject().put(
            "dialogues",
            JSONArray()
                .put(
                    JSONObject()
                        .put("chapterIndex", 0)
                        .put("speaker", "面露狐疑")
                        .put("dialogue", "我先回去探探爹的口风。")
                        .put("sourceText", chapter.content)
                )
                .put(
                    JSONObject()
                        .put("chapterIndex", 0)
                        .put("speaker", "宋沁")
                        .put("dialogue", "虚构对白")
                        .put("sourceText", "原文不存在")
                )
        )

        val result = DialogueAnnotationParser.parseAndValidate(
            "book", root, listOf(chapter), listOf(character), 2
        )

        assertEquals(0, result.annotations.size)
        assertTrue(result.issues.any { "动作描写" in it })
        assertTrue(result.issues.any { "不存在于原文" in it })
    }
}
