package com.storybrain.app.ui.navigation

/** Original 0.4.1 capabilities and their canonical UI entry destination. */
enum class AppFeature(val destination: AppDestination) {
    LIBRARY(AppDestination.Library),
    LIBRARY_SORT(AppDestination.Library),
    LIBRARY_SEARCH(AppDestination.Search),
    IMPORT(AppDestination.Import),
    BOOK_HOME(AppDestination.Book),
    READER(AppDestination.Reader),
    STORY_ANALYSIS(AppDestination.Analysis),
    STORY_GRAPH(AppDestination.Graph),
    CHARACTERS(AppDestination.Graph),
    CHARACTER_CHAT(AppDestination.CharacterChat),
    CHAT_MEMORY(AppDestination.CharacterChat),
    MEMORY_LIBRARY(AppDestination.Memory),
    CHAPTER_AUDIO(AppDestination.AudioChapters),
    AUDIO_ENGINE(AppDestination.AudioEngine),
    LLM_SETTINGS(AppDestination.Llm),
    TTS_SETTINGS(AppDestination.Tts),
    VOICE_LIBRARY(AppDestination.Voices),
    SECURITY_AND_VERSION(AppDestination.About),
    DELETE_CLEANUP(AppDestination.Book)
}
