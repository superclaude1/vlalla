package com.storybrain.app.analysis

import com.storybrain.app.data.ChatMessageEntity
import com.storybrain.app.data.MemoryItemEntity
import com.storybrain.app.data.MemoryType
import com.storybrain.app.data.StoryCharacterEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterPromptBuilderTest {
    private val character = StoryCharacterEntity(
        id = "c", bookId = "b", canonicalName = "张小凡", firstChapterIndex = 0, lastChapterIndex = 10
    )

    @Test
    fun promptContainsOnlyMemoriesPassedBySelectionLayer() {
        val selected = MemoryItemEntity(
            id = "selected", bookId = "b", type = MemoryType.PLOT.name, title = "青云入门",
            content = "张小凡进入青云门", sourceKey = "plot:p", searchTerms = "青云", createdAt = 1, updatedAt = 1
        )
        val prompt = CharacterPromptBuilder.build(character, "诛仙", 15, listOf(selected), emptyList())
        assertTrue(prompt.contains("张小凡进入青云门"))
        assertTrue(prompt.contains("<default-memories>"))
        assertTrue(prompt.contains("<session-memories>"))
        assertFalse(prompt.contains("未选择的秘密"))
    }

    @Test
    fun historyKeepsNewestContiguousMessagesWithinBudget() {
        val messages = listOf(
            message("old", "旧".repeat(8)),
            message("middle", "中".repeat(8)),
            message("new", "新".repeat(8))
        )
        val kept = trimChatHistory(messages, 16)
        assertFalse(kept.any { it.id == "old" })
        assertTrue(kept.map { it.id } == listOf("middle", "new"))
    }

    private fun message(id: String, content: String) = ChatMessageEntity(
        id, "b", "c", "s", "user", content, 1
    )
}
