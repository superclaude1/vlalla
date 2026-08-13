package com.storybrain.app.ui

import com.storybrain.app.data.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryBookSortingTest {
    private val books = listOf(
        book(id = "older-zulu", title = "zulu", importedAt = 100L),
        book(id = "newest-beta", title = "Beta", importedAt = 300L),
        book(id = "middle-alpha", title = "alpha", importedAt = 200L)
    )

    @Test
    fun importedTimeOrderShowsNewestBookFirst() {
        assertEquals(
            listOf("newest-beta", "middle-alpha", "older-zulu"),
            sortLibraryBooks(books, LibrarySortOrder.IMPORTED_TIME).map { it.id }
        )
    }

    @Test
    fun titleOrderSortsBookNamesIgnoringCase() {
        assertEquals(
            listOf("middle-alpha", "newest-beta", "older-zulu"),
            sortLibraryBooks(books, LibrarySortOrder.TITLE).map { it.id }
        )
    }

    private fun book(id: String, title: String, importedAt: Long) = BookEntity(
        id = id,
        title = title,
        sourceName = "$title.txt",
        importedAt = importedAt,
        chapterCount = 1,
        totalChars = 1L
    )
}
