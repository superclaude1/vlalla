package com.storybrain.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storybrain.app.BuildConfig
import com.storybrain.app.ui.theme.AppThemeMode
import com.storybrain.app.ui.theme.AppThemeStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onAppearance: () -> Unit,
    onReading: () -> Unit,
    onLlm: () -> Unit,
    onTts: () -> Unit,
    onData: () -> Unit,
    onDiagnostics: () -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text("设置", fontWeight = FontWeight.Bold) }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SettingsLink("外观与主题", "深色、浅色或跟随系统", Icons.Rounded.ColorLens, onAppearance) }
            item { SettingsLink("阅读默认值", "字号、行距、边距与自动跟随", Icons.Rounded.AutoStories, onReading) }
            item { SettingsLink("LLM 服务", "分析与角色对话接口", Icons.Rounded.Psychology, onLlm) }
            item { SettingsLink("配音服务", "真实负载试听、音色与精品缓存", Icons.Rounded.GraphicEq, onTts) }
            item { SettingsLink("数据与备份", "用户内容与缓存边界", Icons.Rounded.Backup, onData) }
            item { SettingsLink("诊断信息", "版本、任务与本地日志", Icons.Rounded.MonitorHeart, onDiagnostics) }
        }
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { AppThemeStore(context) }
    val mode by store.mode.collectAsStateWithLifecycle(initialValue = AppThemeMode.DARK)
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { SettingsPageTopBar("外观与主题", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("应用主题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            AppThemeMode.entries.forEach { option ->
                FilterChip(
                    selected = mode == option,
                    onClick = { scope.launch { store.save(option) } },
                    label = { Text(when (option) { AppThemeMode.DARK -> "深色（默认）"; AppThemeMode.LIGHT -> "浅色"; AppThemeMode.SYSTEM -> "跟随系统" }) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text("深色主题使用近黑背景、蓝灰层次和橙色强调色。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

enum class InformationSettingsPage { DATA, DIAGNOSTICS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformationSettingsScreen(page: InformationSettingsPage, onBack: () -> Unit) {
    val title = if (page == InformationSettingsPage.DATA) "数据与备份" else "诊断信息"
    Scaffold(topBar = { SettingsPageTopBar(title, onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page == InformationSettingsPage.DATA) {
                Text("备份范围", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("保留书籍数据库、自定义封面、书签、高亮、批注和记忆。")
                Text("排除 API Key、临时文件、系统朗读缓存和精品配音缓存。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("章境 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontWeight = FontWeight.Bold)
                Text("诊断日志仅保存在本机；未接入外部崩溃或行为分析服务。")
                Text("Edge 阶段会分别报告连接服务、等待首帧、接收音频和保存文件。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPageTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }
    )
}
