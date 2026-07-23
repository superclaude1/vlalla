package com.storybrain.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storybrain.app.data.ReaderTheme
import com.storybrain.app.data.ReadingMarkType
import com.storybrain.app.data.ReadingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderContentsScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpenChapter: (String, Int) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenNotes: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { ReaderPageTopBar("目录", onBack) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenBookmarks, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Bookmark, null); Spacer(Modifier.width(6.dp)); Text("书签")
                    }
                    OutlinedButton(onClick = onOpenNotes, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.EditNote, null); Spacer(Modifier.width(6.dp)); Text("批注")
                    }
                }
            }
            items(state.chapters, key = { it.id }) { chapter ->
                Card(onClick = { onOpenChapter(chapter.id, 0) }) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${chapter.chapterIndex + 1}", Modifier.width(38.dp), color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(chapter.title, fontWeight = if (chapter.id == state.chapter?.id) FontWeight.Bold else FontWeight.Normal)
                            Text("${chapter.charCount} 字", style = MaterialTheme.typography.labelSmall)
                        }
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderMarksScreen(
    viewModel: ReaderViewModel,
    type: ReadingMarkType,
    onBack: () -> Unit,
    onOpen: (String, Int) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val marks = state.marks.filter { ReadingMarkType.fromStorage(it.type) == type }
    Scaffold(topBar = { ReaderPageTopBar(if (type == ReadingMarkType.BOOKMARK) "书签" else "批注", onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (marks.isEmpty()) item { Text("暂无${if (type == ReadingMarkType.BOOKMARK) "书签" else "批注"}") }
            items(marks, key = { it.id }) { mark ->
                Card(onClick = { onOpen(mark.chapterId, mark.startOffset) }) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(state.chapters.firstOrNull { it.id == mark.chapterId }?.title ?: "章节", fontWeight = FontWeight.Bold)
                        Text(mark.excerpt, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        if (mark.note.isNotBlank()) Text(mark.note, color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(onClick = { viewModel.deleteMark(mark.id) }) { Text("删除") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSearchScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpen: (String, Int) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = { ReaderPageTopBar("全文搜索", onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::search,
                    label = { Text("搜索本书") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.searching || state.searchIndexing) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(state.searchIndexStage.ifBlank { "正在搜索" }, style = MaterialTheme.typography.labelSmall)
                }
            }
            items(state.searchResults, key = { "${it.chapterId}:${it.sourceOffset}" }) { hit ->
                Card(onClick = { onOpen(hit.chapterId, hit.sourceOffset) }) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("第 ${hit.chapterIndex + 1} 章 · ${hit.chapterTitle}", fontWeight = FontWeight.Bold)
                        Text(hit.excerpt, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

enum class ReaderPreferencePage { APPEARANCE, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderPreferenceScreen(viewModel: ReaderViewModel, page: ReaderPreferencePage, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state.preferences
    var draft by remember(current) { mutableStateOf(current) }
    Scaffold(topBar = { ReaderPageTopBar(if (page == ReaderPreferencePage.APPEARANCE) "阅读界面" else "阅读设置", onBack) }) { padding ->
        val value = draft
        if (value == null) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (page == ReaderPreferencePage.APPEARANCE) {
                Text("主题", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderTheme.entries.forEach { theme ->
                        FilterChip(
                            selected = value.theme == theme,
                            onClick = { draft = value.copy(theme = theme) },
                            label = { Text(when (theme) { ReaderTheme.PAPER -> "纸白"; ReaderTheme.SEPIA -> "羊皮纸"; ReaderTheme.NIGHT -> "夜间" }) }
                        )
                    }
                }
                PreferenceSlider("字号 ${value.fontSizeSp.toInt()}", value.fontSizeSp, 14f..30f) { draft = value.copy(fontSizeSp = it) }
                PreferenceSlider("行距 ${"%.1f".format(value.lineHeightMultiplier)}", value.lineHeightMultiplier, 1.2f..2.2f) { draft = value.copy(lineHeightMultiplier = it) }
                SwitchRow("衬线字体", value.serifFont) { draft = value.copy(serifFont = it) }
            } else {
                Text("阅读模式", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(value.mode == ReadingMode.CHAT, { draft = value.copy(mode = ReadingMode.CHAT) }, label = { Text("聊天阅读") })
                    FilterChip(value.mode == ReadingMode.ORIGINAL, { draft = value.copy(mode = ReadingMode.ORIGINAL) }, label = { Text("原文阅读") })
                }
                PreferenceSlider("段距 ${value.paragraphSpacingDp.toInt()}", value.paragraphSpacingDp, 0f..24f) { draft = value.copy(paragraphSpacingDp = it) }
                PreferenceSlider("左右边距 ${value.horizontalPaddingDp}", value.horizontalPaddingDp.toFloat(), 12f..40f) { draft = value.copy(horizontalPaddingDp = it.toInt()) }
                SwitchRow("朗读自动跟随", value.autoFollowAudio) { draft = value.copy(autoFollowAudio = it) }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = viewModel::resetStyle, modifier = Modifier.fillMaxWidth()) { Text("恢复全局设置") }
            Button(onClick = { viewModel.saveStyle(value); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
        }
    }
}

@Composable
private fun PreferenceSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderPageTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }
    )
}
