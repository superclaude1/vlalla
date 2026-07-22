package com.storybrain.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPolicyTest {
    @Test
    fun httpsIsAllowedByDefault() {
        assertEquals("https://example.com/v1", EndpointPolicy.requireAllowed("example.com/v1", false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun httpRequiresExplicitConsent() {
        EndpointPolicy.requireAllowed("http://10.0.2.2:8000/v1", false)
    }

    @Test
    fun explicitlyConfirmedHttpIsAllowed() {
        val address = "http://10.0.2.2:8000/v1"
        assertEquals(address, EndpointPolicy.requireAllowed(address, true))
        assertTrue(EndpointPolicy.isInsecure(address))
        assertFalse(EndpointPolicy.isInsecure("https://example.com"))
    }
}
