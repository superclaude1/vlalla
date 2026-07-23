package com.storybrain.app.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.storybrain.app.data.TaskRecordEntity
import com.storybrain.app.data.TaskStatus
import com.storybrain.app.data.TaskType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    viewModel: TaskViewModel,
    onOpenBook: (String) -> Unit,
    onOpenReader: (String, String) -> Unit,
    onOpenTask: (String) -> Unit
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<TaskType?>(null) }
    val visible = remember(tasks, filter) { tasks.filter { filter == null || it.type == filter?.name } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("任务", fontWeight = FontWeight.Bold); Text("进行中与近 7 天", style = MaterialTheme.typography.labelSmall) } },
                actions = { TextButton(onClick = viewModel::clearFinished) { Text("清除已完成") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter == null, { filter = null }, label = { Text("全部") })
                    TaskType.entries.forEach { type ->
                        FilterChip(filter == type, { filter = type }, label = { Text(type.label()) })
                    }
                }
            }
            if (visible.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 80.dp), contentAlignment = Alignment.Center) {
                        Text("暂无任务记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(visible, key = { it.workName }) { task ->
                TaskCard(
                    task = task,
                    onCancel = { viewModel.cancel(task) },
                    onRetry = { viewModel.retry(task) },
                    onDetails = { onOpenTask(task.workName) },
                    onOpen = {
                        task.chapterId?.let { onOpenReader(task.bookId, it) } ?: onOpenBook(task.bookId)
                    }
                )
            }
        }
    }
}

@Composable
private fun TaskCard(task: TaskRecordEntity, onCancel: () -> Unit, onRetry: () -> Unit, onOpen: () -> Unit, onDetails: () -> Unit) {
    val status = TaskStatus.fromStorage(task.status)
    val active = status == TaskStatus.QUEUED || status == TaskStatus.RUNNING
    Card(onClick = onDetails) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (status) {
                        TaskStatus.COMPLETED -> Icons.Rounded.CheckCircle
                        TaskStatus.FAILED -> Icons.Rounded.Error
                        TaskStatus.CANCELLED -> Icons.Rounded.Cancel
                        else -> Icons.Rounded.Schedule
                    },
                    null,
                    tint = when (status) {
                        TaskStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        TaskStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.secondary
                    }
                )
                Spacer(Modifier.padding(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${TaskType.fromStorage(task.type)?.label() ?: "未知任务"} · ${status.label()}", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (active) {
                if (task.total > 0) {
                    LinearProgressIndicator(
                        progress = { task.completed.toFloat().div(task.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("${task.completed}/${task.total} · ${task.stage}", style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(task.stage, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消") }
            } else {
                if (task.stage.isNotBlank()) Text(task.stage, style = MaterialTheme.typography.bodySmall)
                task.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("打开") }
                    if (status == TaskStatus.FAILED || status == TaskStatus.CANCELLED) {
                        Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Refresh, null)
                            Text("重试")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    workName: String,
    viewModel: TaskViewModel,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenReader: (String, String) -> Unit
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val task = tasks.firstOrNull { it.workName == workName }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("任务详情") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        if (task == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("任务记录不存在或已清理") }
        } else {
            val status = TaskStatus.fromStorage(task.status)
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(task.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${TaskType.fromStorage(task.type)?.label() ?: "未知"} · ${status.label()}")
                if (task.total > 0) {
                    LinearProgressIndicator(progress = { task.completed.toFloat().div(task.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("${task.completed}/${task.total}")
                }
                Text(task.stage.ifBlank { "暂无阶段信息" })
                task.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (status == TaskStatus.QUEUED || status == TaskStatus.RUNNING) {
                    OutlinedButton(onClick = { viewModel.cancel(task) }, modifier = Modifier.fillMaxWidth()) { Text("取消任务") }
                }
                if (status == TaskStatus.FAILED || status == TaskStatus.CANCELLED) {
                    Button(onClick = { viewModel.retry(task) }, modifier = Modifier.fillMaxWidth()) { Text("重试") }
                }
                OutlinedButton(
                    onClick = { task.chapterId?.let { onOpenReader(task.bookId, it) } ?: onOpenBook(task.bookId) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("打开对应内容") }
            }
        }
    }
}

private fun TaskType.label() = when (this) {
    TaskType.ANALYSIS -> "分析"
    TaskType.TTS -> "配音"
    TaskType.SEARCH_INDEX -> "索引"
}

private fun TaskStatus.label() = when (this) {
    TaskStatus.PENDING -> "待处理"
    TaskStatus.QUEUED -> "排队中"
    TaskStatus.RUNNING -> "进行中"
    TaskStatus.COMPLETED -> "已完成"
    TaskStatus.FAILED -> "失败"
    TaskStatus.CANCELLED -> "已取消"
}
