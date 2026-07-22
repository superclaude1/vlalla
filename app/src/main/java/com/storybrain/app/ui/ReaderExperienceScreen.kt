package com.storybrain.app.ui

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storybrain.app.data.ChapterEntity
import com.storybrain.app.data.ChapterListItem
import com.storybrain.app.data.MemoryType
import com.storybrain.app.data.ReaderTheme
import com.storybrain.app.data.ReadingMarkEntity
import com.storybrain.app.data.ReadingMarkType
import com.storybrain.app.data.ReadingMode
import com.storybrain.app.data.TaskStatus
import com.storybrain.app.reader.ReadingBlock
import com.storybrain.app.reader.ResolvedReadingPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.json.JSONArray

private enum class ReaderDrawerTab { CONTENTS, BOOKMARKS, NOTES }
private const val ACTION_MARK_SELECTION = 0x5A01

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderExperienceScreen(
    viewModel: ReaderViewModel,
    appViewModel: AppViewModel,
    playbackViewModel: PlaybackViewModel,
    onBack: () -> Unit,
    onOpenChapter: (chapterId: String, sourceOffset: Int) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playback by playbackViewModel.uiState.collectAsStateWithLifecycle()
    val preferences = state.preferences
    val mode = preferences?.mode ?: ReadingMode.CHAT
    val blocks = state.document?.blocks(mode).orEmpty()
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var chromeVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var selectedBlock by remember { mutableStateOf<ReadingBlock?>(null) }
    var editingMark by remember { mutableStateOf<ReadingMarkEntity?>(null) }
    var pendingAnchor by remember { mutableIntStateOf(-1) }
    var restored by remember(state.chapter?.id) { mutableStateOf(false) }
    val chapter = state.chapter
    val currentIndex = state.chapters.indexOfFirst { it.id == chapter?.id }
    val activeDisplayIndex = remember(playback.chapterId, playback.blockIndex, state.document, mode) {
        if (playback.chapterId != chapter?.id || playback.blockIndex < 0) -1
        else state.document?.chatBlocks?.getOrNull(playback.blockIndex)?.sourceStart?.let { sourceOffset ->
            state.document?.blockIndexAt(mode, sourceOffset)
        } ?: -1
    }

    LaunchedEffect(state.document, mode, state.position, viewModel.requestedOffset) {
        val document = state.document ?: return@LaunchedEffect
        if (!restored || pendingAnchor >= 0) {
            val requested = pendingAnchor.takeIf { it >= 0 }
                ?: viewModel.requestedOffset.takeIf { it >= 0 }
                ?: state.position?.takeIf { it.chapterId == chapter?.id }?.sourceOffset
                ?: 0
            listState.scrollToItem(document.blockIndexAt(mode, requested))
            pendingAnchor = -1
            restored = true
        }
    }

    LaunchedEffect(blocks, listState) {
        androidx.compose.runtime.snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.distinctUntilChanged().collectLatest { (index, offsetPx) ->
            delay(500)
            blocks.getOrNull(index)?.let { viewModel.savePosition(it.sourceStart, offsetPx) }
        }
    }

    DisposableEffect(chapter?.id, blocks) {
        onDispose {
            blocks.getOrNull(listState.firstVisibleItemIndex)?.let {
                viewModel.savePosition(it.sourceStart, listState.firstVisibleItemScrollOffset)
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(activeDisplayIndex, preferences?.autoFollowAudio) {
        if (activeDisplayIndex >= 0 && preferences?.autoFollowAudio == true &&
            kotlin.math.abs(listState.firstVisibleItemIndex - activeDisplayIndex) > 1
        ) {
            listState.animateScrollToItem(activeDisplayIndex)
        }
    }

    LaunchedEffect(playback.missingNextChapterAudio, playback.error) {
        val error = playback.error ?: return@LaunchedEffect
        val action = snackbar.showSnackbar(
            message = error,
            actionLabel = if (playback.missingNextChapterAudio) "生成下一章" else null
        )
        if (action == androidx.compose.material3.SnackbarResult.ActionPerformed && playback.missingNextChapterAudio) {
            state.chapters.getOrNull(playback.chapterIndex + 1)?.let {
                appViewModel.generateChapterTts(viewModel.bookId, it.id)
            }
        }
        playbackViewModel.clearError()
    }

    selectedBlock?.let { block ->
        ReadingMarkDialog(
            block = block,
            onDismiss = { selectedBlock = null },
            onSave = { type, note, colorKey ->
                viewModel.addMark(type, block.sourceStart, block.sourceEnd, block.text, note, colorKey)
                selectedBlock = null
            },
            onSaveMemory = {
                val speakerId = (block as? ReadingBlock.Dialogue)?.speaker?.let { name ->
                    state.characters.firstOrNull { it.canonicalName == name }?.id
                }
                appViewModel.saveNewMemory(
                    bookId = viewModel.bookId,
                    type = MemoryType.EXCERPT,
                    title = chapter?.title ?: "阅读摘录",
                    content = block.text,
                    chapterIndex = chapter?.chapterIndex,
                    characterIds = listOfNotNull(speakerId)
                ) { selectedBlock = null }
            }
        )
    }

    editingMark?.let { mark ->
        EditReadingMarkDialog(
            mark = mark,
            onDismiss = { editingMark = null },
            onSave = { note, colorKey ->
                viewModel.updateMark(
                    mark.copy(note = note, colorKey = colorKey, updatedAt = System.currentTimeMillis())
                )
                editingMark = null
            }
        )
    }

    if (showSearch) {
        ReaderSearchDialog(
            query = state.searchQuery,
            loading = state.searching,
            results = state.searchResults,
            indexing = state.searchIndexing,
            indexCompleted = state.searchIndexCompleted,
            indexTotal = state.searchIndexTotal,
            indexStage = state.searchIndexStage,
            onQuery = viewModel::search,
            onDismiss = { showSearch = false },
            onOpen = { hit ->
                showSearch = false
                onOpenChapter(hit.chapterId, hit.sourceOffset)
            }
        )
    }

    if (showSettings && preferences != null) {
        ReaderSettingsSheet(
            initial = preferences,
            onDismiss = { showSettings = false },
            onSave = {
                viewModel.saveStyle(it)
                showSettings = false
            },
            onReset = {
                viewModel.resetStyle()
                showSettings = false
            }
        )
    }

    val palette = readerPalette(preferences?.theme ?: ReaderTheme.PAPER)
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ReaderDrawer(
                chapters = state.chapters,
                currentChapterId = chapter?.id,
                marks = state.marks,
                onOpenChapter = { id, offset ->
                    scope.launch { drawerState.close() }
                    onOpenChapter(id, offset)
                },
                onDeleteMark = viewModel::deleteMark,
                onEditMark = { editingMark = it }
            )
        }
    ) {
        Scaffold(
            containerColor = palette.background,
            contentColor = palette.text,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (chromeVisible) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = palette.surface,
                            titleContentColor = palette.text,
                            navigationIconContentColor = palette.text,
                            actionIconContentColor = palette.text
                        ),
                        title = {
                            Column {
                                Text(chapter?.title ?: "正在读取", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (mode == ReadingMode.CHAT) "聊天阅读" else "原文阅读",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                        },
                        actions = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.AutoMirrored.Rounded.MenuBook, "目录、书签与批注")
                            }
                            IconButton(onClick = { showSearch = true }) { Icon(Icons.Rounded.Search, "书内搜索") }
                            IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, "阅读设置") }
                        }
                    )
                }
            },
            bottomBar = {
                if (chromeVisible) {
                    ReaderBottomBar(
                        chapter = chapter,
                        currentIndex = currentIndex,
                        chapterCount = state.chapters.size,
                        mode = mode,
                        palette = palette,
                        hasAudio = !chapter?.ttsManifestPath.isNullOrBlank(),
                        playing = playback.isPlaying && playback.chapterId == chapter?.id,
                        onMode = { newMode ->
                            val anchor = blocks.getOrNull(listState.firstVisibleItemIndex)?.sourceStart ?: 0
                            pendingAnchor = anchor
                            viewModel.savePosition(anchor, listState.firstVisibleItemScrollOffset)
                            viewModel.setMode(newMode)
                        },
                        onPrevious = {
                            state.chapters.getOrNull(currentIndex - 1)?.let { onOpenChapter(it.id, 0) }
                        },
                        onNext = {
                            state.chapters.getOrNull(currentIndex + 1)?.let { onOpenChapter(it.id, 0) }
                        },
                        onAudio = {
                            val current = chapter ?: return@ReaderBottomBar
                            if (current.ttsManifestPath.isNullOrBlank()) appViewModel.generateChapterTts(viewModel.bookId, current.id)
                            else if (playback.chapterId == current.id && playback.isPlaying) playbackViewModel.pause()
                            else if (playback.chapterId == current.id) playbackViewModel.play()
                            else playbackViewModel.playChapter(viewModel.bookId, current.id)
                        }
                    )
                }
            }
        ) { padding ->
            ReaderContent(
                modifier = Modifier.padding(padding),
                listState = listState,
                blocks = blocks,
                preferences = preferences,
                palette = palette,
                activeIndex = activeDisplayIndex,
                marks = state.marks.filter { it.chapterId == chapter?.id },
                onToggleChrome = { chromeVisible = !chromeVisible },
                onPlayBlock = { block ->
                    val chatIndex = state.document?.blockIndexAt(ReadingMode.CHAT, block.sourceStart) ?: return@ReaderContent
                    if (playback.chapterId == chapter?.id) playbackViewModel.seekToBlock(chatIndex)
                    else if (!chapter?.ttsManifestPath.isNullOrBlank()) playbackViewModel.playChapter(viewModel.bookId, chapter!!.id, chatIndex)
                    else chromeVisible = !chromeVisible
                },
                onMark = { selectedBlock = it }
            )
        }
    }
}

