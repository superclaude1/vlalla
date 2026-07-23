package com.storybrain.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storybrain.app.data.TaskStatus
import com.storybrain.app.data.BookEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookHubScreen(
    bookId: String,
    viewModel: AppViewModel,
    playbackViewModel: PlaybackViewModel,
    onBack: () -> Unit,
    onContinue: (String) -> Unit,
    onOpenChapters: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenPlot: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenRelations: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTts: () -> Unit
) {
    val detail by viewModel.bookDetail(bookId).collectAsStateWithLifecycle()
    val book = detail.book
    val current = detail.chapters.getOrNull(book?.currentChapterIndex ?: 0) ?: detail.chapters.firstOrNull()
    val analysisProgress = if ((book?.chapterCount ?: 0) > 0) {
        (book?.analysisCompleted ?: 0).toFloat() / (book?.chapterCount ?: 1)
    } else 0f
    val ttsCount = detail.chapters.count { TaskStatus.fromStorage(it.ttsStatus) == TaskStatus.COMPLETED }
    var coverError by remember { mutableStateOf<String?>(null) }
    val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.importBookCover(bookId, uri) { coverError = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.title ?: "书籍主页", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        BookCover(book, Modifier.size(width = 92.dp, height = 128.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(book?.title ?: "正在读取", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("${book?.chapterCount ?: 0} 章 · 分析 ${book?.analysisCompleted ?: 0} · 配音 $ttsCount")
                            LinearProgressIndicator(progress = { analysisProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Button(
                                onClick = { current?.let { onContinue(it.id) } },
                                enabled = current != null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.AutoStories, null)
                                Spacer(Modifier.width(6.dp))
                                Text("继续阅读")
                            }
                            OutlinedButton(
                                onClick = { current?.let { playbackViewModel.playChapter(bookId, it.id) } },
                                enabled = current != null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null)
                                Spacer(Modifier.width(6.dp))
                                Text("立即朗读")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { coverLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("更换封面") }
                                if (book?.coverPath != null) {
                                    OutlinedButton(
                                        onClick = { viewModel.restoreDefaultCover(bookId, book.coverPath) { coverError = it } },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("恢复默认") }
                                }
                            }
                            coverError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
            item { Text("书籍功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BookFeatureRow("章节目录", "浏览并打开章节", Icons.Rounded.Book, onOpenChapters)
                    BookFeatureRow("故事分析", "分析范围、进度与控制", Icons.Rounded.Analytics, onOpenAnalysis)
                    BookFeatureRow("章境 / 剧情", "剧情节点与故事脉络", Icons.Rounded.Route, onOpenPlot)
                    BookFeatureRow("人物", "人物档案与角色入口", Icons.Rounded.Groups, onOpenCharacters)
                    BookFeatureRow("关系", "人物关系与证据", Icons.Rounded.Hub, onOpenRelations)
                    BookFeatureRow("地点", "故事发生地点", Icons.Rounded.LocationOn, onOpenLocations)
                    BookFeatureRow("记忆", "摘录与长期记忆", Icons.Rounded.Memory, onOpenMemory)
                    BookFeatureRow("配音工作室", "音色、缓存与精品生成", Icons.Rounded.GraphicEq, onOpenTts)
                }
            }
        }
    }
}

@Composable
private fun BookFeatureRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(icon, null) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

enum class BookFeaturePage { CHAPTERS, ANALYSIS, PLOT, CHARACTERS, RELATIONS, LOCATIONS, TTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookFeatureScreen(
    bookId: String,
    page: BookFeaturePage,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onReadChapter: (String) -> Unit
) {
    val detail by viewModel.bookDetail(bookId).collectAsStateWithLifecycle()
    val brain by viewModel.storyBrainDetail(bookId).collectAsStateWithLifecycle()
    val title = when (page) {
        BookFeaturePage.CHAPTERS -> "章节目录"
        BookFeaturePage.ANALYSIS -> "故事分析"
        BookFeaturePage.PLOT -> "章境 / 剧情"
        BookFeaturePage.CHARACTERS -> "人物"
        BookFeaturePage.RELATIONS -> "关系"
        BookFeaturePage.LOCATIONS -> "地点"
        BookFeaturePage.TTS -> "配音工作室"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(title); Text(detail.book?.title.orEmpty(), style = MaterialTheme.typography.labelSmall) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (page) {
                BookFeaturePage.CHAPTERS -> items(detail.chapters, key = { it.id }) { chapter ->
                    InfoCard("第 ${chapter.chapterIndex + 1} 章 · ${chapter.title}", "${chapter.charCount} 字 · ${TaskStatus.fromStorage(chapter.ttsStatus).labelZh()}") {
                        onReadChapter(chapter.id)
                    }
                }
                BookFeaturePage.ANALYSIS -> {
                    item {
                        val book = detail.book
                        InfoCard("分析进度 ${book?.analysisCompleted ?: 0}/${book?.chapterCount ?: 0}", "后台执行，离开页面不会中断")
                    }
                    item {
                        Button(onClick = { viewModel.analyzeBook(bookId, 5) }, modifier = Modifier.fillMaxWidth()) { Text("继续分析 5 章") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.cancelAnalysis(bookId) }, modifier = Modifier.fillMaxWidth()) { Text("取消当前分析") }
                    }
                }
                BookFeaturePage.PLOT -> items(brain.nodes, key = { it.id }) { node ->
                    InfoCard(node.title, "第 ${node.startChapterIndex + 1} 章 · ${node.summary}")
                }
                BookFeaturePage.CHARACTERS -> items(brain.characters, key = { it.id }) { character ->
                    InfoCard(character.canonicalName, character.personality.ifBlank { "尚无人物描述" })
                }
                BookFeaturePage.RELATIONS -> items(brain.relations, key = { it.id }) { relation ->
                    val from = brain.characters.firstOrNull { it.id == relation.fromCharacterId }?.canonicalName ?: "未知"
                    val to = brain.characters.firstOrNull { it.id == relation.toCharacterId }?.canonicalName ?: "未知"
                    InfoCard("$from → $to", "${relation.relationType} · ${relation.evidence}")
                }
                BookFeaturePage.LOCATIONS -> {
                    val locations = brain.nodes.mapNotNull { it.locationName?.trim() }.filter(String::isNotBlank).distinct()
                    if (locations.isEmpty()) item { InfoCard("尚无地点", "完成故事分析后将在这里汇总") }
                    else items(locations) { location -> InfoCard(location, "剧情节点中的地点") }
                }
                BookFeaturePage.TTS -> {
                    item { InfoCard("即时朗读", "没有精品缓存时自动使用系统中文 TTS；不会调用 LLM 或付费服务") }
                    items(detail.chapters, key = { it.id }) { chapter ->
                        Card {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text(chapter.title, fontWeight = FontWeight.SemiBold)
                                Text(TaskStatus.fromStorage(chapter.ttsStatus).labelZh(), style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onReadChapter(chapter.id) }) { Text("打开") }
                                    Button(onClick = { viewModel.generateChapterTts(bookId, chapter.id) }) { Text("生成精品配音") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (onClick != null) Card(onClick = onClick, content = { content() }) else Card(content = { content() })
}

private fun TaskStatus.labelZh() = when (this) {
    TaskStatus.PENDING -> "未生成"
    TaskStatus.QUEUED -> "排队中"
    TaskStatus.RUNNING -> "生成中"
    TaskStatus.COMPLETED -> "已有精品缓存"
    TaskStatus.FAILED -> "失败，可重试"
    TaskStatus.CANCELLED -> "已取消"
}

@Composable
internal fun GeneratedTitleCover(title: String, modifier: Modifier = Modifier) {
    val palettes = listOf(0xFF263846, 0xFF4B3028, 0xFF293D34, 0xFF3C304C)
    val color = androidx.compose.ui.graphics.Color(palettes[(title.hashCode() and Int.MAX_VALUE) % palettes.size])
    Surface(modifier = modifier, color = color, shape = MaterialTheme.shapes.medium) {
        Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            Text(title.ifBlank { "章境" }, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, maxLines = 4)
        }
    }
}

@Composable
internal fun BookCover(book: BookEntity?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val path = book?.coverPath
    val bitmap = remember(path) {
        path?.let { File(context.filesDir, it) }
            ?.takeIf { it.isFile }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }
    if (bitmap == null) {
        GeneratedTitleCover(book?.title.orEmpty(), modifier)
    } else {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "${book?.title.orEmpty()} 封面",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}
