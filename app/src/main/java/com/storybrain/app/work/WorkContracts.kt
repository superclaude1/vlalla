package com.storybrain.app.work

import androidx.work.Data

object WorkContracts {
    const val KEY_BOOK_ID = "book_id"
    const val KEY_CHAPTER_ID = "chapter_id"
    const val KEY_REQUESTED_CHAPTER_COUNT = "requested_chapter_count"
    const val KEY_COMPLETED = "completed"
    const val KEY_TOTAL = "total"
    const val KEY_STAGE = "stage"
    const val NO_CHAPTER_COUNT = -1

    const val TAG_ANALYSIS = "story-analysis"
    const val TAG_TTS = "chapter-tts"
    const val TAG_SEARCH_INDEX = "chapter-search-index"
    const val MAINTENANCE_WORK = "maintenance:startup"

    fun analysisName(bookId: String) = "analysis:$bookId"
    fun ttsName(chapterId: String) = "tts:$chapterId"
    fun searchIndexName(bookId: String) = "search-index:$bookId"

    fun progress(completed: Int, total: Int, stage: String): Data = Data.Builder()
        .putInt(KEY_COMPLETED, completed)
        .putInt(KEY_TOTAL, total)
        .putString(KEY_STAGE, stage)
        .build()
}

data class TaskProgress(
    val completed: Int,
    val total: Int,
    val stage: String
) {
    companion object {
        fun from(data: Data) = TaskProgress(
            completed = data.getInt(WorkContracts.KEY_COMPLETED, 0),
            total = data.getInt(WorkContracts.KEY_TOTAL, 0),
            stage = data.getString(WorkContracts.KEY_STAGE).orEmpty()
        )
    }
}
