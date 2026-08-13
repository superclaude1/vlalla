package com.storybrain.app.analysis

import com.storybrain.app.data.ChapterEntity
import com.storybrain.app.data.StoryCharacterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterCharacterMentionTest {
    @Test
    fun parsesChapterRoleEvidenceIntoCharacterMention() {
        val chapter = ChapterEntity(
            id = "chapter-2",
            bookId = "book-1",
            chapterIndex = 2,
            title = "夜行",
            content = "小凡握紧剑。",
            charCount = 7
        )
        val character = StoryCharacterEntity(
            id = "character-1",
            bookId = "book-1",
            canonicalName = "张小凡",
            aliasesJson = "[\"小凡\"]",
            firstChapterIndex = 0,
            lastChapterIndex = 2
        )

        val mentions = ChapterCharacterMentionParser.parse(
            bookId = "book-1",
            raw = """{"characters":[{"name":"小凡","chapterMentions":[{"chapterIndex":2,"evidence":"小凡握紧剑","confidence":0.91}]}]}""",
            characters = listOf(character),
            chapters = listOf(chapter),
            sourceHashByChapterIndex = mapOf(2 to "sha-2"),
            analysisVersion = 1
        )

        assertEquals(1, mentions.size)
        assertEquals("chapter-2", mentions.single().chapterId)
        assertEquals("character-1", mentions.single().characterId)
        assertEquals("小凡握紧剑", mentions.single().evidence)
        assertEquals(0.91f, mentions.single().confidence, 0.001f)
        assertEquals("sha-2", mentions.single().sourceHash)
        assertEquals(1, mentions.single().analysisVersion)
    }

    @Test
    fun conflictingAliasIsExcludedInsteadOfSilentlyOverwritingCharacter() {
        val first = character("first", "甲", "影子")
        val second = character("second", "乙", "影子")

        val index = CharacterIdentityMatcher.unambiguousNameIndex(listOf(first, second))

        assertEquals(first, index["甲"])
        assertEquals(second, index["乙"])
        assertTrue("conflicting alias must not resolve to whichever row was inserted last", "影子" !in index)
    }

    @Test
    fun chapterCandidatesTakePriorityWhenBuildingReaderSpeakers() {
        val global = character("global", "全局角色", "小名")
        val chapter = character("chapter", "本章角色", "小名")

        val speakers = ReaderSpeakerPolicy.buildKnownSpeakers(
            chapterCharacters = listOf(chapter),
            allCharacters = listOf(global)
        )

        assertEquals("本章角色", speakers["本章角色"])
        assertEquals("本章角色", speakers["小名"])
    }
}

private fun character(id: String, canonicalName: String, alias: String) = StoryCharacterEntity(
    id = id,
    bookId = "book-1",
    canonicalName = canonicalName,
    aliasesJson = "[\"$alias\"]",
    firstChapterIndex = 0,
    lastChapterIndex = 1
)
