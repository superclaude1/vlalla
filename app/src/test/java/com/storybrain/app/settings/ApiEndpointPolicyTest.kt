package com.storybrain.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiEndpointPolicyTest {
    @Test
    fun canonicalizesSafeHttpsEndpointWithoutQueryOrFragment() {
        assertEquals(
            "https://api.openai.com/v1",
            ApiEndpointPolicy.normalize("api.openai.com")
        )
        assertEquals(
            "https://example.com/openai",
            ApiEndpointPolicy.normalize("HTTPS://Example.COM/openai/")
        )
        assertEquals(
            "https://example.com/v1%2Ftenant",
            ApiEndpointPolicy.normalize("https://example.com/v1%2Ftenant/")
        )
    }

    @Test
    fun rejectsCredentialsQueryFragmentAndNonHttpsSchemes() {
        listOf(
            "https://user:password@example.com/v1",
            "https://example.com/v1?api_key=secret",
            "https://example.com/v1#token",
            "https://example.com:0/v1",
            "http://example.com/v1",
            "file:///tmp/model"
        ).forEach { endpoint ->
            assertThrows(IllegalArgumentException::class.java) {
                ApiEndpointPolicy.normalize(endpoint)
            }
        }
    }

    @Test
    fun rejectsMissingOrAmbiguousHosts() {
        listOf(
            "https:///v1",
            "https://example.com@evil.example/v1",
            "https://exa mple.com/v1"
        ).forEach { endpoint ->
            assertThrows(IllegalArgumentException::class.java) {
                ApiEndpointPolicy.normalize(endpoint)
            }
        }
    }
}