private data class ReaderPalette(val background: Color, val surface: Color, val text: Color, val accent: Color)

private fun readerPalette(theme: ReaderTheme) = when (theme) {
    ReaderTheme.PAPER -> ReaderPalette(Color(0xFFF8F4EA), Color(0xFFFFFCF4), Color(0xFF2C2923), Color(0xFF745B2E))
    ReaderTheme.SEPIA -> ReaderPalette(Color(0xFFE8D5B2), Color(0xFFF1E0C2), Color(0xFF3F3021), Color(0xFF815B2A))
    ReaderTheme.NIGHT -> ReaderPalette(Color(0xFF171A1C), Color(0xFF222629), Color(0xFFD7D5CE), Color(0xFFB9A274))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderContent(
    modifier: Modifier,
    listState: LazyListState,
    blocks: List<ReadingBlock>,
    preferences: ResolvedReadingPreferences?,
    palette: ReaderPalette,
    activeIndex: Int,
    marks: List<ReadingMarkEntity>,
    onToggleChrome: () -> Unit,
    onPlayBlock: (ReadingBlock) -> Unit,
    onMark: (ReadingBlock) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .clickable(onClick = onToggleChrome),
        state = listState,
        contentPadding = PaddingValues(
            horizontal = (preferences?.horizontalPaddingDp ?: 20).dp,
            vertical = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy((preferences?.paragraphSpacingDp ?: 8f).dp)
    ) {
        itemsIndexed(blocks, key = { _, item -> "${item.sourceStart}:${item.sourceEnd}:${item::class.simpleName}" }) { index, block ->
            val blockMarks = marks.filter { mark ->
                ReadingMarkType.fromStorage(mark.type) != ReadingMarkType.BOOKMARK &&
                    mark.startOffset < block.sourceEnd && mark.endOffset > block.sourceStart
            }
            when (block) {
                is ReadingBlock.Dialogue -> DialogueReadingBlock(
                    block,
                    preferences,
                    palette,
                    index == activeIndex,
                    blockMarks.firstOrNull()?.colorKey,
                    { onPlayBlock(block) },
                    { onMark(block) }
                )
                is ReadingBlock.Narration -> OriginalReadingBlock(
                    block,
                    preferences,
                    palette,
                    index == activeIndex,
                    blockMarks,
                    { onPlayBlock(block) },
                    { selectionStart, selectionEnd ->
                        val start = selectionStart.coerceIn(0, block.text.length)
                        val end = selectionEnd.coerceIn(start, block.text.length)
                        if (end > start) {
                            onMark(
                                ReadingBlock.Narration(
                                    text = block.text.substring(start, end),
                                    sourceStart = block.sourceStart + start,
                                    sourceEnd = block.sourceStart + end
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialogueReadingBlock(
    block: ReadingBlock.Dialogue,
    preferences: ResolvedReadingPreferences?,
    palette: ReaderPalette,
    active: Boolean,
    markColorKey: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = MaterialTheme.shapes.large, color = palette.accent.copy(alpha = .18f)) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Text(block.speaker.take(1), color = palette.accent, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.widthIn(max = 520.dp)) {
            Text(block.speaker, style = MaterialTheme.typography.labelMedium, color = palette.accent)
            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
                shape = MaterialTheme.shapes.large,
                color = when {
                    active -> palette.accent.copy(alpha = .2f)
                    markColorKey != null -> markColor(markColorKey).copy(alpha = .16f)
                    else -> palette.surface
                }
            ) {
                ReaderText(block.text, preferences, palette.text, Modifier.padding(14.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OriginalReadingBlock(
    block: ReadingBlock.Narration,
    preferences: ResolvedReadingPreferences?,
    palette: ReaderPalette,
    active: Boolean,
    marks: List<ReadingMarkEntity>,
    onClick: () -> Unit,
    onSelection: (Int, Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (active) palette.accent.copy(alpha = .14f) else Color.Transparent
    ) {
        val textSize = preferences?.fontSizeSp ?: 18f
        val lineHeight = preferences?.lineHeightMultiplier ?: 1.6f
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                TextView(context).apply {
                    setTextIsSelectable(true)
                    includeFontPadding = false
                }
            },
            update = { textView ->
                textView.text = SpannableString(block.text).apply {
                    marks.forEach { mark ->
                        val start = (mark.startOffset - block.sourceStart).coerceIn(0, block.text.length)
                        val end = (mark.endOffset - block.sourceStart).coerceIn(start, block.text.length)
                        if (end > start) {
                            setSpan(
                                BackgroundColorSpan(markColor(mark.colorKey).copy(alpha = .28f).toArgb()),
                                start,
                                end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }
                }
                textView.setTextColor(palette.text.toArgb())
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
                textView.setLineSpacing(0f, lineHeight)
                textView.typeface = if (preferences?.serifFont == true) Typeface.SERIF else Typeface.DEFAULT
                val verticalPadding = (4 * textView.resources.displayMetrics.density).toInt()
                textView.setPadding(0, verticalPadding, 0, verticalPadding)
                textView.setOnClickListener { onClick() }
                textView.customSelectionActionModeCallback = selectionActionCallback(textView, onSelection)
            }
        )
    }
}

private fun selectionActionCallback(
    textView: TextView,
    onSelection: (Int, Int) -> Unit
) = object : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.add(Menu.NONE, ACTION_MARK_SELECTION, Menu.NONE, "标记/批注")
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId != ACTION_MARK_SELECTION) return false
        val start = minOf(textView.selectionStart, textView.selectionEnd)
        val end = maxOf(textView.selectionStart, textView.selectionEnd)
        if (start >= 0 && end > start) onSelection(start, end)
        mode.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) = Unit
}

@Composable
private fun ReaderText(text: String, preferences: ResolvedReadingPreferences?, color: Color, modifier: Modifier) {
    val size = preferences?.fontSizeSp ?: 18f
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        lineHeight = (size * (preferences?.lineHeightMultiplier ?: 1.6f)).sp,
        fontFamily = if (preferences?.serifFont == true) FontFamily.Serif else FontFamily.Default
    )
}

@Composable
private fun ReaderBottomBar(
    chapter: ChapterEntity?,
    currentIndex: Int,
    chapterCount: Int,
    mode: ReadingMode,
    palette: ReaderPalette,
    hasAudio: Boolean,
    playing: Boolean,
    onMode: (ReadingMode) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAudio: () -> Unit
) {
    Surface(color = palette.surface, contentColor = palette.text, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                val chipColors = FilterChipDefaults.filterChipColors(
                    containerColor = palette.surface,
                    labelColor = palette.text,
                    selectedContainerColor = palette.accent.copy(alpha = .22f),
                    selectedLabelColor = palette.text
                )
                FilterChip(
                    selected = mode == ReadingMode.CHAT,
                    onClick = { onMode(ReadingMode.CHAT) },
                    label = { Text("聊天") },
                    colors = chipColors
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = mode == ReadingMode.ORIGINAL,
                    onClick = { onMode(ReadingMode.ORIGINAL) },
                    label = { Text("原文") },
                    colors = chipColors
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(enabled = currentIndex > 0, onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "上一章")
                }
                Text(
                    chapter?.let { "${it.chapterIndex + 1}/$chapterCount · ${it.title}" } ?: "正在读取",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onAudio) {
                    Icon(
                        when {
                            playing -> Icons.Rounded.Pause
                            hasAudio -> Icons.Rounded.PlayArrow
                            else -> Icons.Rounded.GraphicEq
                        },
                        if (hasAudio) "播放配音" else "生成配音"
                    )
                }
                IconButton(enabled = currentIndex in 0 until chapterCount - 1, onClick = onNext) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, "下一章")
                }
            }
        }
    }
}

@Composable
private fun ReaderDrawer(
    chapters: List<ChapterListItem>,
    currentChapterId: String?,
    marks: List<ReadingMarkEntity>,
    onOpenChapter: (String, Int) -> Unit,
    onDeleteMark: (String) -> Unit,
    onEditMark: (ReadingMarkEntity) -> Unit
) {
    var tab by remember { mutableStateOf(ReaderDrawerTab.CONTENTS) }
    ModalDrawerSheet(Modifier.widthIn(max = 360.dp)) {
        Text("阅读导航", Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        TabRow(tab.ordinal) {
            ReaderDrawerTab.entries.forEach { item ->
                Tab(
                    selected = tab == item,
                    onClick = { tab = item },
                    text = { Text(when (item) {
                        ReaderDrawerTab.CONTENTS -> "目录"
                        ReaderDrawerTab.BOOKMARKS -> "书签"
                        ReaderDrawerTab.NOTES -> "批注"
                    }) }
                )
            }
        }
        when (tab) {
            ReaderDrawerTab.CONTENTS -> LazyColumn(Modifier.fillMaxSize()) {
                items(chapters, key = { it.id }) { chapter ->
                    Row(
                        Modifier.fillMaxWidth().selectable(chapter.id == currentChapterId) { onOpenChapter(chapter.id, 0) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${chapter.chapterIndex + 1}", Modifier.width(36.dp), color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(chapter.title, fontWeight = if (chapter.id == currentChapterId) FontWeight.Bold else FontWeight.Normal)
                            Text(
                                if (TaskStatus.fromStorage(chapter.ttsStatus) == TaskStatus.COMPLETED) "已有配音" else "${chapter.charCount} 字",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }
            ReaderDrawerTab.BOOKMARKS -> MarkList(
                marks.filter { ReadingMarkType.fromStorage(it.type) == ReadingMarkType.BOOKMARK },
                chapters,
                onOpenChapter,
                onDeleteMark,
                onEditMark
            )
            ReaderDrawerTab.NOTES -> MarkList(
                marks.filter { ReadingMarkType.fromStorage(it.type) != ReadingMarkType.BOOKMARK },
                chapters,
                onOpenChapter,
                onDeleteMark,
                onEditMark
            )
        }
    }
}

@Composable
private fun MarkList(
    marks: List<ReadingMarkEntity>,
    chapters: List<ChapterListItem>,
    onOpen: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (ReadingMarkEntity) -> Unit
) {
    if (marks.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            Text("还没有内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(marks, key = { it.id }) { mark ->
            Card(onClick = { onOpen(mark.chapterId, mark.startOffset) }) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(chapters.firstOrNull { it.id == mark.chapterId }?.title ?: "章节", style = MaterialTheme.typography.labelMedium)
                    Text(mark.excerpt, maxLines = 3, overflow = TextOverflow.Ellipsis, color = markColor(mark.colorKey))
                    if (mark.note.isNotBlank()) Text(mark.note, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.align(Alignment.End)) {
                        if (ReadingMarkType.fromStorage(mark.type) != ReadingMarkType.BOOKMARK) {
                            TextButton(onClick = { onEdit(mark) }) { Text("编辑") }
                        }
                        TextButton(onClick = { onDelete(mark.id) }) { Text("删除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingMarkDialog(
    block: ReadingBlock,
    onDismiss: () -> Unit,
    onSave: (ReadingMarkType, String, String) -> Unit,
    onSaveMemory: () -> Unit
) {
    var note by remember { mutableStateOf("") }
    var colorKey by remember { mutableStateOf("amber") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.FormatQuote, null) },
        title = { Text("标记这段文字") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(block.text, maxLines = 5, overflow = TextOverflow.Ellipsis)
                TextField(note, { note = it }, label = { Text("批注（可选）") }, modifier = Modifier.fillMaxWidth())
                MarkColorPicker(colorKey) { colorKey = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onSave(ReadingMarkType.BOOKMARK, "", colorKey) }) { Text("书签") }
                    TextButton(onClick = { onSave(ReadingMarkType.HIGHLIGHT, "", colorKey) }) { Text("高亮") }
                    TextButton(onClick = onSaveMemory) { Text("存入记忆") }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(ReadingMarkType.NOTE, note, colorKey) }, enabled = note.isNotBlank()) { Text("保存批注") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun EditReadingMarkDialog(
    mark: ReadingMarkEntity,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var note by remember(mark.id) { mutableStateOf(mark.note) }
    var colorKey by remember(mark.id) { mutableStateOf(mark.colorKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑批注") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(mark.excerpt, maxLines = 5, overflow = TextOverflow.Ellipsis)
                TextField(note, { note = it }, label = { Text("批注") }, modifier = Modifier.fillMaxWidth())
                MarkColorPicker(colorKey) { colorKey = it }
            }
        },
        confirmButton = { Button(onClick = { onSave(note, colorKey) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun MarkColorPicker(selected: String, onSelected: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("amber" to "琥珀", "green" to "青绿", "blue" to "靛蓝").forEach { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelected(key) },
                label = { Text(label, color = markColor(key)) }
            )
        }
    }
}

private fun markColor(key: String) = when (key) {
    "green" -> Color(0xFF2F6B52)
    "blue" -> Color(0xFF3E5E8C)
    else -> Color(0xFF9A6A15)
}

@Composable
private fun ReaderSearchDialog(
    query: String,
    loading: Boolean,
    results: List<com.storybrain.app.data.ChapterSearchHit>,
    indexing: Boolean,
    indexCompleted: Int,
    indexTotal: Int,
    indexStage: String,
    onQuery: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpen: (com.storybrain.app.data.ChapterSearchHit) -> Unit
) {
    var input by remember(query) { mutableStateOf(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("书内搜索") },
        text = {
            Column(Modifier.height(430.dp)) {
                TextField(
                    input,
                    { input = it },
                    label = { Text("输入关键词") },
                    trailingIcon = { IconButton(onClick = { onQuery(input) }) { Icon(Icons.Rounded.Search, "搜索") } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (indexing) {
                    if (indexTotal > 0) {
                        LinearProgressIndicator(
                            progress = { indexCompleted.toFloat().div(indexTotal).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Text(
                        indexStage.ifBlank { "正在建立旧书全文索引" },
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(results) { hit ->
                        Card(onClick = { onOpen(hit) }) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(hit.chapterTitle, fontWeight = FontWeight.Bold)
                                Text(hit.excerpt, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onQuery(input) }) { Text("搜索") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    initial: ResolvedReadingPreferences,
    onDismiss: () -> Unit,
    onSave: (ResolvedReadingPreferences) -> Unit,
    onReset: () -> Unit
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("阅读样式", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = draft.theme == theme,
                        onClick = { draft = draft.copy(theme = theme) },
                        label = { Text(when (theme) {
                            ReaderTheme.PAPER -> "纸白"
                            ReaderTheme.SEPIA -> "羊皮纸"
                            ReaderTheme.NIGHT -> "夜间"
                        }) }
                    )
                }
            }
            Text("字号 ${draft.fontSizeSp.toInt()}")
            Slider(draft.fontSizeSp, { draft = draft.copy(fontSizeSp = it) }, valueRange = 14f..30f, steps = 15)
            Text("行距 ${"%.1f".format(draft.lineHeightMultiplier)}")
            Slider(draft.lineHeightMultiplier, { draft = draft.copy(lineHeightMultiplier = it) }, valueRange = 1.2f..2.2f, steps = 9)
            Text("段距 ${draft.paragraphSpacingDp.toInt()} dp")
            Slider(draft.paragraphSpacingDp, { draft = draft.copy(paragraphSpacingDp = it) }, valueRange = 0f..24f, steps = 11)
            Text("页边距 ${draft.horizontalPaddingDp} dp")
            Slider(draft.horizontalPaddingDp.toFloat(), { draft = draft.copy(horizontalPaddingDp = it.toInt()) }, valueRange = 12f..40f, steps = 13)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !draft.serifFont, onClick = { draft = draft.copy(serifFont = false) }, label = { Text("系统字体") })
                FilterChip(selected = draft.serifFont, onClick = { draft = draft.copy(serifFont = true) }, label = { Text("衬线字体") })
            }
            FilterChip(
                selected = draft.autoFollowAudio,
                onClick = { draft = draft.copy(autoFollowAudio = !draft.autoFollowAudio) },
                label = { Text("听书自动跟随段落") }
            )
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onReset) { Text("恢复全局设置") }
                Button(onClick = { onSave(draft.copy(usesGlobalStyle = false)) }) { Text("应用到本书") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
