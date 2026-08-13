package com.storybrain.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Neo4jExportRequestPolicyTest {
    @Test
    fun currentRequestMustMatchBookAndRequestIdentity() {
        val state = ExportUiState(bookId = "book-a", requestId = 7L, running = true)
        assertTrue(Neo4jExportRequestPolicy.matches(state, "book-a", 7L))
        assertFalse(Neo4jExportRequestPolicy.matches(state, "book-b", 7L))
        assertFalse(Neo4jExportRequestPolicy.matches(state, "book-a", 8L))
    }

    @Test
    fun anotherRequestCannotStartWhileAnyExportIsActive() {
        assertFalse(Neo4jExportRequestPolicy.canStart(ExportUiState(bookId = "book-a", running = true)))
        assertTrue(Neo4jExportRequestPolicy.canStart(ExportUiState()))
    }
}
