@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.storybrain.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storybrain.app.data.BookEntity
import com.storybrain.app.R
import com.storybrain.app.data.ChapterEntity
import com.storybrain.app.data.ChatMessageEntity
import com.storybrain.app.data.ChatSessionEntity
import com.storybrain.app.data.MemoryType
import com.storybrain.app.data.TaskStatus
import com.storybrain.app.data.PlotNodeEntity
import com.storybrain.app.data.StoryCharacterEntity
import com.storybrain.app.data.StoryRelationEntity
import com.storybrain.app.data.TtsProfileIds
import com.storybrain.app.data.TtsProfileVoicePoolEntity
import com.storybrain.app.reader.ReadingBlock
import com.storybrain.app.reader.TextToChatParser
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LibraryScreen(
    viewModel: AppViewModel,
    onImportStarted: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val books by viewModel.books.collectAsStateWithLifecycle(initialValue = emptyList())
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var sortByTitle by rememberSaveable { mutableStateOf(false) }
    val visibleBooks = remember(books, libraryQuery, sortByTitle) {
        books.filter { it.title.contains(libraryQuery, true) }
            .let { list -> if (sortByTitle) list.sortedBy { it.title.lowercase() } else list }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            viewModel.loadNovel(uri)
            onImportStarted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("章境", fontWeight = FontWeight.Bold)
                        Text("让每一本小说成为可探索的世界", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { sortByTitle = !sortByTitle }) {
                        Icon(Icons.Rounded.SortByAlpha, if (sortByTitle) "按导入时间排序" else "按书名排序")
                    }
                    IconButton(onClick = { launcher.launch(arrayOf("text/plain", "application/octet-stream")) }) {
                        Icon(Icons.Rounded.Add, contentDescription = "导入小说")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Rounded.AutoStories, null) },
                    label = { Text("书架") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenSettings,
                    icon = { Icon(Icons.Rounded.Settings, null) },
                    label = { Text("设置") }
                )
            }
        }
    ) { padding ->
        if (books.isEmpty()) {
            EmptyLibrary(
                modifier = Modifier.padding(padding),
                onImport = { launcher.launch(arrayOf("text/plain", "application/octet-stream")) }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("我的书架", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = libraryQuery,
                        onValueChange = { libraryQuery = it.take(60) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        label = { Text("搜索书名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                items(visibleBooks, key = { it.id }) { book ->
                    val bookChapters by viewModel.chapters(book.id).collectAsStateWithLifecycle(initialValue = emptyList())
                    BookCard(
                        book = book,
                        ttsCompleted = bookChapters.count { it.ttsStatus == TaskStatus.COMPLETED.name },
                        onClick = { onOpenBook(book.id) }
                    )
                }
                if (visibleBooks.isEmpty()) item { Text("没有匹配的小说", modifier = Modifier.padding(24.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier, onImport: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.story_brain_cover),
            contentDescription = "章境封面",
            modifier = Modifier.size(width = 190.dp, height = 250.dp).clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(24.dp))
        Text("导入你的第一本小说", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("自动分章、聊天式阅读、角色配音与动态故事图谱", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport) {
            Icon(Icons.Rounded.UploadFile, null)
            Spacer(Modifier.width(8.dp))
            Text("选择 TXT 文件")
        }
    }
}

@Composable
private fun BookCard(book: BookEntity, ttsCompleted: Int, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 64.dp, height = 88.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.story_brain_cover),
                    contentDescription = "${book.title} 封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("${book.chapterCount}章 · ${formatChars(book.totalChars)}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (book.chapterCount == 0) 0f else (book.currentChapterIndex + 1f) / book.chapterCount },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text("读到第 ${book.currentChapterIndex + 1} 章", style = MaterialTheme.typography.labelSmall)
                Text("已分析 ${book.analysisCompleted}/${book.chapterCount} 章 · 已配音 $ttsCompleted 章", style = MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
fun ImportPreviewScreen(viewModel: AppViewModel, onBack: () -> Unit, onImported: (String) -> Unit) {
    val state by viewModel.importState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("检查章节") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }
            )
        },
        bottomBar = {
            if (state.novel != null) {
                Surface(shadowElevation = 8.dp) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("重新选择") }
                        Button(onClick = { viewModel.confirmImport(onImported) }, modifier = Modifier.weight(1f)) { Text("确认导入") }
                    }
                }
            }
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在识别编码并自动分章…")
                }
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "导入失败", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onBack) { Text("返回书架") }
                }
            }
            state.novel != null -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::updateImportTitle,
                        label = { Text("书名") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("识别到 ${state.novel!!.chapters.size} 章", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("请快速检查章节边界，确认后将保存在本机。", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                itemsIndexed(state.novel!!.chapters) { index, chapter ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(38.dp))
                            Column(Modifier.weight(1f)) {
                                Text(chapter.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text("${chapter.content.length} 字", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookScreen(
    bookId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onReadChapter: (String) -> Unit,
    onOpenBrain: () -> Unit,
    onOpenMemory: () -> Unit
) {
    val book by viewModel.book(bookId).collectAsStateWithLifecycle(initialValue = null)
    val chapters by viewModel.chapters(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val analysis by viewModel.analysisState.collectAsStateWithLifecycle()
    val ttsProfiles by viewModel.ttsProfiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val globalTts by viewModel.ttsConfig.collectAsStateWithLifecycle(initialValue = com.storybrain.app.settings.TtsGlobalConfig())
    val bookTts by viewModel.bookTtsSetting(bookId).collectAsStateWithLifecycle(initialValue = null)
    var analysisCountText by rememberSaveable(bookId) { mutableStateOf("5") }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val initializedChapters = minOf(15, book?.chapterCount ?: 15)
    val initializationComplete = (book?.analysisCompleted ?: 0) >= initializedChapters
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!deleting) confirmDelete = false },
            title = { Text("删除小说？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将删除《${book?.title ?: "这本小说"}》的正文、分析结果、角色对话和本地配音。此操作无法撤销。")
                    deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, enabled = !deleting) { Text("取消") }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        deleteError = null
                        viewModel.deleteBook(
                            bookId,
                            onComplete = onBack,
                            onError = { message -> deleting = false; deleteError = message }
                        )
                    }
                ) { Text(if (deleting) "正在删除…" else "确认删除", color = MaterialTheme.colorScheme.error) }
            }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.title ?: "小说") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = {
                    Box {
                        IconButton(onClick = { moreMenuExpanded = true }) { Icon(Icons.Rounded.MoreVert, "更多") }
                        DropdownMenu(expanded = moreMenuExpanded, onDismissRequest = { moreMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("删除小说") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                onClick = {
                                    moreMenuExpanded = false
                                    deleteError = null
                                    confirmDelete = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("故事分析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("前15章初始化故事世界，完成后可自定义每次继续分析的章数。", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "已分析 ${book?.analysisCompleted ?: 0}/${book?.chapterCount ?: 0} 章",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = {
                                val total = book?.chapterCount ?: 0
                                if (total == 0) 0f else (book?.analysisCompleted ?: 0).toFloat() / total
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = onOpenBrain) {
                                Icon(Icons.Rounded.AccountTree, null)
                                Spacer(Modifier.width(6.dp))
                                Text("章境")
                            }
                            Button(onClick = {
                                chapters.getOrNull(book?.currentChapterIndex ?: 0)?.let { onReadChapter(it.id) }
                            }) {
                                Icon(Icons.Rounded.PlayArrow, null)
                                Spacer(Modifier.width(6.dp))
                                Text("开始阅读")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onOpenMemory, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Bookmarks, null)
                            Spacer(Modifier.width(6.dp))
                            Text("管理角色记忆库")
                        }
                        Spacer(Modifier.height(10.dp))
                        if (initializationComplete && (book?.analysisCompleted ?: 0) < (book?.chapterCount ?: 0)) {
                            OutlinedTextField(
                                value = analysisCountText,
                                onValueChange = { value -> analysisCountText = value.filter(Char::isDigit).take(4) },
                                label = { Text("本次继续分析章数") },
                                supportingText = {
                                    Text("剩余 ${(book?.chapterCount ?: 0) - (book?.analysisCompleted ?: 0)} 章")
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(
                            onClick = {
                                viewModel.analyzeBook(
                                    bookId,
                                    chapterCount = if (initializationComplete) analysisCountText.toIntOrNull() ?: 1 else null
                                )
                            },
                            enabled = !analysis.running && (book?.analysisCompleted ?: 0) < (book?.chapterCount ?: 0),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.CloudSync, null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    analysis.running && analysis.bookId == bookId -> "LLM 分析中…"
                                    !initializationComplete && (book?.analysisCompleted ?: 0) == 0 -> "分析前15章"
                                    !initializationComplete -> "继续初始化至第15章"
                                    (book?.analysisCompleted ?: 0) >= (book?.chapterCount ?: 0) -> "全书分析完成"
                                    else -> "继续分析 ${analysisCountText.toIntOrNull() ?: 1} 章"
                                }
                            )
                        }
                        if (analysis.bookId == bookId && analysis.message != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                analysis.message.orEmpty(),
                                color = if (analysis.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("本书主力引擎", fontWeight = FontWeight.Bold)
                        Text(
                            bookTts?.primaryProfileId?.let { id -> ttsProfiles.firstOrNull { it.id == id }?.displayName }
                                ?: "跟随全局 · ${ttsProfiles.firstOrNull { it.id == globalTts.globalProfileId }?.displayName ?: "Edge TTS"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        var engineMenuOpen by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { engineMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.GraphicEq, null)
                                Spacer(Modifier.width(6.dp))
                                Text("选择本书主力引擎")
                            }
                            DropdownMenu(engineMenuOpen, { engineMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("跟随全局") },
                                    onClick = { viewModel.setBookPrimaryProfile(bookId, null); engineMenuOpen = false }
                                )
                                ttsProfiles.forEach { profile ->
                                    DropdownMenuItem(
                                        text = { Text(profile.displayName) },
                                        onClick = { viewModel.setBookPrimaryProfile(bookId, profile.id); engineMenuOpen = false }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("章节目录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            items(chapters, key = { it.id }) { chapter ->
                ChapterRow(chapter, onClick = {
                    viewModel.markReading(bookId, chapter.chapterIndex)
                    onReadChapter(chapter.id)
                })
            }
        }
    }
}

@Composable
private fun ChapterRow(chapter: ChapterEntity, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${chapter.chapterIndex + 1}", modifier = Modifier.width(40.dp), color = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(chapter.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${chapter.charCount} 字 · ${ttsLabel(chapter.ttsStatus)}", style = MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
fun ReaderScreen(
    bookId: String,
    chapterId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenChapter: (String) -> Unit
) {
    val chapter by viewModel.chapter(chapterId).collectAsStateWithLifecycle(initialValue = null)
    val chapters by viewModel.chapters(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val characters by viewModel.characters(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    var pendingMemory by remember { mutableStateOf<PendingReadingMemory?>(null) }
    val knownSpeakers = remember(characters) {
        buildMap {
            characters.forEach { character ->
                put(character.canonicalName, character.canonicalName)
                val aliases = runCatching { JSONArray(character.aliasesJson) }.getOrNull() ?: JSONArray()
                for (index in 0 until aliases.length()) {
                    aliases.optString(index).trim().takeIf { it.isNotBlank() }?.let { alias ->
                        putIfAbsent(alias, character.canonicalName)
                    }
                }
            }
        }
    }
    val blocks = remember(chapter?.content, knownSpeakers) {
        chapter?.let { TextToChatParser.parse(it.content, knownSpeakers) }.orEmpty()
    }
    val currentIndex = chapters.indexOfFirst { it.id == chapterId }
    pendingMemory?.let { memory ->
        MemoryEditorDialog(
            title = "保存为原文记忆",
            initialTitle = memory.title,
            initialContent = memory.content,
            onDismiss = { pendingMemory = null },
            onSave = { title, content ->
                viewModel.saveNewMemory(
                    bookId = bookId,
                    type = MemoryType.EXCERPT,
                    title = title,
                    content = content,
                    chapterIndex = chapter?.chapterIndex,
                    characterIds = memory.characterId?.let(::listOf).orEmpty()
                ) { pendingMemory = null }
            }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(chapter?.title ?: "正在读取", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("聊天式阅读", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = {
                    if (!chapter?.ttsManifestPath.isNullOrBlank()) {
                        IconButton(
                            enabled = !ttsState.running,
                            onClick = {
                                val current = chapter ?: return@IconButton
                                if (ttsState.playing && ttsState.chapterId == current.id) viewModel.stopChapterTts()
                                else viewModel.playChapterTts(current.id, current.ttsManifestPath.orEmpty())
                            }
                        ) {
                            Icon(
                                if (ttsState.playing && ttsState.chapterId == chapter?.id) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                if (ttsState.playing && ttsState.chapterId == chapter?.id) "停止配音" else "播放本章配音"
                            )
                        }
                    }
                    IconButton(
                        enabled = !ttsState.running,
                        onClick = { chapter?.let { viewModel.generateChapterTts(bookId, it.id) } }
                    ) {
                        Icon(Icons.Rounded.GraphicEq, if (chapter?.ttsManifestPath == null) "生成本章配音" else "重新生成本章配音")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 6.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        enabled = currentIndex > 0,
                        onClick = {
                            chapters.getOrNull(currentIndex - 1)?.let {
                                if (ttsState.playing) viewModel.stopChapterTts()
                                viewModel.markReading(bookId, it.chapterIndex)
                                onOpenChapter(it.id)
                            }
                        }
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "上一章") }
                    chapter?.let { Text("第 ${it.chapterIndex + 1} 章 · ${ttsLabel(it.ttsStatus)}", style = MaterialTheme.typography.labelMedium) }
                    IconButton(
                        enabled = currentIndex >= 0 && currentIndex < chapters.lastIndex,
                        onClick = {
                            chapters.getOrNull(currentIndex + 1)?.let {
                                if (ttsState.playing) viewModel.stopChapterTts()
                                viewModel.markReading(bookId, it.chapterIndex)
                                onOpenChapter(it.id)
                            }
                        }
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "下一章") }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp, 16.dp, 14.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (ttsState.chapterId == chapterId && (ttsState.progress != null || ttsState.message != null)) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                ttsState.progress ?: ttsState.message.orEmpty(),
                                color = if (ttsState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            if (ttsState.running) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
            items(blocks) { block ->
                when (block) {
                    is ReadingBlock.Dialogue -> DialogueBubble(block) {
                        pendingMemory = PendingReadingMemory(
                            title = "${block.speaker}的原文对白",
                            content = block.text,
                            characterId = characters.firstOrNull { it.canonicalName == block.speaker }?.id
                        )
                    }
                    is ReadingBlock.Narration -> NarrationCard(block.text) {
                        pendingMemory = PendingReadingMemory("第${(chapter?.chapterIndex ?: 0) + 1}章旁白", block.text)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogueBubble(block: ReadingBlock.Dialogue, onLongClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) { Text(block.speaker.take(1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary) }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.widthIn(max = 310.dp)) {
            Text(block.speaker, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            Surface(
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick),
                shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(block.text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun NarrationCard(text: String, onLongClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp).combinedClickable(onClick = {}, onLongClick = onLongClick),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .58f)
        ) {
            Text(text, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private data class PendingReadingMemory(val title: String, val content: String, val characterId: String? = null)

@Composable
fun StoryBrainScreen(
    bookId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenMemory: () -> Unit,
    onChatCharacter: (String) -> Unit
) {
    val characters by viewModel.characters(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val relations by viewModel.relations(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val nodes by viewModel.plotNodes(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val memoryCount by viewModel.memoryCount(bookId).collectAsStateWithLifecycle(initialValue = 0)
    val ttsConfig by viewModel.ttsConfig.collectAsStateWithLifecycle(initialValue = com.storybrain.app.settings.TtsGlobalConfig())
    val ttsProfiles by viewModel.ttsProfiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val bookTts by viewModel.bookTtsSetting(bookId).collectAsStateWithLifecycle(initialValue = null)
    val activeBindings by viewModel.activeVoiceBindings(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val edgeVoices by viewModel.ttsVoicePool(TtsProfileIds.EDGE).collectAsStateWithLifecycle(initialValue = emptyList())
    val fishVoices by viewModel.ttsVoicePool(TtsProfileIds.FISH).collectAsStateWithLifecycle(initialValue = emptyList())
    val compatibleVoices by viewModel.ttsVoicePool(TtsProfileIds.OPENAI).collectAsStateWithLifecycle(initialValue = emptyList())
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val content = pendingExport
        if (uri != null && content != null) viewModel.writeNeo4jExport(uri, content)
        pendingExport = null
    }
    val tabs = listOf("剧情链", "角色图", "地图")
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("章境") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = onOpenMemory) { Icon(Icons.Rounded.Bookmarks, "打开记忆库") }
                    IconButton(
                        enabled = !exportState.running,
                        onClick = {
                            viewModel.prepareNeo4jExport(bookId) { content ->
                                pendingExport = content
                                exportLauncher.launch("story-brain-$bookId.cypher")
                            }
                        }
                    ) { Icon(Icons.Rounded.FileDownload, "导出 Neo4j Cypher") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("角色", characters.size.toString(), Icons.Rounded.Groups, Modifier.weight(1f))
                MetricCard("关系", relations.size.toString(), Icons.Rounded.AccountTree, Modifier.weight(1f))
                MetricCard("事件", nodes.size.toString(), Icons.Rounded.Schedule, Modifier.weight(1f))
            }
            OutlinedButton(onClick = onOpenMemory, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Icon(Icons.Rounded.Bookmarks, null)
                Spacer(Modifier.width(8.dp))
                Text("记忆库 · $memoryCount 条")
            }
            exportState.message?.let { message ->
                Text(
                    message,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = if (exportState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title -> Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) }) }
            }
            val icon = when (selectedTab) {
                0 -> Icons.Rounded.AccountTree
                1 -> Icons.Rounded.Groups
                else -> Icons.Rounded.Map
            }
            val hasSelectedData = when (selectedTab) {
                0 -> nodes.isNotEmpty()
                1 -> characters.isNotEmpty()
                else -> nodes.any { !it.locationName.isNullOrBlank() }
            }
            if (!hasSelectedData) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, null, Modifier.size(60.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(14.dp))
                        Text("等待故事分析", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("完成LLM分析后，这里会按章节展示动态${tabs[selectedTab]}。", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        StoryGraphVisualization(selectedTab, characters, relations, nodes)
                    }
                    when (selectedTab) {
                        0 -> items(nodes, key = { it.id }) { node ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(node.title, fontWeight = FontWeight.Bold)
                                    Text("第 ${node.startChapterIndex + 1} 章${node.endChapterIndex?.let { "—第 ${it + 1} 章" }.orEmpty()}", style = MaterialTheme.typography.labelSmall)
                                    if (node.summary.isNotBlank()) Text(node.summary, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        1 -> items(characters, key = { it.id }) { character ->
                            val relatedCount = relations.count { it.fromCharacterId == character.id || it.toCharacterId == character.id }
                            var voiceMenuOpen by remember(character.id) { mutableStateOf(false) }
                            val allVoices = edgeVoices + fishVoices + compatibleVoices
                            val sortedVoices = remember(character, allVoices) {
                                allVoices.sortedByDescending { voiceMatchScore(character, it) }
                            }
                            val binding = activeBindings.firstOrNull { it.characterId == character.id }
                            val primaryId = bookTts?.primaryProfileId ?: ttsConfig.globalProfileId
                            val primaryName = ttsProfiles.firstOrNull { it.id == primaryId }?.displayName ?: "Edge TTS"
                            val localImportance = (relatedCount * 0.08f + nodes.count { character.id in runCatching { JSONArray(it.participantIdsJson) }.getOrNull().stringValues() } * 0.12f).coerceAtMost(1f)
                            val importance = maxOf(character.importanceScore, localImportance)
                            Card(
                                onClick = { onChatCharacter(character.id) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) { Text(character.canonicalName.take(1), fontWeight = FontWeight.Bold) }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(character.canonicalName, fontWeight = FontWeight.Bold)
                                        Text("${character.personality.ifBlank { "性格待完善" }} · $relatedCount 条关系", style = MaterialTheme.typography.bodySmall)
                                        if (importance >= .65f && primaryId != TtsProfileIds.FISH) {
                                            Text("重点角色 · 建议使用 Fish", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        TextButton(onClick = { onChatCharacter(character.id) }) { Text("对话") }
                                        Box {
                                            OutlinedButton(
                                                onClick = { voiceMenuOpen = true },
                                                enabled = sortedVoices.isNotEmpty(),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                            ) {
                                                Icon(Icons.Rounded.GraphicEq, null, Modifier.size(16.dp))
                                                Spacer(Modifier.width(5.dp))
                                                Text(
                                                    binding?.let { "${ttsProfiles.firstOrNull { p -> p.id == it.profileId }?.displayName ?: "平台"} · ${it.voiceName}" }
                                                        ?: "跟随 $primaryName",
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            DropdownMenu(expanded = voiceMenuOpen, onDismissRequest = { voiceMenuOpen = false }) {
                                                DropdownMenuItem(
                                                    text = { Text("跟随本书主力引擎") },
                                                    onClick = { viewModel.clearCharacterVoice(character.id); voiceMenuOpen = false }
                                                )
                                                sortedVoices.forEachIndexed { index, voice ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Column {
                                                                Text("${ttsProfiles.firstOrNull { it.id == voice.profileId }?.displayName ?: "平台"} · ${voice.voiceName}")
                                                                Text(
                                                                    buildString {
                                                                        if (index == 0 && voiceMatchScore(character, voice) > 0) append("推荐 · ")
                                                                        val tags = runCatching { JSONArray(voice.tagsJson) }.getOrNull() ?: JSONArray()
                                                                        append(listOf(voice.gender, tags.stringValues().joinToString()).filter(String::isNotBlank).joinToString(" · "))
                                                                    },
                                                                    style = MaterialTheme.typography.labelSmall
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            viewModel.assignCharacterVoice(character.id, voice.profileId, voice.voiceId, voice.voiceName)
                                                            voiceMenuOpen = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> items(nodes.mapNotNull { it.locationName }.distinct()) { location ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Map, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Text(location, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun voiceMatchScore(character: StoryCharacterEntity, voice: TtsProfileVoicePoolEntity): Int {
    var score = if (character.gender == voice.gender) 5 else 0
    val personality = character.personality.lowercase()
    val tags = runCatching { JSONArray(voice.tagsJson) }.getOrNull() ?: JSONArray()
    score += tags.stringValues().count { tag -> tag.isNotBlank() && personality.contains(tag.lowercase()) } * 2
    return score
}

private fun JSONArray?.stringValues(): List<String> = if (this == null) emptyList() else
    (0 until length()).map(::optString).filter(String::isNotBlank)

@Composable
private fun StoryGraphVisualization(
    tab: Int,
    characters: List<StoryCharacterEntity>,
    relations: List<StoryRelationEntity>,
    plots: List<PlotNodeEntity>
) {
    val graph = remember(tab, characters, relations, plots) {
        when (tab) {
            0 -> {
                val selected = plots.take(20)
                val nodes = selected.map { GraphNode(it.id, it.title.take(8)) }
                val ids = nodes.map { it.id }.toSet()
                val explicit = selected.flatMap { plot ->
                    val parents = runCatching { JSONArray(plot.parentIdsJson) }.getOrNull() ?: JSONArray()
                    (0 until parents.length()).mapNotNull { index ->
                        parents.optString(index).takeIf(ids::contains)?.let { GraphEdge(it, plot.id, "剧情") }
                    }
                }
                val edges = if (explicit.isNotEmpty()) explicit else nodes.zipWithNext().map { GraphEdge(it.first.id, it.second.id, "推进") }
                GraphData(nodes, edges)
            }
            1 -> {
                val selected = characters.take(20)
                val ids = selected.map { it.id }.toSet()
                GraphData(
                    selected.map { GraphNode(it.id, it.canonicalName.take(8)) },
                    relations.filter { it.fromCharacterId in ids && it.toCharacterId in ids }.map {
                        GraphEdge(it.fromCharacterId, it.toCharacterId, relationLabel(it.relationType), it.relationType == "PROTECTS")
                    }
                )
            }
            else -> {
                val locations = plots.mapNotNull { it.locationName }.distinct().take(20)
                val nodes = locations.map { GraphNode(it, it.take(8)) }
                val sequence = plots.mapNotNull { it.locationName }.filter { it in locations }.distinct()
                GraphData(nodes, sequence.zipWithNext().map { GraphEdge(it.first, it.second, "剧情移动") })
            }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                when (tab) { 0 -> "动态剧情链"; 1 -> "角色关系图（绿色为保护关系）"; else -> "故事地点关联图" },
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            SimpleStoryGraph(graph)
        }
    }
}

@Composable
private fun SimpleStoryGraph(graph: GraphData) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val protect = Color(0xFF2E7D32)
    Canvas(Modifier.fillMaxWidth().height(330.dp)) {
        if (graph.nodes.isEmpty()) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val orbit = minOf(size.width, size.height) * .36f
        val positions = graph.nodes.mapIndexed { index, node ->
            val angle = -PI / 2 + 2 * PI * index / graph.nodes.size
            node.id to Offset(
                center.x + (orbit * cos(angle)).toFloat(),
                center.y + (orbit * sin(angle)).toFloat()
            )
        }.toMap()
        graph.edges.forEach { edge ->
            val from = positions[edge.from] ?: return@forEach
            val to = positions[edge.to] ?: return@forEach
            drawLine(if (edge.protects) protect else primary.copy(alpha = .65f), from, to, strokeWidth = if (edge.protects) 7f else 4f)
            drawContext.canvas.nativeCanvas.drawText(
                edge.label,
                (from.x + to.x) / 2f,
                (from.y + to.y) / 2f,
                android.graphics.Paint().apply { color = (if (edge.protects) protect else onSurface).toArgb(); textSize = 24f }
            )
        }
        graph.nodes.forEach { node ->
            val position = positions.getValue(node.id)
            drawCircle(primary, 46f, position)
            drawCircle(surface, 39f, position)
            drawContext.canvas.nativeCanvas.drawText(
                node.label,
                position.x - node.label.length * 13f,
                position.y + 9f,
                android.graphics.Paint().apply { color = onSurface.toArgb(); textSize = 27f; isFakeBoldText = true }
            )
        }
    }
}

private data class GraphNode(val id: String, val label: String)
private data class GraphEdge(val from: String, val to: String, val label: String, val protects: Boolean = false)
private data class GraphData(val nodes: List<GraphNode>, val edges: List<GraphEdge>)

private fun relationLabel(type: String): String = when (type) {
    "PROTECTS" -> "保护"
    "FRIEND" -> "朋友"
    "ENEMY" -> "敌对"
    "FAMILY" -> "家人"
    "MENTOR" -> "师徒"
    "LOVES" -> "爱慕"
    "ALLY" -> "同盟"
    "BETRAYS" -> "背叛"
    else -> type.take(8)
}

@Composable
fun CharacterChatScreen(
    bookId: String,
    characterId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val characters by viewModel.characters(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val sessions by viewModel.chatSessions(characterId).collectAsStateWithLifecycle(initialValue = emptyList())
    val state by viewModel.characterChatState.collectAsStateWithLifecycle()
    val pickerState by viewModel.memoryPickerState.collectAsStateWithLifecycle()
    val character = characters.firstOrNull { it.id == characterId }
    var currentSessionId by rememberSaveable(characterId) { mutableStateOf("") }
    val messagesFlow = remember(currentSessionId) { viewModel.chatMessages(currentSessionId) }
    val messages by messagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentSession = sessions.firstOrNull { it.id == currentSessionId }
    var draft by rememberSaveable(characterId) { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDeleteSession by remember { mutableStateOf(false) }
    var sessionMenuExpanded by remember { mutableStateOf(false) }
    var renameSession by remember { mutableStateOf(false) }
    var showMemoryPicker by remember { mutableStateOf(false) }
    var showDefaultMemoryNotice by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var saveMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(characterId) {
        viewModel.ensureChatSession(bookId, characterId) { sessionId, seededNow ->
            currentSessionId = sessionId
            showDefaultMemoryNotice = seededNow
        }
    }
    LaunchedEffect(sessions, currentSessionId) {
        if (sessions.isNotEmpty() && sessions.none { it.id == currentSessionId }) currentSessionId = sessions.first().id
    }
    LaunchedEffect(currentSessionId) {
        if (currentSessionId.isNotBlank()) viewModel.loadMemoryPicker(bookId, characterId, currentSessionId)
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    if (showMemoryPicker && currentSessionId.isNotBlank()) {
        MemoryPickerSheet(bookId, characterId, currentSessionId, draft, viewModel) { showMemoryPicker = false }
    }
    if (showDefaultMemoryNotice) {
        AlertDialog(
            onDismissRequest = { showDefaultMemoryNotice = false },
            title = { Text("已建立角色默认记忆") },
            text = { Text("已把当前可用的直接关系和近期剧情设为该角色的默认记忆。你可以立即检查和调整；未选择的内容不会发送给模型。") },
            dismissButton = { TextButton(onClick = { showDefaultMemoryNotice = false }) { Text("稍后") } },
            confirmButton = {
                Button(onClick = {
                    showDefaultMemoryNotice = false
                    showMemoryPicker = true
                }) { Text("现在调整") }
            }
        )
    }
    if (renameSession) {
        var title by remember(currentSessionId) { mutableStateOf(currentSession?.title.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renameSession = false },
            title = { Text("重命名对话") },
            text = { OutlinedTextField(title, { title = it.take(40) }, label = { Text("会话标题") }, singleLine = true) },
            dismissButton = { TextButton(onClick = { renameSession = false }) { Text("取消") } },
            confirmButton = { Button(onClick = { viewModel.renameChatSession(currentSessionId, title); renameSession = false }) { Text("保存") } }
        )
    }
    if (confirmDeleteSession) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSession = false },
            title = { Text("删除当前会话？") },
            text = { Text("当前会话中的消息和临时记忆选择将被删除，角色默认记忆不受影响。") },
            dismissButton = { TextButton(onClick = { confirmDeleteSession = false }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChatSession(currentSessionId, bookId, characterId) { currentSessionId = it }
                    confirmDeleteSession = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空对话？") },
            text = { Text("将删除当前会话中的全部本地对话记录，其他会话不受影响。") },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCharacterChat(currentSessionId)
                    confirmClear = false
                }) { Text("确认清空", color = MaterialTheme.colorScheme.error) }
            }
        )
    }
    actionMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { actionMessage = null },
            title = { Text("消息操作") },
            text = { Text(message.content, maxLines = 6, overflow = TextOverflow.Ellipsis) },
            dismissButton = { TextButton(onClick = { actionMessage = null }) { Text("取消") } },
            confirmButton = {
                Row {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(message.content)); actionMessage = null }) {
                        Icon(Icons.Rounded.ContentCopy, null); Text("复制")
                    }
                    TextButton(onClick = { saveMessage = message; actionMessage = null }) {
                        Icon(Icons.Rounded.Bookmarks, null); Text("存为记忆")
                    }
                }
            }
        )
    }
    saveMessage?.let { message ->
        MemoryEditorDialog(
            title = "保存对话记忆",
            initialTitle = "与${character?.canonicalName ?: "角色"}的对话",
            initialContent = message.content,
            onDismiss = { saveMessage = null },
            onSave = { title, content ->
                viewModel.saveNewMemory(bookId, MemoryType.CHAT, title, content, characterIds = listOf(characterId)) {
                    saveMessage = null
                    if (currentSessionId.isNotBlank()) viewModel.loadMemoryPicker(bookId, characterId, currentSessionId)
                }
            }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(character?.canonicalName ?: "角色对话")
                        Text(currentSession?.title ?: "正在准备对话…", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = {
                    Box {
                        IconButton(onClick = { sessionMenuExpanded = true }) { Icon(Icons.Rounded.Schedule, "会话历史") }
                        DropdownMenu(expanded = sessionMenuExpanded, onDismissRequest = { sessionMenuExpanded = false }) {
                            ChatSessionMenuContent(
                                sessions = sessions,
                                currentSessionId = currentSessionId,
                                onSelect = { currentSessionId = it; sessionMenuExpanded = false },
                                onCreate = {
                                    viewModel.createChatSession(bookId, characterId) { currentSessionId = it }
                                    sessionMenuExpanded = false
                                },
                                onRename = { renameSession = true; sessionMenuExpanded = false },
                                onClear = { confirmClear = true; sessionMenuExpanded = false },
                                onDelete = { confirmDeleteSession = true; sessionMenuExpanded = false }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    val pickerMatches = pickerState.sessionId == currentSessionId
                    val defaultCount = if (pickerMatches) pickerState.items.count { it.isDefault } else 0
                    val sessionCount = if (pickerMatches) pickerState.items.count { it.isSession } else 0
                    OutlinedButton(
                        onClick = { showMemoryPicker = true },
                        enabled = currentSessionId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Bookmarks, null)
                        Spacer(Modifier.width(8.dp))
                        Text("对话记忆 · 默认 $defaultCount · 本次 $sessionCount")
                    }
                    Spacer(Modifier.height(6.dp))
                    state.error?.takeIf { state.characterId == characterId }?.let {
                        ChatErrorMessage(it)
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it.take(1000) },
                            label = { Text("和角色说点什么") },
                            enabled = !state.running,
                            modifier = Modifier.weight(1f),
                            maxLines = 4
                        )
                        IconButton(
                            enabled = draft.isNotBlank() && !state.running && currentSessionId.isNotBlank(),
                            onClick = {
                                val sent = draft
                                viewModel.sendCharacterMessage(bookId, characterId, currentSessionId, sent) {
                                    draft = ""
                                }
                            }
                        ) { Icon(Icons.AutoMirrored.Rounded.Send, "发送") }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "你正在和 ${character?.canonicalName ?: "角色"} 开始一个新会话。先从记忆库选择允许角色使用的剧情，再开始对话。",
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            items(messages, key = { it.id }) { message -> CharacterChatBubble(message, character?.canonicalName.orEmpty()) { actionMessage = message } }
            if (state.running && state.characterId == characterId) {
                item { Text("${character?.canonicalName ?: "角色"} 正在思考…", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
internal fun CharacterChatBubble(message: ChatMessageEntity, characterName: String, onLongClick: () -> Unit) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Text(if (isUser) "我" else characterName, style = MaterialTheme.typography.labelSmall)
            Surface(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .testTag("chat-message-${message.id}")
                    .combinedClickable(onClick = {}, onLongClick = onLongClick),
                shape = if (isUser) RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp) else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) { Text(message.content, Modifier.padding(12.dp)) }
        }
    }
}

@Composable
internal fun ChatSessionMenuContent(
    sessions: List<ChatSessionEntity>,
    currentSessionId: String,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onRename: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit
) {
    sessions.forEach { session ->
        DropdownMenuItem(
            text = { Text(if (session.id == currentSessionId) "✓ ${session.title}" else session.title) },
            onClick = { onSelect(session.id) },
            modifier = Modifier.testTag("chat-session-${session.id}")
        )
    }
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("新建对话") },
        leadingIcon = { Icon(Icons.Rounded.Add, null) },
        onClick = onCreate,
        modifier = Modifier.testTag("chat-session-create")
    )
    DropdownMenuItem(text = { Text("重命名当前会话") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = onRename)
    DropdownMenuItem(text = { Text("清空当前消息") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = onClear)
    DropdownMenuItem(text = { Text("删除当前会话") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = onDelete)
}

@Composable
internal fun ChatErrorMessage(message: String) {
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag("chat-error")
    )
}

@Composable
private fun MetricCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatChars(chars: Long): String = when {
    chars >= 10_000 -> "%.1f万字".format(chars / 10_000f)
    else -> "${chars}字"
}

private fun ttsLabel(status: String): String = when (status) {
    TaskStatus.QUEUED.name -> "配音已排队"
    TaskStatus.RUNNING.name -> "正在配音"
    TaskStatus.COMPLETED.name -> "配音可用"
    TaskStatus.FAILED.name -> "配音失败"
    else -> "未配音"
}
