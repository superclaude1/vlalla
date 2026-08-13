package com.storybrain.app.reader

import com.storybrain.app.data.ChapterEntity

object ReaderPagerPolicy {
    fun startIndex(chapters: List<ChapterEntity>, chapterId: String): Int? =
        chapters.indexOfFirst { it.id == chapterId }.takeIf { it >= 0 }
}
