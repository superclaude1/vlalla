package com.storybrain.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPositionPolicyTest {
    @Test
    fun restoredPositionMustBelongToTheSameBookChapterAndMode() {
        val saved = ReaderPosition(
            bookId = "book-a",
            chapterId = "chapter-2",
            displayMode = ReaderDisplayMode.PLAIN_TEXT,
            itemIndex = 7,
            itemOffsetPx = 24
        )

        assertEquals(
            ReaderViewport(7, 24),
            ReaderPositionPolicy.restore(
                saved = saved,
                bookId = "book-a",
                chapterId = "chapter-2",
                displayMode = ReaderDisplayMode.PLAIN_TEXT,
                itemCount = 20
            )
        )
        assertNull(ReaderPositionPolicy.restore(saved, "book-b", "chapter-2", ReaderDisplayMode.PLAIN_TEXT, 20))
        assertNull(ReaderPositionPolicy.restore(saved, "book-a", "chapter-3", ReaderDisplayMode.PLAIN_TEXT, 20))
        assertNull(ReaderPositionPolicy.restore(saved, "book-a", "chapter-2", ReaderDisplayMode.DIALOGUE, 20))
    }

    @Test
    fun staleAndMalformedPositionsClampWithoutCrashing() {
        val saved = ReaderPosition(
            bookId = "book",
            chapterId = "chapter",
            displayMode = ReaderDisplayMode.PLAIN_TEXT,
            itemIndex = 999,
            itemOffsetPx = -100
        )

        assertEquals(
            ReaderViewport(itemIndex = 3, itemOffsetPx = 0),
            ReaderPositionPolicy.restore(saved, "book", "chapter", ReaderDisplayMode.PLAIN_TEXT, itemCount = 4)
        )
        assertNull(ReaderPositionPolicy.restore(saved, "book", "chapter", ReaderDisplayMode.PLAIN_TEXT, itemCount = 0))
    }

    @Test
    fun positionRoundTripsThroughStableStorageMap() {
        val original = ReaderPosition(
            bookId = "book:长篇",
            chapterId = "chapter/42",
            displayMode = ReaderDisplayMode.DIALOGUE,
            itemIndex = 12,
            itemOffsetPx = 36
        )

        assertEquals(original, ReaderPosition.fromMap(original.toMap()))
        assertNull(ReaderPosition.fromMap(mapOf(ReaderPosition.KEY_BOOK_ID to "book")))
    }

    @Test
    fun storageKeysAreScopedByBookChapterAndMode() {
        val plain = ReaderPositionPolicy.storageKey("book-a", "chapter-1", ReaderDisplayMode.PLAIN_TEXT)
        val dialogue = ReaderPositionPolicy.storageKey("book-a", "chapter-1", ReaderDisplayMode.DIALOGUE)
        val anotherChapter = ReaderPositionPolicy.storageKey("book-a", "chapter-2", ReaderDisplayMode.PLAIN_TEXT)

        assertEquals(plain, ReaderPositionPolicy.storageKey("book-a", "chapter-1", ReaderDisplayMode.PLAIN_TEXT))
        assertNotEquals(plain, dialogue)
        assertNotEquals(plain, anotherChapter)
    }

    @Test
    fun persistenceSkipsDuplicateViewports() {
        val previous = ReaderPosition("book", "chapter", ReaderDisplayMode.PLAIN_TEXT, 3, 10)

        assertFalse(ReaderPositionPolicy.shouldPersist(previous, previous))
        assertTrue(ReaderPositionPolicy.shouldPersist(previous, previous.copy(itemOffsetPx = 11)))
        assertTrue(ReaderPositionPolicy.shouldPersist(null, previous))
    }
}
