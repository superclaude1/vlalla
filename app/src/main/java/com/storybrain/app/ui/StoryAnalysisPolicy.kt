package com.storybrain.app.ui

data class StoryAnalysisActionState(
    val initializationTarget: Int,
    val remaining: Int,
    val showIncrementControls: Boolean,
    val requestedCount: Int?,
    val chapterCount: Int?,
    val inputValue: String,
    val actionLabel: String
)

object StoryAnalysisPolicy {
    const val initializationLimit = 15

    fun state(done: Int, total: Int, requested: String): StoryAnalysisActionState {
        val safeTotal = total.coerceAtLeast(0)
        val safeDone = done.coerceIn(0, safeTotal)
        val initializationTarget = minOf(initializationLimit, safeTotal)
        val remaining = (safeTotal - safeDone).coerceAtLeast(0)
        val initialized = safeDone >= initializationTarget
        val requestedCount = requested.toIntOrNull()?.coerceIn(1, remaining.coerceAtLeast(1))
        val label = when {
            remaining == 0 -> "已完成全书分析"
            !initialized && safeDone == 0 -> "分析前${initializationTarget}章"
            !initialized -> "继续初始化至第${initializationTarget}章"
            else -> "分析接下来${requestedCount ?: 1}章"
        }
        return StoryAnalysisActionState(
            initializationTarget = initializationTarget,
            remaining = remaining,
            showIncrementControls = initialized,
            requestedCount = requestedCount,
            chapterCount = requestedCount.takeIf { initialized },
            inputValue = (requestedCount ?: 1).toString(),
            actionLabel = label
        )
    }

    fun isRunningForBook(bookId: String, stateBookId: String?, running: Boolean): Boolean =
        stateBookId == bookId && running

    fun messageForBook(bookId: String, stateBookId: String?, message: String?): String? =
        message.takeIf { stateBookId == bookId }

    fun failurePrompt(state: AnalysisUiState): String? {
        if (!state.isError) return null
        val batch = if (state.failedBatch != null && state.totalBatches != null) "第 ${state.failedBatch}/${state.totalBatches} 批" else null
        val stage = state.failureStage?.let { "${it}阶段" }
        val retries = state.retryAttempt.takeIf { it > 1 }?.let { "已重试 ${it - 1}/2 次" }
        return listOfNotNull(batch, stage, retries).joinToString(" · ").ifBlank { "本批次失败" }
    }
}
