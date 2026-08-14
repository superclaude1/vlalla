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
        assertTrue(viewModel.contains("ReaderPreferencesStore(application)"))
        assertTrue(viewModel.contains("MutableStateFlow(readerPreferences.value.displayMode.toReaderMode())"))
        assertTrue(screens.contains("viewModel.readerMode.collectAsStateWithLifecycle()"))
        assertTrue(screens.contains("Text(\"纯文本模式\")"))
        assertTrue(screens.contains("Text(\"对话模式\")"))
        assertTrue(screens.contains("setReaderMode(ReaderMode.PLAIN_TEXT)"))
        assertTrue(screens.contains("setReaderMode(ReaderMode.DIALOGUE)"))
    }

    @Test
    fun readerAppearanceControlsArePersistedAndAppliedToEveryReadingMode() {
        val viewModel = File("src/main/java/com/storybrain/app/ui/AppViewModel.kt").readText()
        assertTrue(viewModel.contains("val readerPreferences = readerPreferencesStore.preferences"))
        assertTrue(viewModel.contains("fun adjustReaderFontSize(delta: Int)"))
        assertTrue(screens.contains("Text(\"字号\")"))
        assertTrue(screens.contains("viewModel.adjustReaderFontSize(-1)"))
        assertTrue(screens.contains("viewModel.adjustReaderFontSize(1)"))
        assertTrue(screens.contains("fontSize = readerPreferences.fontSizeSp.sp"))
        assertTrue(screens.contains("lineHeight = readerPreferences.lineHeightSp.sp"))
        assertTrue(screens.contains("horizontal = readerPreferences.horizontalPaddingDp.dp"))
    }

    @Test
    fun readerRestoresAndPersistsSourceOffsetPosition() {
        val viewModel = File("src/main/java/com/storybrain/app/ui/AppViewModel.kt").readText()
        assertTrue(viewModel.contains("fun saveReadingOffset("))
        assertTrue(viewModel.contains("fun observeReadingPosition(bookId: String)"))
        assertTrue(screens.contains("viewModel.observeReadingPosition(bookId)"))
        assertTrue(screens.contains("key(currentChapter?.id, readerPreferences)"))
        assertTrue(screens.contains("pages.indexOfFirst { storedOffset < it.endOffset }"))
        assertTrue(screens.contains("viewModel.saveReadingOffset("))
        assertTrue(screens.contains(".drop(1).collect"))
    }

    @Test
    fun readerShowsBoundedProgressAndRemainingChapters() {
        assertTrue(screens.contains("ReaderProgressPolicy.summarize(currentIndex, chapters.size)"))
        assertTrue(screens.contains("progressSummary.percent"))
        assertTrue(screens.contains("progressSummary.remainingChapters"))
    }

    @Test
    fun dialogueModeUsesParserAliasesAndRealDialogueBubble() {
        assertTrue(screens.contains("ReaderSpeakerPolicy.buildKnownSpeakers"))
        assertTrue(screens.contains("ReaderDocument.create(it, knownSpeakers)"))
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
    fun readerContentKeepsBottomSpaceForFixedBottomBar() {
        assertEquals(96, ReactReferenceContract.readerBottomContentPaddingDp)
        assertTrue(screens.contains("Modifier.fillMaxSize().padding(padding)"))
    }
    @Test
    fun ttsAndBothMemoryFlowsRemainReachableWithFeedback() {
        assertTrue(screens.contains("viewModel.generateChapterTts(bookId, current.id)"))
        assertTrue(screens.contains("viewModel.playChapterTts(current.id, current.ttsManifestPath.orEmpty())"))
        assertTrue(screens.contains("viewModel.stopChapterTts()"))
        assertTrue(screens.contains("content = current.content"))
        assertTrue(screens.contains("content = segmentText"))
        assertTrue(screens.contains("content = block.text"))
        assertTrue(screens.contains("memoryAction.message"))
        assertTrue(screens.contains("MemoryEditorDialog("))
    }
}
