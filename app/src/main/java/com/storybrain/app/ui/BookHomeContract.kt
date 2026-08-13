package com.storybrain.app.ui

import com.storybrain.app.data.TaskStatus
import com.storybrain.app.ui.navigation.AppDestination

/** Stable behavior contract for the directory-first book home. */
enum class BookLevelAction {
    READ,
    ANALYZE,
    GRAPH,
    MEMORY,
    BOOK_ENGINE,
    CHARACTER_AND_NARRATOR_VOICES,
    CHAPTER_AUDIO,
    DELETE
}

object BookChapterStatus {
    fun label(chapterIndex: Int, currentChapterIndex: Int, ttsStatus: String): String = when {
        ttsStatus == TaskStatus.COMPLETED.name -> "音频"
        chapterIndex == currentChapterIndex -> "当前"
        chapterIndex < currentChapterIndex -> "已读"
        else -> "未读"
    }
}

object VoiceBindingMenuPolicy {
    fun enabled(hasBinding: Boolean, voiceCount: Int): Boolean = hasBinding || voiceCount > 0
}

object BookHomeContract {
    const val previewChapterCount = ReactReferenceContract.bookPreviewChapterCount
    val topActions = listOf("返回", "更多")
    val fixedBottomActions = listOf("继续阅读", "故事", "配音")
    const val directoryShowsChapterStatus = true
    const val deleteRequiresConfirmation = true

    val actionPaths = mapOf(
        BookLevelAction.READ to listOf(AppDestination.Reader),
        BookLevelAction.ANALYZE to listOf(AppDestination.Story, AppDestination.Analysis),
        BookLevelAction.GRAPH to listOf(AppDestination.Story, AppDestination.Graph),
        BookLevelAction.MEMORY to listOf(AppDestination.Story, AppDestination.Memory),
        BookLevelAction.BOOK_ENGINE to listOf(AppDestination.Audio, AppDestination.AudioEngine),
        BookLevelAction.CHARACTER_AND_NARRATOR_VOICES to listOf(AppDestination.Audio, AppDestination.AudioVoices),
        BookLevelAction.CHAPTER_AUDIO to listOf(AppDestination.Audio, AppDestination.AudioChapters),
        BookLevelAction.DELETE to emptyList()
    )

    val directActions = BookLevelAction.entries.toSet()

    fun clickCount(action: BookLevelAction): Int = when (action) {
        BookLevelAction.DELETE -> 3 // More, Delete, then explicit destructive confirmation.
        else -> actionPaths.getValue(action).size
    }
}
