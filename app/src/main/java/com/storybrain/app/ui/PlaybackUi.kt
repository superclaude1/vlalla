package com.storybrain.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.storybrain.app.data.SleepTimerMode
import com.storybrain.app.playback.PlaybackUiState

@Composable
fun MiniPlayerBar(state: PlaybackUiState, viewModel: PlaybackViewModel, onExpand: () -> Unit) {
    if (!state.hasMedia) return
    Surface(shadowElevation = 8.dp, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onExpand)) {
            if (state.chapterDurationMs > 0) {
                LinearProgressIndicator(
                    progress = { state.chapterPositionMs.toFloat().div(state.chapterDurationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.chapterTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.bookTitle, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { if (state.isPlaying) viewModel.pause() else viewModel.play() }) {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "暂停" else "播放")
                }
                IconButton(onClick = viewModel::nextChapter) { Icon(Icons.Rounded.SkipNext, "下一章") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedPlayerSheet(state: PlaybackUiState, viewModel: PlaybackViewModel, onDismiss: () -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember(state.chapterPositionMs) { mutableFloatStateOf(state.chapterPositionMs.toFloat()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.chapterTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(state.bookTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
            }
            Slider(
                value = if (dragging) dragPosition else state.chapterPositionMs.toFloat(),
                onValueChange = { dragging = true; dragPosition = it },
                onValueChangeFinished = {
                    dragging = false
                    viewModel.seekToChapterPosition(dragPosition.toLong())
                },
                valueRange = 0f..state.chapterDurationMs.coerceAtLeast(1).toFloat(),
                enabled = state.chapterDurationMs > 0
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(if (dragging) dragPosition.toLong() else state.chapterPositionMs))
                Text(formatDuration(state.chapterDurationMs))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previousChapter) { Icon(Icons.Rounded.SkipPrevious, "上一章") }
                IconButton(onClick = { if (state.isPlaying) viewModel.pause() else viewModel.play() }) {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "暂停" else "播放")
                }
                IconButton(onClick = viewModel::nextChapter) { Icon(Icons.Rounded.SkipNext, "下一章") }
            }
            Text("播放速度", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                    FilterChip(
                        selected = state.speed == speed,
                        onClick = { viewModel.setSpeed(speed) },
                        label = { Text("${speed}x") }
                    )
                }
            }
            Text("睡眠定时", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { minutes ->
                    AssistChip(
                        onClick = { viewModel.setSleepTimer(SleepTimerMode.MINUTES, minutes) },
                        label = { Text("$minutes 分") },
                        leadingIcon = { Icon(Icons.Rounded.Timer, null) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.sleepTimerMode == SleepTimerMode.END_OF_CHAPTER,
                    onClick = { viewModel.setSleepTimer(SleepTimerMode.END_OF_CHAPTER) },
                    label = { Text("本章结束") }
                )
                FilterChip(
                    selected = state.sleepTimerMode == SleepTimerMode.OFF,
                    onClick = { viewModel.setSleepTimer(SleepTimerMode.OFF) },
                    label = { Text("关闭定时") }
                )
            }
            if (state.sleepTimerMode == SleepTimerMode.MINUTES) {
                Text("定时将在 ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(state.sleepTimerEndAt))} 停止")
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}

private fun formatDuration(value: Long): String {
    val seconds = value.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
