package com.storybrain.app.analysis

import com.storybrain.app.settings.ChatCompletionUsage
import com.storybrain.app.settings.UsageQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisUsageQualityTest {
    @Test
    fun aggregateQualityIsPartialWhenOnlySomeBatchesHaveCompleteUsage() {
        val complete = AnalysisUsage(ChatCompletionUsage.from(10, 5, 15))
        val missing = AnalysisUsage(ChatCompletionUsage.from(null, null, null))

        val aggregate = complete + missing

        assertEquals(UsageQuality.PARTIAL, aggregate.quality)
        assertEquals(10, aggregate.promptTokens)
        assertEquals(5, aggregate.completionTokens)
        assertEquals(15, aggregate.totalTokens)
    }
}