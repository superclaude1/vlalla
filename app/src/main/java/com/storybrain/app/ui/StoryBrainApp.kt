package com.storybrain.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private object Routes {
    const val Library = "library"
    const val Settings = "settings"
    const val SettingsAppearance = "settings/appearance"
    const val SettingsReading = "settings/reading"
    const val SettingsLlm = "settings/llm"
    const val SettingsTts = "settings/tts"
    const val SettingsData = "settings/data"
    const val SettingsDiagnostics = "settings/diagnostics"
    const val Tasks = "tasks"
    const val TaskDetail = "task/{workName}"
    const val Import = "import"
    const val Book = "book/{bookId}"
    const val BookChapters = "book/{bookId}/chapters"
    const val BookAnalysis = "book/{bookId}/analysis"
    const val BookPlot = "book/{bookId}/plot"
    const val BookCharacters = "book/{bookId}/characters"
    const val BookRelations = "book/{bookId}/relations"
    const val BookLocations = "book/{bookId}/locations"
    const val BookTts = "book/{bookId}/tts"
    const val Reader = "reader/{bookId}/{chapterId}?offset={offset}"
    const val ReaderContents = "reader-tools/{bookId}/{chapterId}/contents"
    const val ReaderBookmarks = "reader-tools/{bookId}/{chapterId}/bookmarks"
    const val ReaderNotes = "reader-tools/{bookId}/{chapterId}/notes"
    const val ReaderSearch = "reader-tools/{bookId}/{chapterId}/search"
    const val ReaderAppearance = "reader-tools/{bookId}/{chapterId}/appearance"
    const val ReaderSettings = "reader-tools/{bookId}/{chapterId}/settings"
    const val Player = "player"
    const val Brain = "brain/{bookId}"
    const val Memory = "memory/{bookId}"
    const val CharacterChat = "character-chat/{bookId}/{characterId}"
    const val MemoryPicker = "memory-picker/{bookId}/{characterId}/{sessionId}?suggestion={suggestion}"

    fun book(id: String) = "book/$id"
    fun task(workName: String) = "task/${Uri.encode(workName)}"
    fun bookFeature(id: String, feature: String) = "book/$id/$feature"
    fun reader(bookId: String, chapterId: String, offset: Int = -1) = "reader/$bookId/$chapterId?offset=$offset"
    fun readerTool(bookId: String, chapterId: String, tool: String) = "reader-tools/$bookId/$chapterId/$tool"
    fun brain(bookId: String) = "brain/$bookId"
    fun memory(bookId: String) = "memory/$bookId"
    fun characterChat(bookId: String, characterId: String) = "character-chat/$bookId/$characterId"
    fun memoryPicker(bookId: String, characterId: String, sessionId: String, suggestion: String) =
        "memory-picker/$bookId/$characterId/$sessionId?suggestion=${Uri.encode(suggestion)}"
}

