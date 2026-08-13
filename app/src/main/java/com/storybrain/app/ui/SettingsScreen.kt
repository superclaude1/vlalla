@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.storybrain.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storybrain.app.BuildConfig
import com.storybrain.app.data.TtsProviderKind
import com.storybrain.app.data.TtsVoiceRole
import com.storybrain.app.settings.SettingsViewModel

enum class SettingsPage { LLM_CONNECTION, LLM_MODEL, TTS_CONFIG, VOICES_POOL, ABOUT }

@Composable
fun MyScreen(onOpenLlm: () -> Unit, onOpenTts: () -> Unit, onOpenVoices: () -> Unit, onOpenRunLog: () -> Unit, onOpenAbout: () -> Unit) {
    Scaffold(topBar = { TopAppBar(modifier = Modifier.height(ReactReferenceContract.topBarHeightDp.dp), title = { Text("我的") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingRow(NavigationArchitecture.myRows[0], Icons.Rounded.Psychology, onOpenLlm)
            SettingRow(NavigationArchitecture.myRows[1], Icons.Rounded.GraphicEq, onOpenTts)
            SettingRow(NavigationArchitecture.myRows[2], Icons.Rounded.RecordVoiceOver, onOpenVoices)
            SettingRow(NavigationArchitecture.myRows[3], Icons.Rounded.Info, onOpenRunLog)
            SettingRow(NavigationArchitecture.myRows[4], Icons.Rounded.Info, onOpenAbout)
        }
    }
}

@Composable
fun LlmSettingsHubScreen(onBack: () -> Unit, onOpenConnection: () -> Unit, onOpenModel: () -> Unit) {
    SettingsScaffold("LLM", onBack) {
        SettingRow("连接", Icons.Rounded.Key, onOpenConnection)
        SettingRow("模型", Icons.Rounded.Psychology, onOpenModel)
    }
}

@Composable
fun TtsServiceListScreen(onBack: () -> Unit, onOpenService: (String) -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { BackBar("配音服务", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.profiles, key = { it.id }) { profile ->
                SettingRow(if (profile.id == state.globalProfileId) "${profile.displayName} · 全局" else profile.displayName, Icons.Rounded.GraphicEq) { onOpenService(profile.id) }
            }
        }
    }
}

@Composable
fun VoiceLibraryListScreen(onBack: () -> Unit, onOpenPool: (String) -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { BackBar("音色库", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.profiles, key = { it.id }) { profile -> SettingRow(profile.displayName, Icons.Rounded.RecordVoiceOver) { onOpenPool(profile.id) } }
        }
    }
}

