package com.storybrain.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderFunctionParityContractTest {
    private val screens = File("src/main/java/com/storybrain/app/ui/Screens.kt").readText()

    @Test
    fun plainTextIsDefaultAndMoreMenuSwitchesBothReaderModes() {
        val viewModel = File("src/main/java/com/storybrain/app/ui/AppViewModel.kt").readText()
        assertTrue(viewModel.contains("enum class ReaderMode { PLAIN_TEXT, DIALOGUE }"))
        assertTrue(viewModel.contains("MutableStateFlow(ReaderMode.PLAIN_TEXT)"))
        assertTrue(screens.contains("viewModel.readerMode.collectAsStateWithLifecycle()"))
        assertTrue(screens.contains("Text(\"纯文本模式\")"))
        assertTrue(screens.contains("Text(\"对话模式\")"))
        assertTrue(screens.contains("setReaderMode(ReaderMode.PLAIN_TEXT)"))
        assertTrue(screens.contains("setReaderMode(ReaderMode.DIALOGUE)"))
    }

    @Test
    fun dialogueModeUsesParserAliasesAndRealDialogueBubble() {
        assertTrue(screens.contains("ReaderSpeakerPolicy.buildKnownSpeakers"))
        assertTrue(screens.contains("TextToChatParser.parse(currentChapter.content, knownSpeakers)"))
        assertTrue(screens.contains("is ReadingBlock.Dialogue -> DialogueBubble("))
        assertTrue(screens.contains("characterId = chapterMentionCharacters.firstOrNull"))
    }

    @Test
    fun readerUsesPagerInsteadOfRawPointerDragAndRetainsButtonFallbacks() {
        assertTrue(screens.contains("HorizontalPager("))
        assertTrue(screens.contains("userScrollEnabled = chapters.size > 1"))
        assertFalse(screens.contains("detectHorizontalDragGestures"))
        assertFalse(screens.contains(".pointerInput("))
        assertTrue(screens.contains("Text(ReactReferenceContract.readerBottomActions[0])"))
        assertTrue(screens.contains("Text(ReactReferenceContract.readerBottomActions[2])"))
    }

    @Test
    fun everyChapterChangeStopsAudioMarksProgressAndHonorsBoundaries() {
        assertTrue(screens.contains("fun openReaderChapter(targetIndex: Int)"))
        assertTrue(screens.contains("val targetChapter = chapters.getOrNull(targetIndex) ?: return"))
        assertTrue(screens.contains("viewModel.stopChapterTts()"))
        assertTrue(screens.contains("viewModel.markReading(bookId, targetChapter.chapterIndex)"))
        assertTrue(screens.contains("if (requestedIndex == null)"))
        assertTrue(screens.contains("initialPage = requestedIndex"))
        assertTrue(screens.contains("snapshotFlow { pagerState.settledPage }"))
        assertTrue(screens.contains("enabled = currentIndex > 0"))
        assertTrue(screens.contains("enabled = currentIndex >= 0 && currentIndex < chapters.lastIndex"))
    }

    @Test
    fun readerContentLeavesSpaceBelowLastParagraphForFixedBottomBar() {
        assertEquals(96, ReactReferenceContract.readerBottomContentPaddingDp)
        assertTrue(screens.contains("bottomNavigationHeightDp"))
    }
    @Test
    fun ttsAndBothMemoryFlowsRemainReachableWithFeedback() {
        assertTrue(screens.contains("viewModel.generateChapterTts(bookId, current.id)"))
        assertTrue(screens.contains("viewModel.playChapterTts(current.id, current.ttsManifestPath.orEmpty())"))
        assertTrue(screens.contains("viewModel.stopChapterTts()"))
        assertTrue(screens.contains("content = current.content"))
        assertTrue(screens.contains("content = paragraph"))
        assertTrue(screens.contains("content = block.text"))
        assertTrue(screens.contains("memoryAction.message"))
        assertTrue(screens.contains("MemoryEditorDialog("))
    }
}
