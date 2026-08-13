@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.storybrain.app.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storybrain.app.data.TaskEventEntity

@Composable
fun TaskRunLogScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val events by viewModel.taskEvents.collectAsStateWithLifecycle(initialValue = emptyList())
    val clipboard = LocalClipboardManager.current
    Scaffold(topBar = { TopAppBar(title = { Text("运行日志") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("事件列表（ERROR / USAGE）")
            Button(onClick = viewModel::clearTaskEvents, modifier = Modifier.fillMaxWidth()) { Text("清空") }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events, key = { it.id }) { event ->
                    TaskEventCard(event) { clipboard.setText(AnnotatedString(event.message)) }
                }
            }
        }
    }
}

@Composable
private fun TaskEventCard(event: TaskEventEntity, onCopy: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${event.eventType} · ${event.taskType} · ${event.stage}")
            Text(event.message)
            Text("状态码：${event.statusCode ?: "无"} · 尝试：${event.attempt}")
            if (event.eventType == "USAGE") {
                Text("质量：${event.usageQuality ?: "MISSING"}")
                Text("Tokens：prompt=${event.promptTokens ?: "无"} · completion=${event.completionTokens ?: "无"} · total=${event.totalTokens ?: "无"}")
                Text("Request ID：${event.requestId ?: "无"}")
                Text("响应模型：${event.responseModel ?: "无"}")
            }
            Button(onClick = onCopy) { Text("复制") }
        }
    }
}
