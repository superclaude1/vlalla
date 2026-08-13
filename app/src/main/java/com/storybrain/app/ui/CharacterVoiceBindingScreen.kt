@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.storybrain.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.storybrain.app.data.TtsProfileIds

/** Dedicated leaf: character chat and voice binding only; no story graph tabs or metrics. */
@Composable
fun CharacterVoiceBindingScreen(
    bookId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onChatCharacter: (String) -> Unit
) {
    val characters by viewModel.characters(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val bindings by viewModel.activeVoiceBindings(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val narratorBinding by viewModel.activeNarratorBinding(bookId).collectAsStateWithLifecycle(initialValue = null)
    val profiles by viewModel.ttsProfiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val setting by viewModel.bookTtsSetting(bookId).collectAsStateWithLifecycle(initialValue = null)
    val edgeVoices by viewModel.ttsVoicePool(TtsProfileIds.EDGE).collectAsStateWithLifecycle(initialValue = emptyList())
    val fishVoices by viewModel.ttsVoicePool(TtsProfileIds.FISH).collectAsStateWithLifecycle(initialValue = emptyList())
    val compatibleVoices by viewModel.ttsVoicePool(TtsProfileIds.OPENAI).collectAsStateWithLifecycle(initialValue = emptyList())
    val voices = edgeVoices + fishVoices + compatibleVoices
    Scaffold(topBar = { TopAppBar(title = { Text("角色音色") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                var narratorMenuOpen by remember(bookId) { mutableStateOf(false) }
                val narratorVoices = voices.sortedByDescending { it.profileId == setting?.primaryProfileId }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("旁白音色", fontWeight = FontWeight.Bold)
                        Text("单独指定本书旁白；未指定时跟随本书引擎", style = MaterialTheme.typography.bodySmall)
                        Text(narratorBinding?.let { "${profiles.firstOrNull { p -> p.id == it.profileId }?.displayName ?: "服务"} · ${it.voiceName}" } ?: "跟随本书引擎", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { viewModel.clearNarratorVoice(bookId) }) { Text("跟随本书引擎") }
                            Box {
                                OutlinedButton(onClick = { narratorMenuOpen = true }, enabled = narratorVoices.isNotEmpty()) { Text("选择旁白") }
                                DropdownMenu(expanded = narratorMenuOpen, onDismissRequest = { narratorMenuOpen = false }) {
                                    narratorVoices.forEach { voice ->
                                        DropdownMenuItem(
                                            text = { Text("${profiles.firstOrNull { it.id == voice.profileId }?.displayName ?: "服务"} · ${voice.voiceName}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            onClick = { viewModel.assignNarratorVoice(bookId, voice.profileId, voice.voiceId, voice.voiceName); narratorMenuOpen = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (characters.isEmpty()) item { Text("等待故事分析") }
            items(characters, key = { it.id }) { character ->
                var menuOpen by remember(character.id) { mutableStateOf(false) }
                val binding = bindings.firstOrNull { it.characterId == character.id }
                val sortedVoices = remember(character, voices) { voices.sortedByDescending { characterVoiceScore(character.gender, character.personality, it.gender, it.tagsJson) } }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(character.canonicalName, fontWeight = FontWeight.Bold)
                        Text(binding?.let { "${profiles.firstOrNull { p -> p.id == it.profileId }?.displayName ?: "服务"} · ${it.voiceName}" } ?: "跟随本书引擎", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onChatCharacter(character.id) }) { Text("角色对话") }
                            Box {
                                OutlinedButton(
                                    onClick = { menuOpen = true },
                                    enabled = VoiceBindingMenuPolicy.enabled(binding != null, sortedVoices.size)
                                ) { Text("选择音色") }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("跟随本书引擎") }, onClick = { viewModel.clearCharacterVoice(character.id); menuOpen = false })
                                    sortedVoices.forEach { voice -> DropdownMenuItem(text = { Text("${profiles.firstOrNull { it.id == voice.profileId }?.displayName ?: "服务"} · ${voice.voiceName}", maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { viewModel.assignCharacterVoice(character.id, voice.profileId, voice.voiceId, voice.voiceName); menuOpen = false }) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun characterVoiceScore(gender: String, personality: String, voiceGender: String, tagsJson: String): Int {
    val base = if (gender == voiceGender) 5 else 0
    val normalizedPersonality = personality.lowercase()
    return base + normalizedPersonality.split(" ").count { tag -> tag.length > 1 && tagsJson.lowercase().contains(tag) }
}
