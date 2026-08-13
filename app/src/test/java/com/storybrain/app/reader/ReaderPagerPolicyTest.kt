package com.storybrain.app.reader

import com.storybrain.app.data.ChapterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPagerPolicyTest {
    @Test
    fun waitsForRequestedChapterInsteadOfFallingBackToFirstPage() {
        assertNull(ReaderPagerPolicy.startIndex(emptyList(), "chapter-6"))
        assertNull(ReaderPagerPolicy.startIndex(listOf(chapter("chapter-1", 0)), "chapter-6"))
    }

    @Test
    fun startsExactlyAtRequestedChapterAfterListLoads() {
        val chapters = listOf(chapter("chapter-1", 0), chapter("chapter-6", 5))
        assertEquals(1, ReaderPagerPolicy.startIndex(chapters, "chapter-6"))
    }

    private fun chapter(id: String, index: Int) = ChapterEntity(
        id = id,
        bookId = "book",
        chapterIndex = index,
        title = id,
        content = "正文",
        charCount = 2
    )
}
