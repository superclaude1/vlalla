package com.storybrain.app.data

/** The sound source currently feeding the shared Media3 player. */
enum class NarrationSource {
    PREMIUM_CACHE,
    SYSTEM_TTS
}

/** A concrete, user-visible narration stage. */
enum class NarrationStage {
    IDLE,
    PREPARING,
    PLAYING,
    BUFFERING,
    GENERATING,
    FAILED
}

data class NarrationUiState(
    val source: NarrationSource = NarrationSource.PREMIUM_CACHE,
    val stage: NarrationStage = NarrationStage.IDLE,
    val blockIndex: Int = -1,
    val completedSegments: Int = 0,
    val totalSegments: Int = 0,
    val detail: String? = null,
    val canRetry: Boolean = false,
    val needsVoiceData: Boolean = false
)
