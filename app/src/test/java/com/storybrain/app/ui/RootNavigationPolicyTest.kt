package com.storybrain.app.ui

import com.storybrain.app.ui.navigation.AppDestination
import com.storybrain.app.ui.navigation.RootNavigationPolicy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootNavigationPolicyTest {
    private val ui = File("src/main/java/com/storybrain/app/ui")

    @Test
    fun rootBarVisibilityComesFromCanonicalDestinations() {
        assertEquals(AppDestination.Library, RootNavigationPolicy.destinationFor("library"))
        assertEquals(AppDestination.My, RootNavigationPolicy.destinationFor("my"))
        assertTrue(RootNavigationPolicy.showsRootBar("library"))
        assertTrue(RootNavigationPolicy.showsRootBar("my"))
        assertFalse(RootNavigationPolicy.showsRootBar("book/{bookId}"))
        assertFalse(RootNavigationPolicy.showsRootBar("my/llm"))
        assertNull(RootNavigationPolicy.destinationFor("not-registered"))
    }

    @Test
    fun rootTabSwitchesUseStateSavingSingleTopPolicy() {
        val policy = RootNavigationPolicy.tabSwitch
        assertTrue(policy.launchSingleTop)
        assertTrue(policy.saveState)
        assertTrue(policy.restoreState)
        assertEquals(AppDestination.Library, policy.popUpTo)
    }

    @Test
    fun appOwnsBottomBarWhileRootScreensDoNot() {
        val rootScaffold = ui.resolve("navigation/RootScaffold.kt").readText()
        val screens = ui.resolve("Screens.kt").readText()
        val settings = ui.resolve("SettingsScreen.kt").readText()

        assertTrue(rootScaffold.contains("fun RootScaffold("))
        assertTrue(rootScaffold.contains("NavigationBar"))
        assertFalse(screens.substringAfter("fun LibraryScreen(").substringBefore("fun SearchScreen(").contains("NavigationBar"))
        assertFalse(settings.substringAfter("fun MyScreen(").substringBefore("fun LlmSettingsHubScreen(").contains("onBack"))
        assertFalse(settings.substringAfter("fun MyScreen(").substringBefore("fun LlmSettingsHubScreen(").contains("BackBar"))
    }
}