@Composable
fun SettingsScreen(page: SettingsPage, onBack: () -> Unit, profileId: String? = null, viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profile = state.profiles.firstOrNull { it.id == state.selectedProfileId }
    val profileReady = profileId == null || state.loadedProfileId == profileId
    var showKey by remember { mutableStateOf(false) }
    var modelsOpen by remember { mutableStateOf(false) }
    var ttsModelsOpen by remember { mutableStateOf(false) }
    var pendingDeleteApiId by remember { mutableStateOf<String?>(null) }
    var manualVoiceId by remember(profileId) { mutableStateOf("") }
    var manualVoiceName by remember(profileId) { mutableStateOf("") }
    LaunchedEffect(profileId) { profileId?.let(viewModel::selectProfile) }
    pendingDeleteApiId?.let { apiId ->
        val api = state.llmProfiles.firstOrNull { it.id == apiId }
        AlertDialog(
            onDismissRequest = { pendingDeleteApiId = null },
            title = { Text("确认删除 API") },
            text = { Text("将删除 ${api?.displayName ?: "此 API"} 的模型、配置和本机密钥。") },
            confirmButton = { TextButton({ viewModel.deleteLlmProfile(apiId); pendingDeleteApiId = null }) { Text("删除") } },
            dismissButton = { TextButton({ pendingDeleteApiId = null }) { Text("取消") } }
        )
    }
    val title = when (page) {
        SettingsPage.LLM_CONNECTION -> "LLM 连接"
        SettingsPage.LLM_MODEL -> "LLM 模型"
        SettingsPage.TTS_CONFIG -> profile?.displayName ?: "服务配置"
        SettingsPage.VOICES_POOL -> "${profile?.displayName ?: ""}音色"
        SettingsPage.ABOUT -> "关于"
    }
    Scaffold(topBar = { BackBar(title, onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (page) {
                SettingsPage.LLM_CONNECTION -> {
                    items(state.llmProfiles, key = { it.id }) { api ->
                        Card(
                            onClick = { viewModel.selectLlmProfile(api.id) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Key, null)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(api.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(api.baseUrl, style = MaterialTheme.typography.bodySmall)
                                }
                                if (api.id == state.selectedApiProfileId) Text("当前")
                            }
                        }
                    }
                    item { SettingsCard {
                    OutlinedTextField(state.apiDisplayName, viewModel::updateApiDisplayName, Modifier.fillMaxWidth(), label = { Text("API 名称") }, singleLine = true)
                    OutlinedTextField(state.baseUrl, viewModel::updateBaseUrl, Modifier.fillMaxWidth(), label = { Text("API Base URL") }, singleLine = true)
                    Text(if (state.hasStoredKey) "已通过 Android Keystore 安全保存" else "尚未保存 API Key", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(state.apiKeyDraft, viewModel::updateApiKey, Modifier.fillMaxWidth(), label = { Text("API Key") }, visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { TextButton({ showKey = !showKey }) { Text(if (showKey) "隐藏" else "显示") } }, singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(viewModel::detectModels, Modifier.weight(1f), enabled = state.selectedApiProfileId.isNotBlank() && !state.detecting) { Text("检测") }; Button(viewModel::save, Modifier.weight(1f), enabled = state.selectedApiProfileId.isNotBlank()) { Text("保存") } }
                    OutlinedButton(viewModel::addLlmProfile, Modifier.fillMaxWidth()) { Text("保存为新增 API") }
                    OutlinedButton(viewModel::clearApiKey, Modifier.fillMaxWidth(), enabled = state.selectedApiProfileId.isNotBlank() && (state.hasStoredKey || state.apiKeyDraft.isNotBlank())) { Text("清除 API Key") }
                    OutlinedButton({ pendingDeleteApiId = state.selectedApiProfileId }, Modifier.fillMaxWidth(), enabled = state.selectedApiProfileId.isNotBlank()) { Text("删除此 API") }
                    StateMessage(state.message, state.isError)
                    } }
                }
                SettingsPage.LLM_MODEL -> item { SettingsCard {
                    OutlinedTextField(state.selectedModel, {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text("模型") }, trailingIcon = { IconButton({ modelsOpen = true }) { Icon(Icons.Rounded.ChevronRight, null) } })
                    DropdownMenu(modelsOpen, { modelsOpen = false }) {
                        state.llmModelGroups.forEach { group ->
                            DropdownMenuItem({ Text(group.profile.displayName, style = MaterialTheme.typography.labelLarge) }, {}, enabled = false)
                            group.models.forEach { model ->
                                DropdownMenuItem(
                                    { Text(model.identity.modelId) },
                                    { viewModel.selectModel(model.identity); modelsOpen = false }
                                )
                            }
                        }
                    }
                    Button(viewModel::save, Modifier.fillMaxWidth(), enabled = state.selectedApiProfileId.isNotBlank()) { Text("保存") }
                    StateMessage(state.message, state.isError)
                } }
                SettingsPage.TTS_CONFIG -> item { SettingsCard {
                    if (!profileReady) {
                        Text(if (state.profileLoading) "正在加载目标服务…" else state.ttsMessage ?: "目标服务尚未加载")
                    } else {
                        if (profile?.kind != TtsProviderKind.EDGE.name && profile?.kind != TtsProviderKind.ANDROID_SYSTEM.name) {
                            Text(if (state.ttsHasStoredKey) "已通过 Android Keystore 安全保存" else "尚未保存 API Key", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(state.profileBaseUrl, viewModel::updateProfileBaseUrl, Modifier.fillMaxWidth(), enabled = profileReady, label = { Text("API Base URL") }, singleLine = true)
                            OutlinedTextField(state.ttsApiKeyDraft, viewModel::updateTtsApiKey, Modifier.fillMaxWidth(), enabled = profileReady, label = { Text("API Key") }, visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { TextButton({ showKey = !showKey }) { Text(if (showKey) "隐藏" else "显示") } }, singleLine = true)
                            OutlinedButton(viewModel::clearTtsApiKey, Modifier.fillMaxWidth(), enabled = profileReady && (state.ttsHasStoredKey || state.ttsApiKeyDraft.isNotBlank())) { Text("清除 API Key") }
                        }
                        if (profile?.kind == TtsProviderKind.FISH_AUDIO.name) {
                            OutlinedTextField(
                                state.profileModel, {}, Modifier.fillMaxWidth(), enabled = profileReady,
                                readOnly = true, label = { Text("Fish 模型") },
                                trailingIcon = { IconButton({ ttsModelsOpen = true }) { Icon(Icons.Rounded.ChevronRight, null) } }
                            )
                            DropdownMenu(ttsModelsOpen, { ttsModelsOpen = false }) {
                                state.ttsModels.forEach { model -> DropdownMenuItem({ Text(model) }, { viewModel.updateProfileModel(model); ttsModelsOpen = false }) }
                            }
                        } else if (profile?.kind != TtsProviderKind.ANDROID_SYSTEM.name) {
                            OutlinedTextField(state.profileModel, viewModel::updateProfileModel, Modifier.fillMaxWidth(), enabled = profileReady, label = { Text("模型") }, singleLine = true)
                        }
                        if (profile?.kind == TtsProviderKind.OPENAI_COMPATIBLE.name) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(state.profileSupportsInstructions, viewModel::setSupportsInstructions, enabled = profileReady); Text("支持 instructions 演绎参数") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(viewModel::detectTtsService, Modifier.weight(1f), enabled = profileReady && !state.ttsDetecting) { Text("检测") }
                            Button(viewModel::saveTts, Modifier.weight(1f), enabled = profileReady) { Text("保存") }
                        }
                        OutlinedButton({ viewModel.selectGlobalProfile(state.selectedProfileId) }, Modifier.fillMaxWidth(), enabled = profileReady) { Text("设为全局服务") }
                    }
                    StateMessage(state.ttsMessage, state.ttsIsError)
                } }
                SettingsPage.VOICES_POOL -> {
                    if (!profileReady) {
                        item { Text(if (state.profileLoading) "正在加载目标音色池…" else state.ttsMessage ?: "目标音色池尚未加载") }
                    } else {
                        if (state.voicePool.isEmpty()) item { Text("暂无音色") }
                        items(state.voicePool, key = { "${it.profileId}-${it.voiceId}-${it.role}" }) { voice ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(voice.voiceName); Text("${roleLabel(voice.role)} · ${voice.voiceId}", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        if (profile?.kind == TtsProviderKind.FISH_AUDIO.name) {
                            item {
                                SettingsCard {
                                    Text("Fish Audio 音色", style = MaterialTheme.typography.titleMedium)
                                    OutlinedTextField(state.voiceQuery, viewModel::updateVoiceQuery, Modifier.fillMaxWidth(), label = { Text("音色名称") }, singleLine = true)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton({ viewModel.searchFishVoices(true) }, Modifier.weight(1f), enabled = !state.ttsDetecting) { Text("我的音色") }
                                        OutlinedButton({ viewModel.searchFishVoices(false) }, Modifier.weight(1f), enabled = !state.ttsDetecting) { Text("公开搜索") }
                                    }
                                    StateMessage(state.ttsMessage, state.ttsIsError)
                                }
                            }
                            items(state.voices, key = { it.id }) { voice ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text(voice.name, style = MaterialTheme.typography.titleMedium)
                                        Text(listOf(voice.gender, voice.source).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                            TtsVoiceRole.entries.forEach { role -> TextButton({ viewModel.addVoiceToPool(voice, role) }) { Text("加入${roleLabel(role.name)}") } }
                                        }
                                    }
                                }
                            }
                        }
                        if (profile?.kind == TtsProviderKind.OPENAI_COMPATIBLE.name) {
                            item {
                                SettingsCard {
                                    Text("手工添加音色", style = MaterialTheme.typography.titleMedium)
                                    OutlinedTextField(manualVoiceId, { manualVoiceId = it }, Modifier.fillMaxWidth(), label = { Text("Voice ID") }, singleLine = true)
                                    OutlinedTextField(manualVoiceName, { manualVoiceName = it }, Modifier.fillMaxWidth(), label = { Text("显示名称") }, singleLine = true)
                                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                        TtsVoiceRole.entries.forEach { role -> TextButton({ viewModel.addManualVoice(manualVoiceId, manualVoiceName, role) }) { Text("加入${roleLabel(role.name)}") } }
                                    }
                                    StateMessage(state.ttsMessage, state.ttsIsError)
                                }
                            }
                        }
                    }
                }
                SettingsPage.ABOUT -> item { Card { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text("章境 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"); Text("API Key 使用 Android Keystore 加密保存在本机。", style = MaterialTheme.typography.bodySmall) } } }
            }
        }
    }
}

