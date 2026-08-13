package com.storybrain.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactReferenceContractTest {
    private val uiSource = File("src/main/java/com/storybrain/app/ui")

    @Test
    fun shellMatchesReference() {
        assertEquals(52, ReactReferenceContract.topBarHeightDp)
        assertEquals(listOf("书架", "我的"), ReactReferenceContract.bottomTabs)
        assertEquals(52 to 72, ReactReferenceContract.shelfCoverDp)
        assertEquals("search", ReactReferenceContract.searchRoute)
        assertEquals(listOf("暂无小说", "导入 TXT"), ReactReferenceContract.emptyLibraryContent)
    }

    @Test
    fun searchRemainsARealDestinationAlongsideShelfSorting() {
        val app = uiSource.resolve("StoryBrainApp.kt").readText()
        val screens = uiSource.resolve("Screens.kt").readText()
        assertTrue(app.contains("AppDestination.Search ->"))
        assertTrue(app.contains("SearchScreen("))
        assertTrue(screens.contains("fun SearchScreen("))
        assertTrue(screens.contains("LibrarySortOrder.IMPORTED_TIME"))
        assertTrue(screens.contains("LibrarySortOrder.TITLE"))
    }

    @Test
    fun bookAndReaderMatchReference() {
        assertEquals(64 to 88, ReactReferenceContract.bookHubCoverDp)
        assertEquals(listOf("目录", "故事", "配音"), ReactReferenceContract.bookPortals)
        assertEquals(listOf("上一章", "当前进度", "下一章"), ReactReferenceContract.readerBottomActions)
        assertEquals(listOf("本章配音", "保存本章"), ReactReferenceContract.readerSheetActions)
    }

    @Test
    fun readerUsesRealSheetActionsAndContinuousProse() {
        val screens = uiSource.resolve("Screens.kt").readText()
        assertTrue(screens.contains("ModalBottomSheet("))
        assertTrue(screens.contains("viewModel.generateChapterTts(bookId, current.id)"))
        assertTrue(screens.contains("viewModel.saveNewMemory("))
        assertTrue(screens.contains("fontFamily = ReactReferenceContract.readerFontFamily"))
        assertTrue(screens.contains("Text(\"纯文本模式\")"))
        assertTrue(screens.contains("Text(\"对话模式\")"))
        assertTrue(screens.contains("DialogueBubble("))
        assertTrue(ReactReferenceContract.readerUsesSerifProse)
        assertTrue(ReactReferenceContract.bookHubTopBarTitleIsEmpty)
        assertEquals(6, ReactReferenceContract.primaryButtonRadiusDp)
        assertEquals(4, ReactReferenceContract.portalRadiusDp)
    }

    @Test
    fun systemChromeStaysDark() {
        assertTrue(ReactReferenceContract.darkSystemBars)
    }

    @Test
    fun bookHomeIsDirectoryFirst() {
        assertEquals(14, ReactReferenceContract.bookPreviewChapterCount)
        assertFalse(ReactReferenceContract.bookHomeShowsCover)
        assertTrue(ReactReferenceContract.bookActionsAreFixedToBottom)
    }
}
