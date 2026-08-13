package com.storybrain.app.settings

import com.storybrain.app.data.LlmApiProfileEntity
import com.storybrain.app.data.LlmModelEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LlmProfileSelectionTest {
    @Test
    fun modelIdentityIncludesApiProfileAndGroupingKeepsApisSeparate() {
        val profiles = listOf(
            LlmApiProfileEntity("api-a", "主 API", "https://a.example/v1", 1L),
            LlmApiProfileEntity("api-b", "备用 API", "https://b.example/v1", 2L)
        )
        val models = listOf(
            LlmModelEntity("api-b", "same-model", 3L),
            LlmModelEntity("api-a", "same-model", 4L),
            LlmModelEntity("api-a", "other-model", 5L)
        )

        val groups = groupLlmModels(profiles, models)

        assertEquals(listOf("api-a", "api-b"), groups.map { it.profile.id })
        assertEquals(
            listOf(
                LlmModelIdentity("api-a", "other-model"),
                LlmModelIdentity("api-a", "same-model")
            ),
            groups.first().models.map { it.identity }
        )
        assertEquals(LlmModelIdentity("api-b", "same-model"), groups.last().models.single().identity)
    }

    @Test
    fun taskSnapshotIsAnImmutableValueOfProfileModelAndCredential() {
        val snapshot = LlmProfileSnapshot("api-a", "https://a.example/v1", "secret-a", "model-a")
        val changedSelection = LlmModelIdentity("api-b", "model-b")

        assertEquals("api-a", snapshot.apiProfileId)
        assertEquals("https://a.example/v1", snapshot.baseUrl)
        assertEquals("secret-a", snapshot.apiKey)
        assertEquals("model-a", snapshot.modelId)
        assertEquals("api-b", changedSelection.apiProfileId)
    }
}
