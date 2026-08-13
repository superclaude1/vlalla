package com.storybrain.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryAnalysisPolicyTest {
    @Test
    fun firstRunInitializesAtMostFirstFifteenChaptersWithoutCustomCount() {
        val state = StoryAnalysisPolicy.state(done = 0, total = 23, requested = "5")

        assertFalse(state.showIncrementControls)
        assertEquals("分析前15章", state.actionLabel)
        assertNull(state.chapterCount)
    }

    @Test
    fun interruptedInitializationContinuesToInitializationTarget() {
        val state = StoryAnalysisPolicy.state(done = 6, total = 10, requested = "2")

        assertFalse(state.showIncrementControls)
        assertEquals("继续初始化至第10章", state.actionLabel)
        assertNull(state.chapterCount)
    }

    @Test
    fun initializedBookShowsRemainingAndClampsIncrementToOneThroughRemaining() {
        val tooLarge = StoryAnalysisPolicy.state(done = 15, total = 18, requested = "99")
        val zero = StoryAnalysisPolicy.state(done = 15, total = 18, requested = "0")

        assertTrue(tooLarge.showIncrementControls)
        assertEquals(3, tooLarge.remaining)
        assertEquals(3, tooLarge.chapterCount)
        assertEquals("3", tooLarge.inputValue)
        assertEquals(1, zero.chapterCount)
        assertEquals("1", zero.inputValue)
    }

    @Test
    fun analysisStateOnlyAppliesToItsOwnBook() {
        assertTrue(StoryAnalysisPolicy.isRunningForBook("book-a", "book-a", true))
        assertFalse(StoryAnalysisPolicy.isRunningForBook("book-a", "book-b", true))
        assertNull(StoryAnalysisPolicy.messageForBook("book-a", "book-b", "other status"))
        assertEquals("own status", StoryAnalysisPolicy.messageForBook("book-a", "book-a", "own status"))
    }

    @Test
    fun completedBookHasStableCompletedLabel() {
        val state = StoryAnalysisPolicy.state(done = 8, total = 8, requested = "5")

        assertEquals(0, state.remaining)
        assertEquals("已完成全书分析", state.actionLabel)
    }
}
