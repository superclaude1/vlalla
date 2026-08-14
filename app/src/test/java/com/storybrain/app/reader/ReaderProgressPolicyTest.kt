package com.storybrain.app.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderProgressPolicyTest {
    @Test
    fun emptyBookHasNeutralProgress() {
        assertEquals(
            ReaderProgressSummary(chapterNumber = 0, chapterCount = 0, percent = 0, remainingChapters = 0),
            ReaderProgressPolicy.summarize(currentIndex = -1, chapterCount = 0)
        )
    }

    @Test
    fun progressIsBoundedAndMonotonicAcrossChapters() {
        val first = ReaderProgressPolicy.summarize(currentIndex = 0, chapterCount = 10)
        val middle = ReaderProgressPolicy.summarize(currentIndex = 4, chapterCount = 10)
        val last = ReaderProgressPolicy.summarize(currentIndex = 9, chapterCount = 10)

        assertEquals(10, first.percent)
        assertEquals(50, middle.percent)
        assertEquals(100, last.percent)
        assertEquals(9, first.remainingChapters)
        assertEquals(5, middle.remainingChapters)
        assertEquals(0, last.remainingChapters)
    }

    @Test
    fun staleIndicesClampToBookBounds() {
        assertEquals(1, ReaderProgressPolicy.summarize(-5, 3).chapterNumber)
        assertEquals(3, ReaderProgressPolicy.summarize(99, 3).chapterNumber)
        assertEquals(100, ReaderProgressPolicy.summarize(99, 3).percent)
    }
}
