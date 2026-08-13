package com.storybrain.app.ui.navigation

sealed class AppDestination(
    val route: String,
    val showRootBar: Boolean = false,
    val arguments: List<String> = emptyList()
) {
    object Library : AppDestination("library", showRootBar = true)
    object Search : AppDestination("search")
    object My : AppDestination("my", showRootBar = true)
    object Import : AppDestination("import")
    object Book : AppDestination("book/{bookId}", arguments = listOf("bookId"))
    object Chapters : AppDestination("chapters/{bookId}", arguments = listOf("bookId"))
    object Reader : AppDestination("reader/{bookId}/{chapterId}", arguments = listOf("bookId", "chapterId"))
    object Story : AppDestination("story/{bookId}", arguments = listOf("bookId"))
    object Analysis : AppDestination("analysis/{bookId}", arguments = listOf("bookId"))
    object Graph : AppDestination("graph/{bookId}", arguments = listOf("bookId"))
    object Memory : AppDestination("memory/{bookId}", arguments = listOf("bookId"))
    object Audio : AppDestination("audio/{bookId}", arguments = listOf("bookId"))
    object AudioEngine : AppDestination("audio-engine/{bookId}", arguments = listOf("bookId"))
    object AudioVoices : AppDestination("audio-voices/{bookId}", arguments = listOf("bookId"))
    object AudioChapters : AppDestination("audio-chapters/{bookId}", arguments = listOf("bookId"))
    object CharacterChat : AppDestination(
        "character-chat/{bookId}/{characterId}",
        arguments = listOf("bookId", "characterId")
    )
    object Llm : AppDestination("my/llm")
    object LlmConnection : AppDestination("my/llm/connection")
    object LlmModel : AppDestination("my/llm/model")
    object Tts : AppDestination("my/tts")
    object TtsConfig : AppDestination("my/tts/{profileId}", arguments = listOf("profileId"))
    object Voices : AppDestination("my/voices")
    object VoicePool : AppDestination("my/voices/{profileId}", arguments = listOf("profileId"))
    object TaskRunLog : AppDestination("my/run-log")
    object About : AppDestination("my/about")
}
