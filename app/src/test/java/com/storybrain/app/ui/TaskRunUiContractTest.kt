package com.storybrain.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRunUiContractTest {
    private val root = File("src/main/java/com/storybrain/app/ui")

    @Test
    fun myScreenExposesRealRunLogDestinationAndAppWiresIt() {
        val settings = root.resolve("SettingsScreen.kt").readText()
        val app = root.resolve("StoryBrainApp.kt").readText()
        val destinations = root.resolve("navigation/AppDestinations.kt").readText()
        val registry = root.resolve("navigation/AppDestinationRegistry.kt").readText()

        assertTrue(settings.contains("运行日志") || settings.contains("onOpenRunLog"))
        assertTrue(settings.contains("onOpenRunLog"))
        assertTrue(settings.contains("TaskRunLogScreen") || File("src/main/java/com/storybrain/app/ui/TaskRunLogScreen.kt").exists())
        assertTrue(app.contains("AppDestination.TaskRunLog -> TaskRunLogScreen"))
        assertTrue(destinations.contains("object TaskRunLog"))
        assertTrue(registry.contains("AppDestination.TaskRunLog"))
    }

    @Test
    fun runLogListsErrorsCopiesSingleEventAndClearsPersistedEvents() {
        val viewModel = root.resolve("AppViewModel.kt").readText()
        val logScreen = root.resolve("TaskRunLogScreen.kt").readText()
        assertTrue(logScreen.contains("事件列表"))
        assertTrue(logScreen.contains("复制"))
        assertTrue(logScreen.contains("清空"))
        assertTrue(logScreen.contains("LocalClipboardManager.current"))
        assertTrue(logScreen.contains("event.id") || logScreen.contains("event ->") || logScreen.contains("TaskEventCard"))
        assertTrue(viewModel.contains("taskEvents"))
        assertTrue(viewModel.contains("clearTaskEvents"))
    }

    @Test
    fun runLogListsUsageAndErrorEventsWithDiagnosticMetadata() {
        val dao = File("src/main/java/com/storybrain/app/data/StoryDao.kt").readText()
        val repository = File("src/main/java/com/storybrain/app/data/StoryRepository.kt").readText()
        val viewModel = root.resolve("AppViewModel.kt").readText()
        val logScreen = root.resolve("TaskRunLogScreen.kt").readText()

        assertTrue(dao.contains("SELECT * FROM task_events ORDER BY createdAt DESC, id DESC"))
        assertTrue(repository.contains("observeTaskEvents"))
        assertTrue(viewModel.contains("taskEvents = repository.observeTaskEvents()"))
        listOf("ERROR", "USAGE", "usageQuality", "promptTokens", "completionTokens", "totalTokens", "requestId", "responseModel").forEach {
            assertTrue("run log should render $it", logScreen.contains(it))
        }
    }
}
