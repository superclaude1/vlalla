package com.storybrain.app.settings

import com.storybrain.app.data.LlmApiProfileEntity
import com.storybrain.app.data.LlmModelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiApiSafetyPolicyTest {
    private val profiles = listOf(
        LlmApiProfileEntity("api-a", "API A", "https://a.example/v1", selectedModel = "a-two"),
        LlmApiProfileEntity("api-b", "API B", "https://b.example/v1", selectedModel = "b-one")
    )
    private val models = listOf(
        LlmModelEntity("api-a", "a-one"),
        LlmModelEntity("api-a", "a-two"),
        LlmModelEntity("api-b", "b-one")
    )

    @Test
    fun crossApiModelSelectionLoadsOnlyTheTargetApisModels() {
        val current = SettingsUiState(
            llmProfiles = profiles,
            llmModelGroups = groupLlmModels(profiles, models),
            selectedApiProfileId = "api-a",
            selectedModel = "a-two",
            detectedModels = listOf("a-one", "a-two")
        )

        val selected = selectLlmModelDraft(current, LlmModelIdentity("api-b", "b-one"))

        assertEquals("api-b", selected.selectedApiProfileId)
        assertEquals("b-one", selected.selectedModel)
        assertEquals(listOf("b-one"), selected.detectedModels)
    }

    @Test
    fun completeDraftAuthorizationRejectsEveryChangedFieldAndRequestId() {
        val original = SettingsUiState(
            selectedApiProfileId = "api-a",
            apiDisplayName = "API A",
            baseUrl = "https://a.example/v1",
            apiKeyDraft = "secret-a",
            selectedModel = "a-one",
            detectedModels = listOf("a-one", "a-two")
        )
        val identity = llmDraftIdentity(original, requestId = 7L)

        assertTrue(authorizesLlmDraft(identity, original, currentRequestId = 7L))
        listOf(
            original.copy(selectedApiProfileId = "api-b"),
            original.copy(apiDisplayName = "renamed"),
            original.copy(baseUrl = "https://changed.example/v1"),
            original.copy(apiKeyDraft = "changed-key"),
            original.copy(selectedModel = "a-two"),
            original.copy(detectedModels = listOf("a-two"))
        ).forEach { changed ->
            assertFalse(authorizesLlmDraft(identity, changed, currentRequestId = 7L))
        }
        assertFalse(authorizesLlmDraft(identity, original, currentRequestId = 8L))
    }

    @Test
    fun deletingCurrentApiRequiresAnExplicitReplacementWhileDeletingAnotherKeepsSelection() {
        assertEquals("", selectionAfterProfileDeletion("api-a", "api-a"))
        assertEquals("api-a", selectionAfterProfileDeletion("api-a", "api-b"))
    }
}
