package com.storybrain.app.ui

import com.storybrain.app.data.BookEntity
import com.storybrain.app.data.BookTtsSettingEntity
import com.storybrain.app.data.ChapterEntity
import com.storybrain.app.data.CharacterVoiceBindingEntity
import com.storybrain.app.data.MemoryItemEntity
import com.storybrain.app.data.PlotNodeEntity
import com.storybrain.app.data.StoryCharacterEntity
import com.storybrain.app.data.StoryRelationEntity
import com.storybrain.app.data.TtsProfileVoicePoolEntity
import com.storybrain.app.data.TtsProviderProfileEntity
import com.storybrain.app.settings.TtsGlobalConfig

data class BookDetailUiState(
    val book: BookEntity? = null,
    val chapters: List<ChapterEntity> = emptyList(),
    val ttsProfiles: List<TtsProviderProfileEntity> = emptyList(),
    val globalTts: TtsGlobalConfig = TtsGlobalConfig(),
    val bookTts: BookTtsSettingEntity? = null
)

data class ReaderUiState(
    val chapter: ChapterEntity? = null,
    val chapters: List<ChapterEntity> = emptyList(),
    val characters: List<StoryCharacterEntity> = emptyList()
)

data class StoryBrainUiState(
    val characters: List<StoryCharacterEntity> = emptyList(),
    val relations: List<StoryRelationEntity> = emptyList(),
    val nodes: List<PlotNodeEntity> = emptyList(),
    val memoryCount: Int = 0,
    val ttsConfig: TtsGlobalConfig = TtsGlobalConfig(),
    val ttsProfiles: List<TtsProviderProfileEntity> = emptyList(),
    val bookTts: BookTtsSettingEntity? = null,
    val activeBindings: List<CharacterVoiceBindingEntity> = emptyList(),
    val edgeVoices: List<TtsProfileVoicePoolEntity> = emptyList(),
    val fishVoices: List<TtsProfileVoicePoolEntity> = emptyList(),
    val compatibleVoices: List<TtsProfileVoicePoolEntity> = emptyList()
)

data class MemoryCenterUiState(
    val memories: List<MemoryItemEntity> = emptyList(),
    val characters: List<StoryCharacterEntity> = emptyList()
)

internal data class StoryBrainCore(
    val characters: List<StoryCharacterEntity>,
    val relations: List<StoryRelationEntity>,
    val nodes: List<PlotNodeEntity>,
    val memoryCount: Int
)

internal data class StoryBrainTts(
    val config: TtsGlobalConfig,
    val profiles: List<TtsProviderProfileEntity>,
    val bookSetting: BookTtsSettingEntity?,
    val bindings: List<CharacterVoiceBindingEntity>
)

internal data class StoryBrainVoicePools(
    val edge: List<TtsProfileVoicePoolEntity>,
    val fish: List<TtsProfileVoicePoolEntity>,
    val compatible: List<TtsProfileVoicePoolEntity>
)
