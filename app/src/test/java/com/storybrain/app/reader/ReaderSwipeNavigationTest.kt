package com.storybrain.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSwipeNavigationTest {
    @Test
    fun swipeLeftMovesToNextChapter() {
        assertEquals(4, ReaderSwipeNavigation.targetIndex(currentIndex = 3, chapterCount = 10, dragPx = -120f, thresholdPx = 80f))
    }

    @Test
    fun swipeRightMovesToPreviousChapter() {
        assertEquals(2, ReaderSwipeNavigation.targetIndex(currentIndex = 3, chapterCount = 10, dragPx = 120f, thresholdPx = 80f))
    }

    @Test
    fun shortDragDoesNotChangeChapter() {
        assertNull(ReaderSwipeNavigation.targetIndex(currentIndex = 3, chapterCount = 10, dragPx = -40f, thresholdPx = 80f))
    }

    @Test
    fun chapterBoundariesDoNotWrap() {
        assertNull(ReaderSwipeNavigation.targetIndex(currentIndex = 0, chapterCount = 10, dragPx = 120f, thresholdPx = 80f))
        assertNull(ReaderSwipeNavigation.targetIndex(currentIndex = 9, chapterCount = 10, dragPx = -120f, thresholdPx = 80f))
    }
}
