package com.storybrain.app.ui

import kotlin.math.roundToInt

data class GraphMetrics(val characters: Int, val relations: Int, val plots: Int)

object StoryGraphMetrics {
    fun metrics(characterCount: Int, relationCount: Int, plotCount: Int): GraphMetrics =
        GraphMetrics(characterCount, relationCount, plotCount)

    fun importancePercent(score: Float): String =
        "${(score.coerceIn(0f, 1f) * 100).roundToInt()}%"
}