@Composable
fun AudioEngineScreen(bookId: String, viewModel: AppViewModel, onBack: () -> Unit) {
    val profiles by viewModel.ttsProfiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val setting by viewModel.bookTtsSetting(bookId).collectAsStateWithLifecycle(initialValue = null)
    Scaffold(topBar = { BackBar("引擎", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SettingEngineRow("跟随全局", setting?.primaryProfileId == null) { viewModel.setBookPrimaryProfile(bookId, null) } }
            items(profiles, key = { it.id }) { profile -> SettingEngineRow(profile.displayName, setting?.primaryProfileId == profile.id) { viewModel.setBookPrimaryProfile(bookId, profile.id) } }
        }
    }
}

@Composable private fun SettingEngineRow(title: String, selected: Boolean, onClick: () -> Unit) { Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected, onClick); Spacer(Modifier.width(12.dp)); Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium) } } }
@Composable private fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) { Scaffold(topBar = { BackBar(title, onBack) }) { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) } }
@Composable private fun BackBar(title: String, onBack: () -> Unit) = TopAppBar(modifier = Modifier.height(ReactReferenceContract.topBarHeightDp.dp), title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } })
@Composable private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) = Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
@Composable private fun SettingRow(title: String, icon: ImageVector, onClick: () -> Unit) { Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.width(16.dp)); Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); Icon(Icons.Rounded.ChevronRight, null) } } }
@Composable private fun StateMessage(message: String?, error: Boolean) { message?.let { Text(it, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) } }
private fun roleLabel(role: String) = when (role) { "NARRATOR" -> "旁白"; "MALE" -> "男性"; "FEMALE" -> "女性"; else -> "通用" }
