package com.storybrain.app.settings

import com.storybrain.app.data.TtsProviderKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceEndpointDraftPolicyTest {
    @Test
    fun canonicalizesOnlyNetworkBackedTtsProfiles() {
        assertEquals(
            "https://api.fish.audio",
            normalizeTtsEndpointDraft(TtsProviderKind.FISH_AUDIO.name, "HTTPS://API.FISH.AUDIO/")
        )
        assertEquals(
            "https://voice.example/v1",
            normalizeTtsEndpointDraft(TtsProviderKind.OPENAI_COMPATIBLE.name, "voice.example/v1/")
        )
        assertEquals("", normalizeTtsEndpointDraft(TtsProviderKind.EDGE.name, ""))
        assertEquals("", normalizeTtsEndpointDraft(TtsProviderKind.ANDROID_SYSTEM.name, ""))
    }
}
