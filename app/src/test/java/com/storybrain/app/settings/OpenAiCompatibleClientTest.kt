package com.storybrain.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiCompatibleClientTest {
    @Test
    fun normalizesCommonEndpointForms() {
        assertEquals("https://api.openai.com/v1", OpenAiCompatibleClient.normalizeBaseUrl("api.openai.com"))
        assertEquals("https://api.deepseek.com", OpenAiCompatibleClient.normalizeBaseUrl("https://api.deepseek.com"))
        assertEquals("https://example.com/openai", OpenAiCompatibleClient.normalizeBaseUrl("https://example.com/openai"))
        assertEquals("http://10.0.2.2:8000/v1", OpenAiCompatibleClient.normalizeBaseUrl("http://10.0.2.2:8000/v1/"))
    }
}
