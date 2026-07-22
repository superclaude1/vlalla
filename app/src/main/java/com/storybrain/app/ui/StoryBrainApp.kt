package com.storybrain.app.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    const val Tasks = "tasks"
    const val Import = "import"
    const val Book = "book/{bookId}"
    const val Reader = "reader/{bookId}/{chapterId}?offset={offset}"
    const val Brain = "brain/{bookId}"
    const val Memory = "memory/{bookId}"
    const val CharacterChat = "character-chat/{bookId}/{characterId}"

    fun book(id: String) = "book/$id"
    fun reader(bookId: String, chapterId: String, offset: Int = -1) = "reader/$bookId/$chapterId?offset=$offset"
    fun brain(bookId: String) = "brain/$bookId"
    fun memory(bookId: String) = "memory/$bookId"
    fun characterChat(bookId: String, characterId: String) = "character-chat/$bookId/$characterId"
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
    val showMiniPlayer = playback.hasMedia && route != Routes.Reader && route != Routes.Import
    var showExpandedPlayer by remember { mutableStateOf(false) }

    if (showExpandedPlayer) {
        ExpandedPlayerSheet(playback, playbackViewModel) { showExpandedPlayer = false }
    }

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
                if (showMiniPlayer) MiniPlayerBar(playback, playbackViewModel) { showExpandedPlayer = true }
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
            SettingsScreen()
        }
        composable(Routes.Tasks) {
            TaskScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel<TaskViewModel>(),
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
            BookScreen(
                bookId = bookId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onReadChapter = { navController.navigate(Routes.reader(bookId, it)) },
                onOpenBrain = { navController.navigate(Routes.brain(bookId)) },
                onOpenMemory = { navController.navigate(Routes.memory(bookId)) }
            )
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
                }
            )
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
                onBack = { navController.popBackStack() }
            )
        }
    }
    }
}
