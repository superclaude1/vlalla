package com.storybrain.app.ui

import com.storybrain.app.data.BookEntity

enum class LibrarySortOrder {
    IMPORTED_TIME,
    TITLE
}

fun sortLibraryBooks(books: List<BookEntity>, order: LibrarySortOrder): List<BookEntity> =
    when (order) {
        LibrarySortOrder.IMPORTED_TIME -> books.sortedByDescending(BookEntity::importedAt)
        LibrarySortOrder.TITLE -> books.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

fun libraryBookStatus(book: BookEntity, ttsCompleted: Int): String {
    val analysis = if (book.analysisCompleted > 0) {
        "已分析 ${book.analysisCompleted}/${book.chapterCount} 章"
    } else {
        "未分析"
    }
    val audio = if (ttsCompleted > 0) "已配音 $ttsCompleted 章" else "未配音"
    return "$analysis · $audio"
}
