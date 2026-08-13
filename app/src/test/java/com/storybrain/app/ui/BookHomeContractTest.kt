package com.storybrain.app.ui

import com.storybrain.app.data.TaskStatus
import com.storybrain.app.ui.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookHomeContractTest {
    @Test
    fun homeKeepsDirectoryFirstStructureAndBookControls() {
        assertEquals(14, BookHomeContract.previewChapterCount)
        assertEquals(listOf("继续阅读", "故事", "配音"), BookHomeContract.fixedBottomActions)
        val screen = java.io.File("src/main/java/com/storybrain/app/ui/InformationArchitectureScreens.kt").readText()
        assertFalse(screen.contains("BookPortal(NavigationArchitecture.bookHubRows[0]"))
        assertTrue(screen.contains("horizontalArrangement = Arrangement.Center"))
        assertEquals(listOf("返回", "更多"), BookHomeContract.topActions)
        assertTrue(BookHomeContract.directoryShowsChapterStatus)
        assertTrue(BookHomeContract.deleteRequiresConfirmation)
    }

    @Test
    fun characterVoicePageAlsoProvidesRealNarratorBindingActions() {
        val screen = java.io.File("src/main/java/com/storybrain/app/ui/CharacterVoiceBindingScreen.kt").readText()
        val viewModel = java.io.File("src/main/java/com/storybrain/app/ui/AppViewModel.kt").readText()
        assertTrue(screen.contains("Text(\"旁白音色\""))
        assertTrue(screen.contains("viewModel.assignNarratorVoice(bookId"))
        assertTrue(screen.contains("viewModel.clearNarratorVoice(bookId)"))
        assertTrue(viewModel.contains("fun assignNarratorVoice("))
        assertTrue(viewModel.contains("fun clearNarratorVoice("))
    }

    @Test
    fun deleteFlowOwnsConfirmationDatabaseCleanupAndChapterAudioCleanup() {
        val screen = java.io.File("src/main/java/com/storybrain/app/ui/InformationArchitectureScreens.kt").readText()
        val viewModel = java.io.File("src/main/java/com/storybrain/app/ui/AppViewModel.kt").readText()
        val repository = java.io.File("src/main/java/com/storybrain/app/data/StoryRepository.kt").readText()
        assertTrue(screen.contains("confirmDelete = true"))
        assertTrue(screen.contains("Text(if (deleting) \"正在删除…\" else \"确认删除\""))
        assertTrue(viewModel.contains("ttsEngine.stageAudioDeletion(bookId, chapterIds)"))
        assertTrue(viewModel.contains("ttsEngine.restoreAudioDeletion(trash)"))
        assertTrue(viewModel.contains("ttsEngine.commitAudioDeletion(trash)"))
        assertTrue(repository.contains("dao.deleteMemoryFtsForBook(bookId)"))
        assertTrue(repository.contains("dao.deleteRelationsForBook(bookId)"))
        assertTrue(repository.contains("dao.deletePlotNodesForBook(bookId)"))
        assertTrue(repository.contains("dao.deleteBook(bookId)"))
    }

    @Test
    fun chapterStatusDistinguishesCurrentReadUnreadAndGeneratedAudio() {
        assertEquals("当前", BookChapterStatus.label(3, 3, TaskStatus.PENDING.name))
        assertEquals("已读", BookChapterStatus.label(2, 3, TaskStatus.PENDING.name))
        assertEquals("未读", BookChapterStatus.label(4, 3, TaskStatus.PENDING.name))
        assertEquals("音频", BookChapterStatus.label(4, 3, TaskStatus.COMPLETED.name))
        val screen = java.io.File("src/main/java/com/storybrain/app/ui/InformationArchitectureScreens.kt").readText()
        assertTrue(screen.contains("BookChapterStatus.label("))
    }

    @Test
    fun existingCharacterBindingCanAlwaysBeClearedEvenWhenVoicePoolIsEmpty() {
        assertTrue(VoiceBindingMenuPolicy.enabled(hasBinding = true, voiceCount = 0))
        assertTrue(VoiceBindingMenuPolicy.enabled(hasBinding = false, voiceCount = 1))
        assertEquals(false, VoiceBindingMenuPolicy.enabled(hasBinding = false, voiceCount = 0))
    }

    @Test
    fun parsesOnlyExplicitNonEmptyTitleAndAuthorMetadata() {
        assertEquals(BookHeader("银河铁道之夜", "宫泽贤治"), BookHeaderParser.parse("《银河铁道之夜》 作者：[日] 宫泽贤治"))
        assertEquals(BookHeader("夜航船 作者：", null), BookHeaderParser.parse("夜航船 作者："))
        assertEquals(BookHeader("夜航船", null), BookHeaderParser.parse("夜航船"))
    }

    @Test
    fun everyBookLevelActionIsReachableWithinTwoClicks() {
        val expected = mapOf(
            BookLevelAction.READ to listOf(AppDestination.Reader),
            BookLevelAction.ANALYZE to listOf(AppDestination.Story, AppDestination.Analysis),
            BookLevelAction.GRAPH to listOf(AppDestination.Story, AppDestination.Graph),
            BookLevelAction.MEMORY to listOf(AppDestination.Story, AppDestination.Memory),
            BookLevelAction.BOOK_ENGINE to listOf(AppDestination.Audio, AppDestination.AudioEngine),
            BookLevelAction.CHARACTER_AND_NARRATOR_VOICES to listOf(AppDestination.Audio, AppDestination.AudioVoices),
            BookLevelAction.CHAPTER_AUDIO to listOf(AppDestination.Audio, AppDestination.AudioChapters),
            BookLevelAction.DELETE to emptyList()
        )

        assertEquals(expected, BookHomeContract.actionPaths)
        assertTrue(BookHomeContract.actionPaths.values.all { it.size <= 2 })
        assertTrue(BookHomeContract.directActions.filterNot { it == BookLevelAction.DELETE }.all { BookHomeContract.clickCount(it) <= 2 })
        assertEquals(3, BookHomeContract.clickCount(BookLevelAction.DELETE))
    }
}
