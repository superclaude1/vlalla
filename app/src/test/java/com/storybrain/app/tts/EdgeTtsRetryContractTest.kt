package com.storybrain.app.tts

import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeTtsRetryContractTest {
    @Test
    fun edgeTransportFailuresAreEligibleForChapterRetry() {
        val source = java.io.File("src/main/java/com/storybrain/app/tts/ChapterTtsEngine.kt").readText()
        assertTrue(source.contains("is EdgeTtsException -> error.retryable"))
    }
}
