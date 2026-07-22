package com.storybrain.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private object Routes {
    const val Library = "library"
    const val Settings = "settings"
    const val Import = "import"
    const val Book = "book/{bookId}"
    const val Reader = "reader/{bookId}/{chapterId}"
    const val Brain = "brain/{bookId}"
    const val Memory = "memory/{bookId}"
    const val CharacterChat = "character-chat/{bookId}/{characterId}"

    fun book(id: String) = "book/$id"
    fun reader(bookId: String, chapterId: String) = "reader/$bookId/$chapterId"
    fun brain(bookId: String) = "brain/$bookId"
    fun memory(bookId: String) = "memory/$bookId"
    fun characterChat(bookId: String, characterId: String) = "character-chat/$bookId/$characterId"
}

@Composable
fun StoryBrainApp(
    viewModel: AppViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel()
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.Library) {
        composable(Routes.Library) {
            LibraryScreen(
                viewModel = libraryViewModel,
                onImportStarted = { navController.navigate(Routes.Import) },
                onOpenBook = { navController.navigate(Routes.book(it)) },
                onOpenSettings = { navController.navigate(Routes.Settings) { launchSingleTop = true } }
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(
                onOpenLibrary = { navController.popBackStack(Routes.Library, inclusive = false) }
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
                navArgument("chapterId") { type = NavType.StringType }
            )
        ) { entry ->
            val bookId = entry.arguments?.getString("bookId").orEmpty()
            val chapterId = entry.arguments?.getString("chapterId").orEmpty()
            ReaderScreen(
                bookId = bookId,
                chapterId = chapterId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenChapter = { nextId ->
                    navController.navigate(Routes.reader(bookId, nextId)) {
                        popUpTo(Routes.reader(bookId, chapterId)) { inclusive = true }
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
