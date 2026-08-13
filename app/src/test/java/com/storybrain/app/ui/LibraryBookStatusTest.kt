package com.storybrain.app.ui

import com.storybrain.app.data.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryBookStatusTest {
    @Test
    fun analysisAndAudioProgressRemainVisibleTogether() {
        val book = book(chapterCount = 20, analysisCompleted = 12)
        assertEquals("已分析 12/20 章 · 已配音 8 章", libraryBookStatus(book, 8))
    }

    @Test
    fun zeroProgressIsStillExplicit() {
        val book = book(chapterCount = 20, analysisCompleted = 0)
        assertEquals("未分析 · 未配音", libraryBookStatus(book, 0))
    }

    @Test
    fun completedAnalysisDoesNotHideAudioProgress() {
        val book = book(chapterCount = 20, analysisCompleted = 20)
        assertEquals("已分析 20/20 章 · 已配音 3 章", libraryBookStatus(book, 3))
    }

    private fun book(chapterCount: Int, analysisCompleted: Int) = BookEntity(
        id = "book",
        title = "书",
        sourceName = "book.txt",
        importedAt = 1L,
        chapterCount = chapterCount,
        totalChars = 1L,
        analysisCompleted = analysisCompleted
    )
}
