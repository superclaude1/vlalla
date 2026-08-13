package com.storybrain.app.ui

import com.storybrain.app.ui.navigation.AppDestination
import com.storybrain.app.ui.navigation.AppDestinations
import com.storybrain.app.ui.navigation.AppFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalFeatureParityTest {
    @Test
    fun everyOriginalFeatureHasOneCanonicalReachableDestination() {
        assertEquals(
            setOf(
                "LIBRARY", "LIBRARY_SORT", "LIBRARY_SEARCH", "IMPORT", "BOOK_HOME", "READER", "STORY_ANALYSIS",
                "STORY_GRAPH", "CHARACTERS", "CHARACTER_CHAT", "CHAT_MEMORY",
                "MEMORY_LIBRARY", "CHAPTER_AUDIO", "AUDIO_ENGINE", "LLM_SETTINGS",
                "TTS_SETTINGS", "VOICE_LIBRARY", "SECURITY_AND_VERSION", "DELETE_CLEANUP"
            ),
            AppFeature.entries.map { it.name }.toSet()
        )

        AppFeature.entries.forEach { feature ->
            assertSame(feature.destination, AppDestinations.byRoute.getValue(feature.destination.route))
        }
    }

    @Test
    fun productionDestinationRegistryContainsEveryRealRouteExactlyOnce() {
        val expectedRoutes = setOf(
            "library", "search", "my", "import", "book/{bookId}", "chapters/{bookId}",
            "reader/{bookId}/{chapterId}", "story/{bookId}", "analysis/{bookId}",
            "graph/{bookId}", "memory/{bookId}", "audio/{bookId}",
            "audio-engine/{bookId}", "audio-voices/{bookId}", "audio-chapters/{bookId}",
            "character-chat/{bookId}/{characterId}", "my/llm", "my/llm/connection",
            "my/llm/model", "my/tts", "my/tts/{profileId}", "my/voices",
            "my/voices/{profileId}", "my/run-log", "my/about"
        )

        assertEquals(expectedRoutes, AppDestinations.all.map { it.route }.toSet())
        assertEquals(AppDestinations.all.size, AppDestinations.byRoute.size)
    }

    @Test
    fun onlyLibraryAndMyAreRootBarDestinations() {
        assertEquals(
            listOf(AppDestination.Library, AppDestination.My),
            AppDestinations.rootTabs
        )
        assertTrue(AppDestination.Library.showRootBar)
        assertTrue(AppDestination.My.showRootBar)

        val detailDestinations = listOf(
            AppDestination.Search, AppDestination.Import, AppDestination.Book,
            AppDestination.Reader, AppDestination.Story, AppDestination.Audio,
            AppDestination.CharacterChat, AppDestination.Llm,
            AppDestination.LlmConnection, AppDestination.LlmModel,
            AppDestination.Tts, AppDestination.TtsConfig,
            AppDestination.Voices, AppDestination.VoicePool, AppDestination.About
        )
        detailDestinations.forEach { assertFalse(it.showRootBar) }
        assertEquals(
            setOf(AppDestination.Library, AppDestination.My),
            AppDestinations.all.filter { it.showRootBar }.toSet()
        )
    }
}
