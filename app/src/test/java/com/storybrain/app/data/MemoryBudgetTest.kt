package com.storybrain.app.data

import org.junit.Assert.assertThrows
import org.junit.Test

class MemoryBudgetTest {
    private fun memory(id: String, content: String) = MemoryItemEntity(
        id = id, bookId = "book", type = MemoryType.NOTE.name, title = id, content = content,
        sourceKey = "manual:$id", searchTerms = content, createdAt = 1, updatedAt = 1
    )

    @Test
    fun rejectsThirtyFirstMemory() {
        val selected = (1..30).map { memory("$it", "短记忆") }
        assertThrows(MemorySelectionException::class.java) { validateMemoryBudget(selected, memory("31", "额外")) }
    }

    @Test
    fun rejectsContentBeyondCharacterBudget() {
        assertThrows(MemorySelectionException::class.java) {
            validateMemoryBudget(listOf(memory("a", "甲".repeat(11_900))), memory("b", "乙".repeat(101)))
        }
    }
}
