package com.storybrain.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryGraphMetricsTest {
    @Test
    fun outerMetricsUseRealCharacterRelationAndPlotCounts() {
        assertEquals(GraphMetrics(4, 7, 9), StoryGraphMetrics.metrics(4, 7, 9))
        val outerScreen = java.io.File("src/main/java/com/storybrain/app/ui/InformationArchitectureScreens.kt").readText()
        assertTrue(outerScreen.contains("viewModel.characters(bookId)"))
        assertTrue(outerScreen.contains("viewModel.relations(bookId)"))
        assertTrue(outerScreen.contains("viewModel.plotNodes(bookId)"))
        assertTrue(outerScreen.contains("GraphMetricText(\"角色\", metrics.characters)"))
        assertTrue(outerScreen.contains("GraphMetricText(\"关系\", metrics.relations)"))
        assertTrue(outerScreen.contains("GraphMetricText(\"事件\", metrics.plots)"))
    }

    @Test
    fun characterImportanceIsRenderedAsClampedRoundedPercentage() {
        assertEquals("0%", StoryGraphMetrics.importancePercent(-0.2f))
        assertEquals("68%", StoryGraphMetrics.importancePercent(0.675f))
        assertEquals("100%", StoryGraphMetrics.importancePercent(1.4f))
    }

    @Test
    fun characterTabContractIncludesReasonRelationsPlotsAndClearableVoiceMenu() {
        val screen = java.io.File("src/main/java/com/storybrain/app/ui/Screens.kt").readText()
        assertTrue(screen.contains("StoryGraphMetrics.importancePercent(character.importanceScore)"))
        assertTrue(screen.contains("character.importanceReason"))
        assertTrue(screen.contains("${'$'}relatedCount 条关系"))
        assertTrue(screen.contains("${'$'}participatedPlotCount 个剧情"))
        assertTrue(screen.contains("VoiceBindingMenuPolicy.enabled(binding != null, sortedVoices.size)"))
    }
}
