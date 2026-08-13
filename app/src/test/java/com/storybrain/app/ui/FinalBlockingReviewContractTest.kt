package com.storybrain.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalBlockingReviewContractTest {
    private val ui = File("src/main/java/com/storybrain/app/ui")

    @Test
    fun settingsRoutesShareOneExplicitViewModelAndGuardRequestedProfile() {
        val app = ui.resolve("StoryBrainApp.kt").readText()
        val settings = ui.resolve("SettingsScreen.kt").readText()
        assertTrue(app.contains("val settingsViewModel: SettingsViewModel = viewModel()"))
        assertTrue(app.contains("SettingsPage.LLM_CONNECTION,") && app.contains("viewModel = settingsViewModel"))
        assertTrue(app.contains("AppDestination.Tts -> TtsServiceListScreen(") && app.contains("onOpenService =") && app.contains("viewModel = settingsViewModel"))
        assertTrue(app.contains("AppDestination.Voices -> VoiceLibraryListScreen(") && app.contains("onOpenPool =") && app.contains("viewModel = settingsViewModel"))
        assertTrue(settings.contains("val profileReady = profileId == null || state.loadedProfileId == profileId"))
        assertTrue(settings.contains("enabled = profileReady"))
    }

    @Test
    fun compactBookHubRetainsRealDeleteFlow() {
        val source = ui.resolve("InformationArchitectureScreens.kt").readText()
        assertTrue(source.contains("DropdownMenuItem(") && source.contains("删除小说"))
        assertTrue(source.contains("viewModel.deleteBook("))
        assertTrue(source.contains("onComplete = onBack"))
        assertTrue(source.contains("deleteError = message"))
    }

    @Test
    fun platformVoicePoolIsEditableAndAudioChaptersOwnPlaybackActions() {
        val settings = ui.resolve("SettingsScreen.kt").readText()
        val audio = ui.resolve("InformationArchitectureScreens.kt").readText()
        assertTrue(settings.contains("searchFishVoices(true)"))
        assertTrue(settings.contains("searchFishVoices(false)"))
        assertTrue(settings.contains("addVoiceToPool(voice, role)"))
        assertTrue(settings.contains("addManualVoice(manualVoiceId, manualVoiceName, role)"))
        assertTrue(audio.contains("viewModel.generateChapterTts(bookId, chapter.id)"))
        assertTrue(audio.contains("viewModel.playChapterTts(chapter.id, chapter.ttsManifestPath.orEmpty())"))
        assertTrue(audio.contains("viewModel.stopChapterTts()"))
        assertFalse(audio.contains("fun AudioChaptersScreen(bookId: String, viewModel: AppViewModel, onBack: () -> Unit, onRead:"))
    }

    @Test
    fun readerUsesOriginalParagraphsAndConfirmableSaveWithFeedback() {
        val screens = ui.resolve("Screens.kt").readText()
        assertTrue(screens.contains("ReaderParagraphs.split(it.content)"))
        assertTrue(screens.contains("TextToChatParser.parse(currentChapter.content, knownSpeakers)"))
        assertTrue(screens.contains("pendingMemory = PendingReadingMemory("))
        assertTrue(screens.contains("content = current.content"))
        assertTrue(screens.contains("memoryAction.message"))
    }

    @Test
    fun staleTtsRequestsCannotOverwriteAnotherProfile() {
        val settings = File("src/main/java/com/storybrain/app/settings/SettingsViewModel.kt").readText()
        assertTrue(settings.contains("if (!sameTtsRequest(identity)) return@onSuccess"))
        assertTrue(settings.contains("if (!sameTtsRequest(identity)) return@onFailure"))
    }

    @Test
    fun savingAChapterMemoryDoesNotSilentlyTruncateAtTwoThousandCharacters() {
        val repository = File("src/main/java/com/storybrain/app/data/StoryRepository.kt").readText()
        val editor = ui.resolve("MemoryScreens.kt").readText()
        assertFalse(repository.contains("content.trim().take(2_000)"))
        assertFalse(editor.contains("content = it.take(2_000)"))
        assertFalse(editor.contains("/2000 字"))
    }
}
