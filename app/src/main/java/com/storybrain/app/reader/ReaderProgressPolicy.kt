package com.storybrain.app.reader

import kotlin.math.roundToInt

data class ReaderProgressSummary(
    val chapterNumber: Int,
    val chapterCount: Int,
    val percent: Int,
    val remainingChapters: Int
)

object ReaderProgressPolicy {
    fun summarize(currentIndex: Int, chapterCount: Int): ReaderProgressSummary {
        if (chapterCount <= 0) return ReaderProgressSummary(0, 0, 0, 0)
        val boundedIndex = currentIndex.coerceIn(0, chapterCount - 1)
        val chapterNumber = boundedIndex + 1
        return ReaderProgressSummary(
            chapterNumber = chapterNumber,
            chapterCount = chapterCount,
            percent = (chapterNumber * 100f / chapterCount).roundToInt().coerceIn(0, 100),
            remainingChapters = (chapterCount - chapterNumber).coerceAtLeast(0)
        )
    }
}
