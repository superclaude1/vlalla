package com.storybrain.app.analysis

import com.storybrain.app.data.StoryCharacterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CharacterIdentityMatcherTest {
    private val character = StoryCharacterEntity(
        id = "character-1",
        bookId = "book",
        canonicalName = "张小凡",
        aliasesJson = "[\"小凡\",\"张师弟\"]",
        firstChapterIndex = 0,
        lastChapterIndex = 10
    )

    @Test
    fun resolvesIncomingCanonicalNameThroughKnownAlias() {
        assertSame(character, CharacterIdentityMatcher.find(listOf(character), "张师弟", emptyList()))
    }

    @Test
    fun mergesOldAndNewAliasesWithoutCanonicalName() {
        assertEquals(
            listOf("小凡", "张师兄", "张师弟"),
            CharacterIdentityMatcher.mergedAliases(character, "张师兄", listOf("小凡"), "张小凡")
        )
    }
}
