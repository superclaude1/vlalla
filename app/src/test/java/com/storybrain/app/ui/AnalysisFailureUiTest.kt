package com.storybrain.app.ui

import com.storybrain.app.analysis.AnalysisProgress
import com.storybrain.app.analysis.AnalysisUsage
import com.storybrain.app.settings.UsageQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisFailureUiTest {
    @Test
    fun failurePromptIdentifiesStageBatchAndRetryAttemptWithoutSensitiveData() {
        val state = AnalysisUiState(
            bookId = "book-1",
            message = "网络请求失败，请检查 API 地址和网络后重试。",
            isError = true,
            failureStage = "响应",
            failedBatch = 2,
            totalBatches = 4,
            retryAttempt = 3
        )

        val prompt = StoryAnalysisPolicy.failurePrompt(state)

        assertEquals("第 2/4 批 · 响应阶段 · 已重试 2/2 次", prompt)
        assertTrue(!prompt.orEmpty().contains("api-key"))
        assertTrue(!prompt.orEmpty().contains("正文"))
    }

    @Test
    fun batchProgressUpdatesChaptersAndUsageWithoutClearingExistingState() {
        val previous = AnalysisUiState(bookId = "book-1", running = true, status = AnalysisStatus.RUNNING)
        val progress = AnalysisProgress(
            completed = 7,
            usage = AnalysisUsage(11, 5, 16, UsageQuality.COMPLETE)
        )

        val updated = previous.withProgress(progress)

        assertEquals(true, updated.running)
        assertEquals(7, updated.completedChapters)
        assertEquals(11, updated.promptTokens)
        assertEquals(5, updated.completionTokens)
        assertEquals(16, updated.totalTokens)
        assertEquals(UsageQuality.COMPLETE, updated.usageQuality)
    }

    @Test
    fun terminalFailurePreservesLastCompletedProgressAndUsage() {
        val progressed = AnalysisUiState(
            bookId = "book-1", running = true, status = AnalysisStatus.RUNNING,
            completedChapters = 7, promptTokens = 11, completionTokens = 5,
            totalTokens = 16, usageQuality = UsageQuality.COMPLETE
        )

        val failed = progressed.asFailure("失败", null)

        assertEquals(7, failed.completedChapters)
        assertEquals(16, failed.totalTokens)
        assertEquals(UsageQuality.COMPLETE, failed.usageQuality)
    }
}
