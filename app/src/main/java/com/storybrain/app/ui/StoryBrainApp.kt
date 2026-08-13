package com.storybrain.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storybrain.app.settings.SettingsViewModel
import com.storybrain.app.ui.navigation.AppDestination
import com.storybrain.app.ui.navigation.AppDestinations
import com.storybrain.app.ui.navigation.RootScaffold

@Composable
fun StoryBrainApp(viewModel: AppViewModel = viewModel()) {
    val settingsViewModel: SettingsViewModel = viewModel()
    val nav = rememberNavController()
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    val currentDestination = AppDestinations.byRoute[currentRoute]
    fun back() = nav.popBackStack()

    RootScaffold(
        currentDestination = currentDestination,
        onSelectRoot = { destination ->
            val policy = com.storybrain.app.ui.navigation.RootNavigationPolicy.tabSwitch
            nav.navigate(destination.route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = policy.saveState }
                launchSingleTop = policy.launchSingleTop
                restoreState = policy.restoreState
            }
        }
    ) {
        NavHost(nav, AppDestination.Library.route) {
            AppDestinations.all.forEach { destination ->
                composable(
                    route = destination.route,
                    arguments = destination.arguments.map { name ->
                        navArgument(name) { type = NavType.StringType }
                    }
                ) { entry ->
                    when (destination) {
                        AppDestination.Library -> LibraryScreen(
                            viewModel,
                            { nav.navigate(AppDestination.Import.route) },
                            { nav.navigate(AppDestinations.book(it)) },
                            { nav.navigate(AppDestination.Search.route) }
                        )
                        AppDestination.Search -> SearchScreen(viewModel, ::back) {
                            nav.navigate(AppDestinations.book(it))
                        }
                        AppDestination.My -> MyScreen(
                            { nav.navigate(AppDestination.Llm.route) },
                            { nav.navigate(AppDestination.Tts.route) },
                            { nav.navigate(AppDestination.Voices.route) },
                            { nav.navigate(AppDestination.TaskRunLog.route) },
                            { nav.navigate(AppDestination.About.route) }
                        )
                        AppDestination.Llm -> LlmSettingsHubScreen(
                            ::back,
                            { nav.navigate(AppDestination.LlmConnection.route) },
                            { nav.navigate(AppDestination.LlmModel.route) }
                        )
                        AppDestination.LlmConnection -> SettingsScreen(
                            SettingsPage.LLM_CONNECTION,
                            ::back,
                            viewModel = settingsViewModel
                        )
                        AppDestination.LlmModel -> SettingsScreen(
                            SettingsPage.LLM_MODEL,
                            ::back,
                            viewModel = settingsViewModel
                        )
                        AppDestination.Tts -> TtsServiceListScreen(
                            onBack = ::back,
                            onOpenService = { nav.navigate(AppDestinations.ttsConfig(it)) },
                            viewModel = settingsViewModel
                        )
                        AppDestination.TtsConfig -> SettingsScreen(
                            SettingsPage.TTS_CONFIG,
                            ::back,
                            entry.arguments?.getString("profileId"),
                            settingsViewModel
                        )
                        AppDestination.Voices -> VoiceLibraryListScreen(
                            onBack = ::back,
                            onOpenPool = { nav.navigate(AppDestinations.voicePool(it)) },
                            viewModel = settingsViewModel
                        )
                        AppDestination.VoicePool -> SettingsScreen(
                            SettingsPage.VOICES_POOL,
                            ::back,
                            entry.arguments?.getString("profileId"),
                            settingsViewModel
                        )
                        AppDestination.TaskRunLog -> TaskRunLogScreen(
                            viewModel = viewModel,
                            onBack = ::back
                        )
                        AppDestination.About -> SettingsScreen(
                            SettingsPage.ABOUT,
                            ::back,
                            viewModel = settingsViewModel
                        )
                        AppDestination.Import -> ImportPreviewScreen(
                            viewModel,
                            { viewModel.cancelImport(); back() }
                        ) { id ->
                            nav.navigate(AppDestinations.book(id)) {
                                popUpTo(AppDestination.Library.route)
                            }
                        }
                        AppDestination.Book -> {
                            val id = entry.arguments?.getString("bookId").orEmpty()
                            BookHubScreen(
                                id,
                                viewModel,
                                ::back,
                                { nav.navigate(AppDestinations.reader(id, it)) },
                                { nav.navigate(AppDestinations.chapters(id)) },
                                { nav.navigate(AppDestinations.story(id)) },
                                { nav.navigate(AppDestinations.audio(id)) }
                            )
                        }
                        AppDestination.Chapters -> {
                            val id = entry.arguments?.getString("bookId").orEmpty()
                            ChapterListScreen(id, viewModel, ::back) {
                                nav.navigate(AppDestinations.reader(id, it))
                            }
                        }
                        AppDestination.Story -> {
                            val id = entry.arguments?.getString("bookId").orEmpty()
                            StoryHubScreen(
                                ::back,
                                { nav.navigate(AppDestinations.analysis(id)) },
                                { nav.navigate(AppDestinations.graph(id)) },
                                { nav.navigate(AppDestinations.memory(id)) }
                            )
                        }
                        AppDestination.Analysis -> StoryAnalysisScreen(
                            entry.arguments?.getString("bookId").orEmpty(),
                            viewModel,
                            ::back
                        )
                        AppDestination.Graph -> {
                            val id = entry.arguments?.getString("bookId").orEmpty()
                            StoryGraphScreen(id, viewModel, ::back) {
                                nav.navigate(AppDestinations.characterChat(id, it))
                            }
                        }
                        AppDestination.Memory -> MemoryCenterScreen(
                            entry.arguments?.getString("bookId").orEmpty(),
                            viewModel,
                            ::back
                        )
                        AppDestination.Audio -> {
                            val id = entry.arguments?.getString("bookId").orEmpty()
                            AudioHubScreen(
                                ::back,
                                { nav.navigate(AppDestinations.audioEngine(id)) },
                                { nav.navigate(AppDestinations.audioVoices(id)) },
                                { nav.navigate(AppDestinations.audioChapters(id)) }
                            )
                        }
                        AppDestination.AudioEngine -> AudioEngineScreen(
                            entry.arguments?.getString("bookId").orEmpty(),
                            viewModel,
                            ::back
                        )
                        AppDestination.AudioVoices -> {
                            val id = entry.arguments?.getString("bookId").orEmpty()
                            CharacterVoiceBindingScreen(id, viewModel, ::back) {
                                nav.navigate(AppDestinations.characterChat(id, it))
                            }
                        }
                        AppDestination.AudioChapters -> AudioChaptersScreen(
                            entry.arguments?.getString("bookId").orEmpty(),
                            viewModel,
                            ::back
                        )
                        AppDestination.Reader -> {
                            val bookId = entry.arguments?.getString("bookId").orEmpty()
                            val chapterId = entry.arguments?.getString("chapterId").orEmpty()
                            ReaderScreen(bookId, chapterId, viewModel, ::back) { next ->
                                nav.navigate(AppDestinations.reader(bookId, next)) {
                                    popUpTo(AppDestinations.reader(bookId, chapterId)) {
                                        inclusive = true
                                    }
                                }
                            }
                        }
                        AppDestination.CharacterChat -> CharacterChatScreen(
                            entry.arguments?.getString("bookId").orEmpty(),
                            entry.arguments?.getString("characterId").orEmpty(),
                            viewModel,
                            ::back
                        )
                    }
                }
            }
        }
    }
}
