package com.storybrain.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsCancellationContractTest {
    @Test
    fun appViewModelOwnsGenerationJobAndExposesCancellation() {
        val source = File("src/main/java/com/storybrain/app/ui/AppViewModel.kt").readText()

        assertTrue(source.contains("private var ttsJob: Job? = null"))
        assertTrue(source.contains("ttsJob = viewModelScope.launch"))
        assertTrue(source.contains("fun cancelChapterTtsGeneration()"))
        assertTrue(source.contains("ttsJob?.cancel()"))
        assertTrue(source.contains("error is CancellationException"))
        val engine = File("src/main/java/com/storybrain/app/tts/ChapterTtsEngine.kt").readText()
        assertTrue(engine.contains("withContext(NonCancellable)"))
    }
}