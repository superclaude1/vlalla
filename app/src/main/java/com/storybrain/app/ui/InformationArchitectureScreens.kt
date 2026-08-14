@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.storybrain.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storybrain.app.R
import com.storybrain.app.data.TaskStatus

@Composable
fun BookHubScreen(bookId: String, viewModel: AppViewModel, onBack: () -> Unit, onRead: (String) -> Unit, onOpenChapters: () -> Unit, onOpenStory: () -> Unit, onOpenAudio: () -> Unit) {
    val book by viewModel.book(bookId).collectAsStateWithLifecycle(initialValue = null)
    val chapters by viewModel.chapters(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val coverError by viewModel.coverError.collectAsStateWithLifecycle()
    var moreMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var deleteError by rememberSaveable { mutableStateOf<String?>(null) }
    val current = chapters.getOrNull(book?.currentChapterIndex ?: 0)
    val status = when {
        (book?.chapterCount ?: 0) > 0 && book?.analysisCompleted == book?.chapterCount -> "已完成"
        (book?.analysisCompleted ?: 0) > 0 -> "分析中"
        else -> "未分析"
    }
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
            dismissButton = { TextButton({ confirmDelete = false }, enabled = !deleting) { Text("取消") } },
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
    Scaffold(topBar = {
        TopAppBar(
            modifier = Modifier.height(ReactReferenceContract.topBarHeightDp.dp),
            title = { Text("") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
            actions = {
                Box {
                    IconButton({ moreMenuExpanded = true }) { Icon(Icons.Rounded.MoreVert, "更多") }
                    DropdownMenu(moreMenuExpanded, { moreMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("生成封面") },
                            leadingIcon = { Icon(Icons.Rounded.Image, null) },
                            onClick = {
                                moreMenuExpanded = false
                                book?.let { viewModel.generateBookCover(bookId, it.title, regenerate = true) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除小说") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { moreMenuExpanded = false; deleteError = null; confirmDelete = true }
                        )
                    }
                }
            }
        )
    }, bottomBar = {
        Surface(tonalElevation = 0.dp, color = MaterialTheme.colorScheme.background) {
            Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { current?.let { onRead(it.id) } },
                    enabled = current != null,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(ReactReferenceContract.primaryButtonRadiusDp.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("继续阅读 · 第${(book?.currentChapterIndex ?: 0) + 1}章") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    BookPortal(NavigationArchitecture.bookHubRows[1], Icons.Rounded.AccountTree, Modifier, onOpenStory)
                    Spacer(Modifier.width(10.dp))
                    BookPortal(NavigationArchitecture.bookHubRows[2], Icons.Rounded.RecordVoiceOver, Modifier, onOpenAudio)
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val header = BookHeaderParser.parse(book?.title.orEmpty())
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(header.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(header.author?.let { "$it 著" }, "共 ${book?.chapterCount ?: 0} 章", status).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                coverError?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("目录", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("全部 ${book?.chapterCount ?: 0} 章 ›", Modifier.clickable(onClick = onOpenChapters), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp)) {
                items(chapters.take(ReactReferenceContract.bookPreviewChapterCount), key = { it.id }) { chapter ->
                    val reached = chapter.chapterIndex <= (book?.currentChapterIndex ?: -1)
                    Row(
                        Modifier.fillMaxWidth().height(42.dp).clickable { viewModel.markReading(bookId, chapter.chapterIndex); onRead(chapter.id) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${chapter.chapterIndex + 1}".padStart(2, '0'), Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(chapter.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(shape = RoundedCornerShape(50), color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(width = 14.dp, height = 5.dp)) {}
                    }
                    HorizontalDivider()
                }
                if (chapters.size > ReactReferenceContract.bookPreviewChapterCount) item {
                    Text("…", Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable private fun BookPortal(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(ReactReferenceContract.portalRadiusDp.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null); Spacer(Modifier.height(8.dp)); Text(title, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
fun ChapterListScreen(bookId: String, viewModel: AppViewModel, onBack: () -> Unit, onRead: (String) -> Unit) {
    val chapters by viewModel.chapters(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val book by viewModel.book(bookId).collectAsStateWithLifecycle(initialValue = null)
    Scaffold(topBar = { CompactBackBar("目录", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(chapters, key = { it.id }) { chapter ->
                Row(Modifier.fillMaxWidth().height(56.dp).clickable { viewModel.markReading(bookId, chapter.chapterIndex); onRead(chapter.id) }, verticalAlignment = Alignment.CenterVertically) {
                    Text("${chapter.chapterIndex + 1}".padStart(2, '0'), Modifier.width(42.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(chapter.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        BookChapterStatus.label(
                            chapter.chapterIndex,
                            book?.currentChapterIndex ?: -1,
                            chapter.ttsStatus
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun StoryHubScreen(onBack: () -> Unit, onAnalysis: () -> Unit, onGraph: () -> Unit, onMemory: () -> Unit) {
    Scaffold(topBar = { CompactBackBar("故事", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            NeutralRow("分析", "继续解析章节和人物", Icons.Rounded.Psychology, onAnalysis)
            NeutralRow("图谱", "剧情、角色、地点", Icons.Rounded.AccountTree, onGraph)
            NeutralRow("记忆", "管理故事记忆", Icons.Rounded.Bookmarks, onMemory)
        }
    }
}

@Composable
fun StoryAnalysisScreen(bookId: String, viewModel: AppViewModel, onBack: () -> Unit) {
    val book by viewModel.book(bookId).collectAsStateWithLifecycle(initialValue = null)
    val analysis by viewModel.analysisState.collectAsStateWithLifecycle()
    var count by rememberSaveable { mutableStateOf("5") }
    var confirmAnalyzeAll by rememberSaveable { mutableStateOf(false) }
    val done = book?.analysisCompleted ?: 0
    val total = book?.chapterCount ?: 0
    val action = StoryAnalysisPolicy.state(done, total, count)
    val running = StoryAnalysisPolicy.isRunningForBook(bookId, analysis.bookId, analysis.running)
    if (confirmAnalyzeAll) {
        AlertDialog(
            onDismissRequest = { if (!running) confirmAnalyzeAll = false },
            title = { Text("分析剩余全部章节？") },
            text = { Text("将从第 ${done + 1} 章开始分析剩余 ${action.remaining} 章。分析会调用 LLM，费用当前未知；可在运行中取消。") },
            confirmButton = {
                Button(onClick = { confirmAnalyzeAll = false; viewModel.analyzeAll(bookId) }, enabled = !running) { Text("确认分析") }
            },
            dismissButton = { TextButton(onClick = { confirmAnalyzeAll = false }, enabled = !running) { Text("取消") } }
        )
    }
    Scaffold(topBar = { CompactBackBar("分析", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("已分析 $done / $total 章", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator({ if (total == 0) 0f else done.toFloat() / total }, Modifier.fillMaxWidth())
            if (action.showIncrementControls && action.remaining > 0) {
                Text("剩余 ${action.remaining} 章", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    action.inputValue,
                    { input -> count = StoryAnalysisPolicy.state(done, total, input.filter(Char::isDigit).take(4)).inputValue },
                    Modifier.fillMaxWidth(),
                    label = { Text("本次章节数（1-${action.remaining}）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Button(
                { if (running) viewModel.cancelAnalysis() else viewModel.analyzeBook(bookId, action.chapterCount) },
                Modifier.fillMaxWidth(),
                enabled = running || action.remaining > 0
            ) { Text(if (running) "取消当前分析" else action.actionLabel) }
            OutlinedButton(
                onClick = { confirmAnalyzeAll = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !running && action.remaining > 0
            ) { Text("一键分析剩余全部章节") }
            if (analysis.bookId == bookId) {
                Text("状态：${analysis.status.displayName()} · 已完成章节：${analysis.completedChapters}")
                Text(
                    "tokens：prompt ${analysis.promptTokens?.toString() ?: "未知"} · completion ${analysis.completionTokens?.toString() ?: "未知"} · total ${analysis.totalTokens?.toString() ?: "未知"}" +
                        "（${analysis.usageQuality.name.lowercase()}）" +
                        " · 费用：${analysis.cost}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            StoryAnalysisPolicy.messageForBook(bookId, analysis.bookId, analysis.message)?.let {
                Text(it, color = if (analysis.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StoryAnalysisPolicy.failurePrompt(analysis)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun AnalysisStatus.displayName(): String = when (this) {
    AnalysisStatus.IDLE -> "未开始"
    AnalysisStatus.RUNNING -> "分析中"
    AnalysisStatus.SUCCESS -> "成功"
    AnalysisStatus.FAILED -> "失败"
    AnalysisStatus.SKIPPED -> "跳过"
    AnalysisStatus.CANCELLED -> "已取消"
}

@Composable
fun StoryGraphScreen(bookId: String, viewModel: AppViewModel, onBack: () -> Unit, onChatCharacter: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val characters by viewModel.characters(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val relations by viewModel.relations(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val plots by viewModel.plotNodes(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val metrics = StoryGraphMetrics.metrics(characters.size, relations.size, plots.size)
    var pendingExport by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var activeRequestId by remember { mutableStateOf<Long?>(null) }
    var screenActive by remember { mutableStateOf(true) }
    DisposableEffect(bookId) {
        screenActive = true
        onDispose {
            screenActive = false
            activeRequestId?.let { viewModel.cancelNeo4jExport(bookId, it) }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val pending = pendingExport
        if (uri != null && pending != null) {
            viewModel.writeNeo4jExport(bookId, pending.first, uri, pending.second)
        } else {
            activeRequestId?.let { viewModel.cancelNeo4jExport(bookId, it) }
            activeRequestId = null
        }
        pendingExport = null
    }
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(ReactReferenceContract.topBarHeightDp.dp),
                title = { Text("图谱", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = {
                    IconButton(
                        enabled = !exportState.running,
                        onClick = {
                            activeRequestId = viewModel.prepareNeo4jExport(bookId) { requestId, content ->
                                if (screenActive && activeRequestId == requestId) {
                                    pendingExport = requestId to content
                                    exportLauncher.launch("story-brain-$bookId.cypher")
                                }
                            }
                        }
                    ) { Icon(Icons.Rounded.FileDownload, "导出 Neo4j Cypher") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GraphMetricText("角色", metrics.characters)
                GraphMetricText("关系", metrics.relations)
                GraphMetricText("事件", metrics.plots)
            }
            if (exportState.bookId == bookId && exportState.running) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在准备 Neo4j 导出…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                exportState.message.takeIf { exportState.bookId == bookId }?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        color = if (exportState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            TabRow(tab) { NavigationArchitecture.graphTabs.forEachIndexed { index, label -> Tab(tab == index, { tab = index }, text = { Text(label) }) } }
            StoryBrainScreen(bookId, viewModel, onBack = {}, onOpenMemory = {}, onChatCharacter = onChatCharacter, initialTab = tab, lockedTab = true, hideTopBar = true)
        }
    }
}

@Composable
private fun GraphMetricText(label: String, value: Int) {
    Text("$label $value", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
}

@Composable
fun AudioHubScreen(onBack: () -> Unit, onEngine: () -> Unit, onVoices: () -> Unit, onChapters: () -> Unit) {
    Scaffold(topBar = { CompactBackBar("配音", onBack) }) { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
        NeutralRow(NavigationArchitecture.audioHubRows[0], "选择本书配音服务", Icons.Rounded.GraphicEq, onEngine)
        NeutralRow(NavigationArchitecture.audioHubRows[1], "单独为角色分配声音", Icons.Rounded.RecordVoiceOver, onVoices)
        NeutralRow(NavigationArchitecture.audioHubRows[2], "生成和播放章节", Icons.AutoMirrored.Rounded.MenuBook, onChapters)
    } }
}

@Composable
fun AudioChaptersScreen(bookId: String, viewModel: AppViewModel, onBack: () -> Unit) {
    val chapters by viewModel.chapters(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    Scaffold(topBar = { CompactBackBar("章节音频", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(chapters, key = { it.id }) { chapter ->
                val active = ttsState.chapterId == chapter.id
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.GraphicEq, null)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${chapter.chapterIndex + 1}. ${chapter.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                active && ttsState.running -> ttsState.progress ?: "正在生成…"
                                active && ttsState.playing -> "正在播放"
                                active && ttsState.message != null -> ttsState.message.orEmpty()
                                chapter.ttsStatus == TaskStatus.COMPLETED.name -> "已生成"
                                chapter.ttsStatus == TaskStatus.FAILED.name -> "生成失败"
                                else -> "未生成"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (active && ttsState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (active && ttsState.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                    if (active && ttsState.playing) {
                        IconButton({ viewModel.stopChapterTts() }) { Icon(Icons.Rounded.Stop, "停止") }
                    } else if (!chapter.ttsManifestPath.isNullOrBlank()) {
                        IconButton(
                            enabled = !ttsState.running,
                            onClick = { viewModel.playChapterTts(chapter.id, chapter.ttsManifestPath.orEmpty()) }
                        ) { Icon(Icons.Rounded.PlayArrow, "播放") }
                    }
                    TextButton(
                        enabled = !ttsState.running,
                        onClick = { viewModel.generateChapterTts(bookId, chapter.id) }
                    ) { Text(when { chapter.ttsStatus == TaskStatus.FAILED.name -> "重试"; chapter.ttsManifestPath.isNullOrBlank() -> "生成"; else -> "重生成" }) }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable private fun CompactBackBar(title: String, onBack: () -> Unit) = TopAppBar(modifier = Modifier.height(ReactReferenceContract.topBarHeightDp.dp), title = { Text(title, style = MaterialTheme.typography.titleMedium) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } })
@Composable private fun NeutralRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().height(72.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Rounded.ChevronRight, null) }; HorizontalDivider() }
