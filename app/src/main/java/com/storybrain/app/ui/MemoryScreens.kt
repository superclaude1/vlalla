@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storybrain.app.data.MemoryItemEntity
import com.storybrain.app.data.MemorySearch
import com.storybrain.app.data.MemoryType
import com.storybrain.app.data.MemoryWithSelection
import com.storybrain.app.data.StoryCharacterEntity
import com.storybrain.app.data.StoryRepository

@Composable
fun MemoryCenterScreen(bookId: String, viewModel: AppViewModel, onBack: () -> Unit) {
    val memories by viewModel.memories(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val characters by viewModel.characters(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val action by viewModel.memoryActionState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<MemoryItemEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val filtered = remember(memories, query, type) {
        memories.filter { memory ->
            (type == null || memory.type == type) &&
                (query.isBlank() || memory.title.contains(query, true) || memory.content.contains(query, true))
        }
    }
    if (adding) {
        MemoryEditorDialog(
            title = "新建手工记忆",
            initialTitle = "",
            initialContent = "",
            onDismiss = { adding = false },
            onSave = { titleText, content ->
                viewModel.saveNewMemory(bookId, MemoryType.NOTE, titleText, content) { adding = false }
            }
        )
    }
    editing?.let { memory ->
        MemoryEditorDialog(
            title = "编辑记忆",
            initialTitle = memory.title,
            initialContent = memory.content,
            onDismiss = { editing = null },
            onDelete = if (memory.editable) ({ viewModel.deleteMemory(memory.id); editing = null }) else null,
            onSave = { titleText, content ->
                viewModel.updateMemory(memory.copy(title = titleText, content = content)) { editing = null }
            }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("记忆库"); Text("${memories.size} 条本地记忆", style = MaterialTheme.typography.labelSmall) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, "新建记忆") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                label = { Text("搜索标题或内容") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            MemoryTypeFilters(type) { type = it }
            action.message?.let {
                Text(
                    it,
                    color = if (action.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (filtered.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.Bookmarks, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(if (memories.isEmpty()) "分析小说或新建笔记后，记忆会出现在这里" else "没有匹配的记忆")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { memory ->
                        MemoryCard(memory, characters, onClick = { if (memory.editable) editing = memory })
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryPickerSheet(
    bookId: String,
    characterId: String,
    sessionId: String,
    suggestionText: String,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.memoryPickerState.collectAsStateWithLifecycle()
    val stateMatches = state.bookId == bookId && state.characterId == characterId && state.sessionId == sessionId
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyCharacter by rememberSaveable { mutableStateOf(false) }
    var chapterText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(bookId, characterId, sessionId) {
        viewModel.loadMemoryPicker(bookId, characterId, sessionId, "", suggestionText)
    }
    LaunchedEffect(query) {
        viewModel.loadMemoryPicker(bookId, characterId, sessionId, query, suggestionText)
    }
    val filtered = if (stateMatches) state.items.filter { memory ->
        (type == null || memory.type == type) &&
            (!onlyCharacter || characterId in MemorySearch.jsonStrings(memory.characterIdsJson)) &&
            (chapterText.toIntOrNull()?.let { limit -> (memory.chapterEndIndex ?: -1) < limit } ?: true)
    } else emptyList()
    val selected = if (stateMatches) state.items.filter { !it.isLocked && (it.isDefault || it.isSession) }.distinctBy { it.id } else emptyList()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("选择对话记忆", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                "已选 ${selected.size}/${StoryRepository.MAX_SELECTED_MEMORIES} 条 · ${selected.sumOf { it.content.length }}/${StoryRepository.MAX_MEMORY_CHARS} 字",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                label = { Text("本地搜索记忆") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            MemoryTypeFilters(type) { type = it }
            FilterChip(
                selected = onlyCharacter,
                onClick = { onlyCharacter = !onlyCharacter },
                label = { Text("仅当前角色") },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            OutlinedTextField(
                value = chapterText,
                onValueChange = { chapterText = it.filter(Char::isDigit).take(5) },
                label = { Text("截至章节（空为全部已分析章节）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            state.message.takeIf { stateMatches }?.let {
                Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 4.dp))
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(460.dp),
                contentPadding = PaddingValues(16.dp, 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (stateMatches && state.suggestions.isNotEmpty() && query.isBlank()) {
                    item { Text("根据当前输入推荐（不会自动使用）", fontWeight = FontWeight.Bold) }
                    items(state.suggestions, key = { "suggest-${it.id}" }) { memory ->
                        MemorySelectionCard(
                            memory = memory,
                            onDefaultChange = { if (!state.loading) viewModel.setMemorySelected(memory.id, MemorySelectionScope.DEFAULT, it) },
                            onSessionChange = { if (!state.loading) viewModel.setMemorySelected(memory.id, MemorySelectionScope.SESSION, it) }
                        )
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
                }
                item { Text("全部可用记忆", fontWeight = FontWeight.Bold) }
                items(filtered, key = { it.id }) { memory ->
                    MemorySelectionCard(
                        memory = memory,
                        onDefaultChange = { if (!state.loading) viewModel.setMemorySelected(memory.id, MemorySelectionScope.DEFAULT, it) },
                        onSessionChange = { if (!state.loading) viewModel.setMemorySelected(memory.id, MemorySelectionScope.SESSION, it) }
                    )
                }
                if (filtered.isEmpty()) item { Text("没有匹配的可用记忆", modifier = Modifier.padding(24.dp)) }
            }
        }
    }
}

@Composable
internal fun MemorySelectionCard(
    memory: MemoryWithSelection,
    onDefaultChange: (Boolean) -> Unit,
    onSessionChange: (Boolean) -> Unit
) {
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(memory.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${memoryTypeLabel(memory.type)}${memory.chapterStartIndex?.let { " · 第${it + 1}章" }.orEmpty()}", style = MaterialTheme.typography.labelSmall)
                }
                if (memory.isLocked) Icon(Icons.Rounded.Lock, "未分析章节，已锁定")
                Text("默认", style = MaterialTheme.typography.labelSmall)
                Checkbox(
                    checked = memory.isDefault,
                    enabled = !memory.isLocked,
                    onCheckedChange = onDefaultChange,
                    modifier = Modifier.testTag("memory-default-${memory.id}")
                )
                Text("本次", style = MaterialTheme.typography.labelSmall)
                Checkbox(
                    checked = memory.isSession,
                    enabled = !memory.isDefault && !memory.isLocked,
                    onCheckedChange = onSessionChange,
                    modifier = Modifier.testTag("memory-session-${memory.id}")
                )
            }
            if (memory.isLocked) {
                Text("尚未分析到该章节，记忆已锁定且不会发送给模型", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            Text(memory.content, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MemoryCard(memory: MemoryItemEntity, characters: List<StoryCharacterEntity>, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text(memoryTypeLabel(memory.type)) })
                Spacer(Modifier.width(8.dp))
                Text(memory.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (memory.editable) Icon(Icons.Rounded.Edit, "可编辑")
            }
            Text(memory.content, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            val names = MemorySearch.jsonStrings(memory.characterIdsJson).mapNotNull { id -> characters.firstOrNull { it.id == id }?.canonicalName }
            Text(
                buildString {
                    memory.chapterStartIndex?.let { append("第${it + 1}章") }
                    if (names.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(names.joinToString("、")) }
                }.ifBlank { "全局记忆" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun MemoryTypeFilters(selected: String?, onSelected: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelected(null) },
                label = { Text("全部") },
                modifier = Modifier.testTag("memory-filter-all")
            )
        }
        items(MemoryType.entries) { type ->
            FilterChip(
                selected = selected == type.name,
                onClick = { onSelected(type.name) },
                label = { Text(memoryTypeLabel(type.name)) },
                modifier = Modifier.testTag("memory-filter-${type.name}")
            )
        }
    }
}

@Composable
fun MemoryEditorDialog(
    title: String,
    initialTitle: String,
    initialContent: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var titleText by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    var content by rememberSaveable(initialContent) { mutableStateOf(initialContent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    titleText,
                    { titleText = it.take(60) },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.testTag("memory-editor-title")
                )
                OutlinedTextField(
                    content,
                    { content = it },
                    label = { Text("记忆内容") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.testTag("memory-editor-content")
                )
                Text("${content.length} 字", style = MaterialTheme.typography.labelSmall)
            }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Icon(Icons.Rounded.Delete, null); Text("删除") } }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(titleText, content) },
                enabled = content.isNotBlank(),
                modifier = Modifier.testTag("memory-editor-save")
            ) { Text("保存") }
        }
    )
}

fun memoryTypeLabel(type: String): String = when (type) {
    MemoryType.PLOT.name -> "剧情"
    MemoryType.RELATION.name -> "关系"
    MemoryType.EXCERPT.name -> "原文"
    MemoryType.NOTE.name -> "笔记"
    MemoryType.CHAT.name -> "对话"
    else -> type
}
