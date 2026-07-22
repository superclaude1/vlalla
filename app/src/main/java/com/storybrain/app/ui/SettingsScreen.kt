package com.storybrain.app.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storybrain.app.BuildConfig
import com.storybrain.app.data.TtsProviderKind
import com.storybrain.app.data.TtsVoiceRole
import com.storybrain.app.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenLibrary: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedProfile = state.profiles.firstOrNull { it.id == state.selectedProfileId }
    val selectedKind = selectedProfile?.kind?.let { runCatching { TtsProviderKind.valueOf(it) }.getOrNull() }
    var llmModelsOpen by remember { mutableStateOf(false) }
    var ttsModelsOpen by remember { mutableStateOf(false) }
    var manualVoiceId by remember(state.selectedProfileId) { mutableStateOf("") }
    var manualVoiceName by remember(state.selectedProfileId) { mutableStateOf("") }
    var showLlmKey by remember { mutableStateOf(false) }
    var showTtsKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { IconButton(onClick = onOpenLibrary) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionHeader("LLM 分析服务", "用于小说分析、角色对话和章节演绎标注，支持 OpenAI-compatible 接口。") }
            item {
                Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(state.baseUrl, viewModel::updateBaseUrl, Modifier.fillMaxWidth(), label = { Text("API Base URL") }, singleLine = true)
                    if (state.baseUrl.trim().startsWith("http://", ignoreCase = true)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(state.allowInsecureHttp, viewModel::setAllowInsecureHttp)
                            Text(
                                "允许此配置使用不安全 HTTP（仅限可信局域网）",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    OutlinedTextField(
                        state.apiKeyDraft, viewModel::updateApiKey, Modifier.fillMaxWidth(), label = { Text("API Key") },
                        placeholder = { Text(if (state.hasStoredKey) "已通过 Android Keystore 保存" else "输入 API Key") },
                        visualTransformation = if (showLlmKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton({ showLlmKey = !showLlmKey }) { Text(if (showLlmKey) "隐藏" else "显示") } },
                        leadingIcon = { Icon(Icons.Rounded.Key, null) }, singleLine = true
                    )
                    Box {
                        OutlinedTextField(state.selectedModel, {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text("LLM 模型") }, trailingIcon = { IconButton({ llmModelsOpen = true }) { Icon(Icons.Rounded.ArrowDropDown, null) } })
                        DropdownMenu(llmModelsOpen, { llmModelsOpen = false }) { state.detectedModels.forEach { model -> DropdownMenuItem({ Text(model) }, { viewModel.selectModel(model); llmModelsOpen = false }) } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(viewModel::detectModels, Modifier.weight(1f), enabled = !state.detecting) { Text(if (state.detecting) "检测中…" else "检测模型") }
                        Button(viewModel::save, Modifier.weight(1f)) { Text("保存 LLM") }
                    }
                    StatusText(state.message, state.isError)
                } }
            }

            item { SectionHeader("全局主力引擎", "旁白和未单独设置的角色使用主力引擎；每本小说仍可覆盖。") }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.profiles.forEach { profile ->
                        FilterChip(
                            selected = state.globalProfileId == profile.id,
                            onClick = { viewModel.selectGlobalProfile(profile.id) },
                            label = { Text(profile.displayName) },
                            leadingIcon = { Icon(Icons.Rounded.RecordVoiceOver, null) }
                        )
                    }
                }
            }

            item { SectionHeader("提供商配置", "选择平台后测试连接、配置模型和角色音色池。") }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.profiles.forEach { profile ->
                        FilterChip(state.selectedProfileId == profile.id, { viewModel.selectProfile(profile.id) }, { Text(profile.displayName) })
                    }
                }
            }
            item {
                Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(selectedProfile?.displayName ?: "配音平台", fontWeight = FontWeight.Bold)
                    if (selectedKind != TtsProviderKind.EDGE) {
                        OutlinedTextField(state.profileBaseUrl, viewModel::updateProfileBaseUrl, Modifier.fillMaxWidth(), label = { Text("API Base URL") }, singleLine = true)
                        if (state.profileBaseUrl.trim().startsWith("http://", ignoreCase = true)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(state.profileAllowInsecureHttp, viewModel::setProfileAllowInsecureHttp)
                                Text(
                                    "允许当前提供商使用不安全 HTTP",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        OutlinedTextField(
                            state.ttsApiKeyDraft, viewModel::updateTtsApiKey, Modifier.fillMaxWidth(), label = { Text("API Key") },
                            placeholder = { Text(if (state.ttsHasStoredKey) "已加密保存" else "输入该平台 API Key") },
                            visualTransformation = if (showTtsKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { TextButton({ showTtsKey = !showTtsKey }) { Text(if (showTtsKey) "隐藏" else "显示") } },
                            leadingIcon = { Icon(Icons.Rounded.Key, null) }, singleLine = true
                        )
                    }
                    if (selectedKind == TtsProviderKind.FISH_AUDIO && state.ttsModels.isNotEmpty()) {
                        Box {
                            OutlinedTextField(state.profileModel, {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text("Fish 模型") }, trailingIcon = { IconButton({ ttsModelsOpen = true }) { Icon(Icons.Rounded.ArrowDropDown, null) } })
                            DropdownMenu(ttsModelsOpen, { ttsModelsOpen = false }) { state.ttsModels.forEach { model -> DropdownMenuItem({ Text(model) }, { viewModel.updateProfileModel(model); ttsModelsOpen = false }) } }
                        }
                        if (state.profileModel == "s2.1-pro-free") Text("免费开发模型受 Fair Use 约束，无 SLA，官方可能调整开放时间。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    } else if (selectedKind != TtsProviderKind.EDGE) {
                        OutlinedTextField(state.profileModel, viewModel::updateProfileModel, Modifier.fillMaxWidth(), label = { Text("TTS 模型") }, singleLine = true)
                    }
                    if (selectedKind == TtsProviderKind.OPENAI_COMPATIBLE) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(state.profileSupportsInstructions, viewModel::setSupportsInstructions)
                            Text("服务支持 instructions 演绎参数")
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(viewModel::detectTtsService, Modifier.weight(1f), enabled = !state.ttsDetecting) { Icon(Icons.Rounded.CloudSync, null); Spacer(Modifier.width(6.dp)); Text("连接测试") }
                        Button(viewModel::saveTts, Modifier.weight(1f)) { Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text("保存") }
                    }
                    StatusText(state.ttsMessage, state.ttsIsError)
                } }
            }

            item { SectionHeader("音色池", "主力引擎从旁白、男性、女性和通用音色池稳定分配角色声音。") }
            if (state.voicePool.isEmpty()) {
                item { Card { Text("尚未配置音色池。云端平台作为主力引擎前至少需要旁白和通用音色。", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) } }
            } else {
                items(state.voicePool, key = { "${it.profileId}-${it.voiceId}-${it.role}" }) { voice ->
                    Card { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(voice.voiceName, fontWeight = FontWeight.Medium); Text("${roleLabel(voice.role)} · ${voice.voiceId}", style = MaterialTheme.typography.labelSmall) }
                    } }
                }
            }

            if (selectedKind == TtsProviderKind.FISH_AUDIO) {
                item { SectionHeader("Fish Audio 音色", "同步我的音色或搜索公开库。公开可见不代表拥有发布和商业使用权。") }
                item {
                    Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(state.voiceQuery, viewModel::updateVoiceQuery, Modifier.fillMaxWidth(), label = { Text("音色名称") }, singleLine = true)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ viewModel.searchFishVoices(true) }, Modifier.weight(1f), enabled = !state.ttsDetecting) { Text("我的音色") }
                            OutlinedButton({ viewModel.searchFishVoices(false) }, Modifier.weight(1f), enabled = !state.ttsDetecting) { Text("公开搜索") }
                        }
                    } }
                }
                items(state.voices, key = { it.id }) { voice ->
                    Card { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(voice.name, fontWeight = FontWeight.Bold)
                        Text("${voice.gender} · ${voice.tags.take(4).joinToString()} · ${voice.source}", style = MaterialTheme.typography.labelSmall)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TtsVoiceRole.entries.forEach { role -> TextButton({ viewModel.addVoiceToPool(voice, role) }) { Text("设为${roleLabel(role.name)}") } }
                        }
                    } }
                }
            }

            if (selectedKind == TtsProviderKind.OPENAI_COMPATIBLE) {
                item { SectionHeader("手工添加兼容音色", "兼容接口没有统一音色列表，请填写平台文档提供的 voice ID。") }
                item {
                    Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(manualVoiceId, { manualVoiceId = it }, Modifier.fillMaxWidth(), label = { Text("Voice ID") }, singleLine = true)
                        OutlinedTextField(manualVoiceName, { manualVoiceName = it }, Modifier.fillMaxWidth(), label = { Text("显示名称") }, singleLine = true)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            TtsVoiceRole.entries.forEach { role -> TextButton({ viewModel.addManualVoice(manualVoiceId, manualVoiceName, role) }) { Text("加入${roleLabel(role.name)}") } }
                        }
                    } }
                }
            }

            item {
                Card { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("安全与版本", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("各平台 API Key 分别通过 Android Keystore AES-GCM 加密。失败时不会静默切换声音平台。", style = MaterialTheme.typography.bodySmall)
                    Text("章境 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.labelSmall)
                } }
            }
        }
    }
}

@Composable private fun SectionHeader(title: String, subtitle: String) { Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(subtitle, style = MaterialTheme.typography.bodyMedium) } }
@Composable private fun StatusText(message: String?, isError: Boolean) { message?.let { Text(it, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) } }
private fun roleLabel(role: String) = when (role) { "NARRATOR" -> "旁白"; "MALE" -> "男性"; "FEMALE" -> "女性"; else -> "通用" }
