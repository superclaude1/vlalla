package com.storybrain.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryImportActionContractTest {
    private val screens = File("src/main/java/com/storybrain/app/ui/Screens.kt").readText()
    private val app = File("src/main/java/com/storybrain/app/ui/StoryBrainApp.kt").readText()

    @Test
    fun shelfExposesBothRealSortOrdersWithoutReplacingSearchOrTxtImport() {
        assertTrue(screens.contains("LibrarySortOrder.IMPORTED_TIME"))
        assertTrue(screens.contains("LibrarySortOrder.TITLE"))
        assertTrue(screens.contains("sortLibraryBooks(books, sortOrder)"))
        assertTrue(screens.contains("onClick = onOpenSearch"))
        assertTrue(screens.contains("launcher.launch(TXT_MIME_TYPES)"))
        assertTrue(screens.contains("viewModel.loadNovel(uri)"))
        assertTrue(screens.contains("onImportStarted()"))
    }

    @Test
    fun importPreviewConnectsTitlePreviewCancelReselectAndConfirmToRealActions() {
        assertTrue(screens.contains("onValueChange = viewModel::updateImportTitle"))
        assertTrue(screens.contains("val novel = state.novel!!"))
        assertTrue(screens.contains("itemsIndexed(novel.chapters)"))
        assertTrue(screens.contains("reselectLauncher.launch(TXT_MIME_TYPES)"))
        assertTrue(screens.contains("viewModel.loadNovel(uri)"))
        assertTrue(screens.contains("viewModel.confirmImport(onImported)"))
        assertTrue(screens.contains("enabled = !state.loading"))
        assertTrue(app.contains("{ viewModel.cancelImport(); back() }"))
    }
}
