package com.storybrain.app.reader

object ReaderSwipeNavigation {
    fun targetIndex(currentIndex: Int, chapterCount: Int, dragPx: Float, thresholdPx: Float): Int? {
        if (chapterCount <= 0 || currentIndex !in 0 until chapterCount) return null
        return when {
            dragPx <= -thresholdPx && currentIndex < chapterCount - 1 -> currentIndex + 1
            dragPx >= thresholdPx && currentIndex > 0 -> currentIndex - 1
            else -> null
        }
    }
}