@Composable
fun StoryBrainApp(
    viewModel: AppViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
    playbackViewModel: PlaybackViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val playback by playbackViewModel.uiState.collectAsStateWithLifecycle()
    val primaryRoute = route in setOf(Routes.Library, Routes.Tasks, Routes.Settings)
    val showMiniPlayer = playback.hasMedia && route != Routes.Reader && route != Routes.Import && route != Routes.Player

    fun navigatePrimary(destination: String) {
        navController.navigate(destination) {
            popUpTo(Routes.Library) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                if (showMiniPlayer) MiniPlayerBar(playback, playbackViewModel) { navController.navigate(Routes.Player) }
                if (primaryRoute) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = route == Routes.Library,
                            onClick = { navigatePrimary(Routes.Library) },
                            icon = { Icon(Icons.Rounded.AutoStories, null) },
                            label = { Text("书架") }
                        )
                        NavigationBarItem(
                            selected = route == Routes.Tasks,
                            onClick = { navigatePrimary(Routes.Tasks) },
                            icon = { Icon(Icons.Rounded.TaskAlt, null) },
                            label = { Text("任务") }
                        )
                        NavigationBarItem(
                            selected = route == Routes.Settings,
                            onClick = { navigatePrimary(Routes.Settings) },
                            icon = { Icon(Icons.Rounded.Settings, null) },
                            label = { Text("设置") }
                        )
                    }
                }
            }
        }
    ) { outerPadding ->
    NavHost(navController = navController, startDestination = Routes.Library, modifier = Modifier.padding(outerPadding)) {
        composable(Routes.Library) {
            LibraryScreen(
                viewModel = libraryViewModel,
                onImportStarted = { navController.navigate(Routes.Import) },
                onOpenBook = { navController.navigate(Routes.book(it)) }
            )
        }
        composable(Routes.Settings) {
            SettingsHubScreen(
                onAppearance = { navController.navigate(Routes.SettingsAppearance) },
                onReading = { navController.navigate(Routes.SettingsReading) },
                onLlm = { navController.navigate(Routes.SettingsLlm) },
                onTts = { navController.navigate(Routes.SettingsTts) },
                onData = { navController.navigate(Routes.SettingsData) },
                onDiagnostics = { navController.navigate(Routes.SettingsDiagnostics) }
            )
        }
        composable(Routes.SettingsAppearance) { AppearanceSettingsScreen { navController.popBackStack() } }
        composable(Routes.SettingsReading) { SettingsScreen(section = SettingsSection.READING, onBack = { navController.popBackStack() }) }
        composable(Routes.SettingsLlm) { SettingsScreen(section = SettingsSection.LLM, onBack = { navController.popBackStack() }) }
        composable(Routes.SettingsTts) { SettingsScreen(section = SettingsSection.TTS, onBack = { navController.popBackStack() }) }
        composable(Routes.SettingsData) { InformationSettingsScreen(InformationSettingsPage.DATA) { navController.popBackStack() } }
        composable(Routes.SettingsDiagnostics) { InformationSettingsScreen(InformationSettingsPage.DIAGNOSTICS) { navController.popBackStack() } }
        composable(Routes.Tasks) {
            TaskScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel<TaskViewModel>(),
                onOpenBook = { navController.navigate(Routes.book(it)) },
                onOpenReader = { bookId, chapterId -> navController.navigate(Routes.reader(bookId, chapterId)) },
                onOpenTask = { navController.navigate(Routes.task(it)) }
            )
        }
        composable(
            Routes.TaskDetail,
            arguments = listOf(navArgument("workName") { type = NavType.StringType })
        ) { entry ->
            TaskDetailScreen(
                workName = entry.arguments?.getString("workName").orEmpty(),
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel<TaskViewModel>(),
                onBack = { navController.popBackStack() },
                onOpenBook = { navController.navigate(Routes.book(it)) },
                onOpenReader = { bookId, chapterId -> navController.navigate(Routes.reader(bookId, chapterId)) }
            )
        }
        composable(Routes.Import) {
            ImportPreviewScreen(
                viewModel = libraryViewModel,
                onBack = {
                    libraryViewModel.cancelImport()
                    navController.popBackStack()
                },
                onImported = { bookId ->
                    navController.navigate(Routes.book(bookId)) {
                        popUpTo(Routes.Library)
                    }
                }
            )
        }
        composable(
            route = Routes.Book,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) { entry ->
            val bookId = entry.arguments?.getString("bookId").orEmpty()
            BookHubScreen(
                bookId = bookId,
                viewModel = viewModel,
                playbackViewModel = playbackViewModel,
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate(Routes.reader(bookId, it)) },
                onOpenChapters = { navController.navigate(Routes.bookFeature(bookId, "chapters")) },
                onOpenAnalysis = { navController.navigate(Routes.bookFeature(bookId, "analysis")) },
                onOpenPlot = { navController.navigate(Routes.bookFeature(bookId, "plot")) },
                onOpenCharacters = { navController.navigate(Routes.bookFeature(bookId, "characters")) },
                onOpenRelations = { navController.navigate(Routes.bookFeature(bookId, "relations")) },
                onOpenLocations = { navController.navigate(Routes.bookFeature(bookId, "locations")) },
                onOpenMemory = { navController.navigate(Routes.memory(bookId)) },
                onOpenTts = { navController.navigate(Routes.bookFeature(bookId, "tts")) }
            )
        }
        listOf(
            Routes.BookChapters to BookFeaturePage.CHAPTERS,
            Routes.BookAnalysis to BookFeaturePage.ANALYSIS,
            Routes.BookPlot to BookFeaturePage.PLOT,
            Routes.BookCharacters to BookFeaturePage.CHARACTERS,
            Routes.BookRelations to BookFeaturePage.RELATIONS,
            Routes.BookLocations to BookFeaturePage.LOCATIONS,
            Routes.BookTts to BookFeaturePage.TTS
        ).forEach { (featureRoute, page) ->
            composable(featureRoute, arguments = listOf(navArgument("bookId") { type = NavType.StringType })) { entry ->
                val bookId = entry.arguments?.getString("bookId").orEmpty()
                BookFeatureScreen(
                    bookId = bookId,
                    page = page,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onReadChapter = { navController.navigate(Routes.reader(bookId, it)) }
                )
            }
        }
        composable(
            route = Routes.Reader,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("chapterId") { type = NavType.StringType },
                navArgument("offset") { type = NavType.IntType; defaultValue = -1 }
            )
        ) { entry ->
            val bookId = entry.arguments?.getString("bookId").orEmpty()
            ReaderExperienceScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ReaderViewModel>(),
                appViewModel = viewModel,
                playbackViewModel = playbackViewModel,
                onBack = { navController.popBackStack() },
                onOpenChapter = { nextId, offset ->
                    navController.navigate(Routes.reader(bookId, nextId, offset)) {
                        popUpTo(entry.destination.id) { inclusive = true }
                    }
                },
                onOpenContents = { navController.navigate(Routes.readerTool(bookId, entry.arguments?.getString("chapterId").orEmpty(), "contents")) },
                onOpenSearch = { navController.navigate(Routes.readerTool(bookId, entry.arguments?.getString("chapterId").orEmpty(), "search")) },
                onOpenAppearance = { navController.navigate(Routes.readerTool(bookId, entry.arguments?.getString("chapterId").orEmpty(), "appearance")) },
                onOpenSettings = { navController.navigate(Routes.readerTool(bookId, entry.arguments?.getString("chapterId").orEmpty(), "settings")) }
            )
        }
        composable(Routes.ReaderContents) { entry ->
            val bookId = entry.arguments?.getString("bookId").orEmpty()
            val chapterId = entry.arguments?.getString("chapterId").orEmpty()
            ReaderContentsScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ReaderViewModel>(),
                onBack = { navController.popBackStack() },
                onOpenChapter = { id, offset -> navController.navigate(Routes.reader(bookId, id, offset)) },
                onOpenBookmarks = { navController.navigate(Routes.readerTool(bookId, chapterId, "bookmarks")) },
                onOpenNotes = { navController.navigate(Routes.readerTool(bookId, chapterId, "notes")) }
            )
        }
        composable(Routes.ReaderBookmarks) { entry ->
            val bookId = entry.arguments?.getString("bookId").orEmpty()
            ReaderMarksScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ReaderViewModel>(),
                type = com.storybrain.app.data.ReadingMarkType.BOOKMARK,
                onBack = { navController.popBackStack() },
                onOpen = { id, offset -> navController.navigate(Routes.reader(bookId, id, offset)) }
            )
        }
        composable(Routes.ReaderNotes) { entry ->
            val bookId = entry.arguments?.getString("bookId").orEmpty()
            ReaderMarksScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ReaderViewModel>(),
                type = com.storybrain.app.data.ReadingMarkType.NOTE,
                onBack = { navController.popBackStack() },
                onOpen = { id, offset -> navController.navigate(Routes.reader(bookId, id, offset)) }
            )
        }
        composable(Routes.ReaderSearch) { entry ->
            val bookId = entry.arguments?.getString("bookId").orEmpty()
            ReaderSearchScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ReaderViewModel>(),
                onBack = { navController.popBackStack() },
                onOpen = { id, offset -> navController.navigate(Routes.reader(bookId, id, offset)) }
            )
        }
        composable(Routes.ReaderAppearance) {
            ReaderPreferenceScreen(androidx.lifecycle.viewmodel.compose.viewModel<ReaderViewModel>(), ReaderPreferencePage.APPEARANCE) { navController.popBackStack() }
        }
        composable(Routes.ReaderSettings) {
            ReaderPreferenceScreen(androidx.lifecycle.viewmodel.compose.viewModel<ReaderViewModel>(), ReaderPreferencePage.SETTINGS) { navController.popBackStack() }
        }
        composable(Routes.Player) {
            FullPlayerScreen(playback, playbackViewModel) { navController.popBackStack() }
        }
        composable(
            route = Routes.Brain,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) { entry ->
            StoryBrainScreen(
                bookId = entry.arguments?.getString("bookId").orEmpty(),
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenMemory = { navController.navigate(Routes.memory(entry.arguments?.getString("bookId").orEmpty())) },
                onChatCharacter = { characterId ->
                    navController.navigate(Routes.characterChat(entry.arguments?.getString("bookId").orEmpty(), characterId))
                }
            )
        }
        composable(
            route = Routes.Memory,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) { entry ->
            MemoryCenterScreen(
                bookId = entry.arguments?.getString("bookId").orEmpty(),
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.CharacterChat,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("characterId") { type = NavType.StringType }
            )
        ) { entry ->
            CharacterChatScreen(
                bookId = entry.arguments?.getString("bookId").orEmpty(),
                characterId = entry.arguments?.getString("characterId").orEmpty(),
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenMemoryPicker = { bookId, characterId, sessionId, suggestion ->
                    navController.navigate(Routes.memoryPicker(bookId, characterId, sessionId, suggestion))
                }
            )
        }
        composable(
            route = Routes.MemoryPicker,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("characterId") { type = NavType.StringType },
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("suggestion") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            MemoryPickerScreen(
                bookId = entry.arguments?.getString("bookId").orEmpty(),
                characterId = entry.arguments?.getString("characterId").orEmpty(),
                sessionId = entry.arguments?.getString("sessionId").orEmpty(),
                suggestionText = entry.arguments?.getString("suggestion").orEmpty(),
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
    }
}
