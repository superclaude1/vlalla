package com.storybrain.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFeatureParityTest {
    private val settingsScreen = File("src/main/java/com/storybrain/app/ui/SettingsScreen.kt").readText()
    private val viewModel = File("src/main/java/com/storybrain/app/settings/SettingsViewModel.kt").readText()

    @Test
    fun llmConnectionShowsSecureKeyStateAndExposesEveryCredentialAction() {
        val connection = settingsScreen
            .substringAfter("SettingsPage.LLM_CONNECTION -> item")
            .substringBefore("SettingsPage.LLM_MODEL -> item")

        assertTrue(connection.contains("已通过 Android Keystore 安全保存"))
        assertTrue(connection.contains("尚未保存 API Key"))
        assertTrue(connection.contains("if (showKey) \"隐藏\" else \"显示\""))
        assertTrue(connection.contains("viewModel::detectModels"))
        assertTrue(connection.contains("viewModel::save"))
        assertTrue(connection.contains("viewModel::clearApiKey"))
        assertTrue(connection.contains("StateMessage(state.message, state.isError)"))
    }

    @Test fun staleModelDetectionCannotOverwriteNewConnectionDraft() {
        val body = viewModel.substringAfter("fun detectModels()").substringBefore("fun save()")
        assertTrue(body.contains("authorizesLlmDetect(identity, _state.value, llmRequestSequence)"))
        assertTrue(viewModel.contains("data class LlmDraftIdentity"))
        assertTrue(viewModel.contains("fun updateBaseUrl(value: String) { invalidateLlmDraft()"))
        assertTrue(viewModel.contains("fun updateApiKey(value: String) { invalidateLlmDraft()"))
    }

    @Test fun staleSavesAndTtsRequestsCannotOverwriteAnotherDraftOrProfile() {
        assertTrue(viewModel.contains("authorizesLlmDraft(identity, _state.value, llmSaveSequence)"))
        assertTrue(viewModel.contains("requestId != ttsSaveSequence"))
        assertTrue(viewModel.contains("sameTtsRequest(identity)"))
        assertTrue(viewModel.contains("sameTtsDraft(snapshot)"))
        assertTrue(viewModel.contains("if (!sameProfileRequest(profileId)) return@launch"))
        assertTrue(settingsScreen.contains("label = { Text(\"Fish 模型\") }"))
        assertTrue(settingsScreen.contains("state.ttsModels.forEach"))
    }
}
