package com.storybrain.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiApiVerticalSliceContractTest {
    private val settingsScreen = File("src/main/java/com/storybrain/app/ui/SettingsScreen.kt").readText()
    private val viewModel = File("src/main/java/com/storybrain/app/settings/SettingsViewModel.kt").readText()

    @Test
    fun settingsShowsApiRecordsCanAddOneAndGroupsModelsByApi() {
        assertTrue(settingsScreen.contains("state.llmProfiles"))
        assertTrue(settingsScreen.contains("viewModel::addLlmProfile"))
        assertTrue(settingsScreen.contains("state.llmModelGroups"))
        assertTrue(settingsScreen.contains("group.profile.displayName"))
        assertTrue(viewModel.contains("LlmModelIdentity"))
    }

    @Test
    fun settingsRequiresConfirmedDeletionAndClearsProfileOwnedState() {
        val repository = File("src/main/java/com/storybrain/app/data/StoryRepository.kt").readText()
        assertTrue(settingsScreen.contains("AlertDialog("))
        assertTrue(settingsScreen.contains("确认删除 API"))
        assertTrue(settingsScreen.contains("viewModel.deleteLlmProfile"))
        assertTrue(viewModel.contains("repository.deleteLlmProfile"))
        assertTrue(viewModel.contains("llmStore.clearApiKey"))
        assertTrue(repository.contains("dao.deleteLlmApiProfile(profileId)"))
    }

    @Test
    fun everyLongRunningLlmServiceCapturesOneSnapshotAtTaskStart() {
        listOf(
            "analysis/LlmStoryAnalyzer.kt" to "analyzeNext",
            "analysis/CharacterChatService.kt" to "send"
        ).forEach { (relative, method) ->
            val source = File("src/main/java/com/storybrain/app/$relative").readText()
            val taskBody = source.substringAfter("suspend fun $method")
            assertTrue("$relative must capture a profile snapshot", taskBody.contains("val profile = settings.snapshot()"))
            assertFalse("$relative must not re-read API keys during the task", taskBody.contains("settings.readApiKey("))
        }
        val engine = File("src/main/java/com/storybrain/app/tts/ChapterTtsEngine.kt").readText()
        assertTrue(engine.contains("val llmConfig = llmSettings.snapshot()"))
        assertTrue(engine.contains("directingService.direct(blocks, llmConfig)"))
    }
}
