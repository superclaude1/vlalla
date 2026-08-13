package com.storybrain.app.ui.navigation

object AppDestinations {
    val all = listOf(
        AppDestination.Library,
        AppDestination.Search,
        AppDestination.My,
        AppDestination.Import,
        AppDestination.Book,
        AppDestination.Chapters,
        AppDestination.Reader,
        AppDestination.Story,
        AppDestination.Analysis,
        AppDestination.Graph,
        AppDestination.Memory,
        AppDestination.Audio,
        AppDestination.AudioEngine,
        AppDestination.AudioVoices,
        AppDestination.AudioChapters,
        AppDestination.CharacterChat,
        AppDestination.Llm,
        AppDestination.LlmConnection,
        AppDestination.LlmModel,
        AppDestination.Tts,
        AppDestination.TtsConfig,
        AppDestination.Voices,
        AppDestination.VoicePool,
        AppDestination.TaskRunLog,
        AppDestination.About
    )

    val byRoute = all.associateBy(AppDestination::route)
    val rootTabs = listOf(AppDestination.Library, AppDestination.My)

    fun book(id: String) = "book/$id"
    fun chapters(id: String) = "chapters/$id"
    fun reader(bookId: String, chapterId: String) = "reader/$bookId/$chapterId"
    fun story(id: String) = "story/$id"
    fun analysis(id: String) = "analysis/$id"
    fun graph(id: String) = "graph/$id"
    fun memory(id: String) = "memory/$id"
    fun audio(id: String) = "audio/$id"
    fun audioEngine(id: String) = "audio-engine/$id"
    fun audioVoices(id: String) = "audio-voices/$id"
    fun audioChapters(id: String) = "audio-chapters/$id"
    fun characterChat(bookId: String, characterId: String) = "character-chat/$bookId/$characterId"
    fun ttsConfig(id: String) = "my/tts/$id"
    fun voicePool(id: String) = "my/voices/$id"
}
