package com.storybrain.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskStatusTest {
    @Test
    fun parsesCancelledAndFallsBackToRetryableFailure() {
        assertEquals(TaskStatus.CANCELLED, TaskStatus.fromStorage("CANCELLED"))
        assertEquals(TaskStatus.FAILED, TaskStatus.fromStorage("FUTURE_STATUS"))
        assertEquals(TaskStatus.FAILED, TaskStatus.fromStorage(null))
    }
}
