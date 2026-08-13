package com.storybrain.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationArchitectureContractTest {
    @Test
    fun storyHubHasOnlyThreeRowsAndGraphUsesTabsInsteadOfThirdLevelRoutes() {
        assertEquals(listOf("分析", "图谱", "记忆"), NavigationArchitecture.storyHubRows)
        assertEquals(listOf("剧情", "角色", "地点"), NavigationArchitecture.graphTabs)
        assertFalse(NavigationArchitecture.routes.any { it.startsWith("story-plot/") })
        assertFalse(NavigationArchitecture.routes.any { it.startsWith("story-characters/") })
        assertFalse(NavigationArchitecture.routes.any { it.startsWith("story-locations/") })
    }

    @Test
    fun audioHubRetainsThreeRealActionRows() {
        assertEquals(listOf("引擎", "角色音色", "章节音频"), NavigationArchitecture.audioHubRows)
        assertTrue(NavigationArchitecture.routes.contains("audio-voices/{bookId}"))
    }

    @Test
    fun architectureIncludesShellAndChatDestinations() {
        assertEquals("search", NavigationArchitecture.SearchRoute)
        assertEquals("my", NavigationArchitecture.MyRoute)
        assertEquals("character-chat/{bookId}/{characterId}", NavigationArchitecture.ChatRoute)
        assertTrue(NavigationArchitecture.routes.containsAll(listOf(
            NavigationArchitecture.SearchRoute,
            NavigationArchitecture.MyRoute,
            NavigationArchitecture.ChatRoute
        )))
    }
}
