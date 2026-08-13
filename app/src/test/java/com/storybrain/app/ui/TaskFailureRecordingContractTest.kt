package com.storybrain.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskFailureRecordingContractTest {
    @Test
    fun appViewModelRecordsAnalysisAndEdgeTtsFailuresThroughSanitizedRepositoryApi() {
        val viewModel = File("src/main/java/com/storybrain/app/ui/AppViewModel.kt").readText()
        val analyzer = File("src/main/java/com/storybrain/app/analysis/LlmStoryAnalyzer.kt").readText()
        assertTrue(viewModel.contains("repository.recordTaskFailure"))
        assertTrue(viewModel.contains("AnalysisFailureException"))
        assertTrue(viewModel.contains("EdgeTtsException"))
        assertTrue(viewModel.contains("NetworkFailureClassifier"))
        assertTrue(analyzer.contains("repository.recordTaskFailure("))
        assertTrue(analyzer.contains("TaskRunType.ANALYSIS"))
        assertTrue(viewModel.contains("TaskRunType.TTS"))
    }
}
