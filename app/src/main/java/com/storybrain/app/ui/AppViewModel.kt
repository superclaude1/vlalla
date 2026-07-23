package com.storybrain.app.ui

import android.app.Application
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.storybrain.app.StoryBrainApplication
import com.storybrain.app.analysis.CharacterChatService
import com.storybrain.app.data.ChatSessionEntity
import com.storybrain.app.data.MemoryItemEntity
import com.storybrain.app.data.MemoryType
import com.storybrain.app.data.MemoryWithSelection
import com.storybrain.app.data.BookTtsSettingEntity
import com.storybrain.app.data.BookCoverStore
import com.storybrain.app.data.CharacterVoiceBindingEntity
import com.storybrain.app.data.TtsProfileVoicePoolEntity
import com.storybrain.app.export.Neo4jExporter
import com.storybrain.app.settings.LlmSettingsStore
import com.storybrain.app.settings.TtsSettingsStore
import com.storybrain.app.tts.ChapterTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AnalysisUiState(
    val bookId: String? = null,
    val running: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

data class CharacterChatUiState(
    val characterId: String? = null,
    val running: Boolean = false,
    val error: String? = null
)

data class TtsUiState(
    val chapterId: String? = null,
    val running: Boolean = false,
    val progress: String? = null,
    val playing: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

data class ExportUiState(val running: Boolean = false, val message: String? = null, val isError: Boolean = false)

enum class MemorySelectionScope { DEFAULT, SESSION }

data class MemoryPickerUiState(
    val bookId: String? = null,
    val characterId: String? = null,
    val sessionId: String? = null,
    val query: String = "",
    val loading: Boolean = false,
    val items: List<MemoryWithSelection> = emptyList(),
    val suggestions: List<MemoryWithSelection> = emptyList(),
    val message: String? = null,
    val isError: Boolean = false
)

data class MemoryActionUiState(val running: Boolean = false, val message: String? = null, val isError: Boolean = false)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as StoryBrainApplication).repository
    private val characterChat = CharacterChatService(repository, LlmSettingsStore(application))
    private val ttsSettings = TtsSettingsStore(application)
    private val ttsEngine = ChapterTtsEngine(application, repository, ttsSettings)
    private val longTasks = (application as StoryBrainApplication).longTaskScheduler
    private val playback = (application as StoryBrainApplication).playbackRepository
    private val coverStore = BookCoverStore(application, repository)
    private val _analysisState = MutableStateFlow(AnalysisUiState())
    val analysisState = _analysisState.asStateFlow()
    private val _characterChatState = MutableStateFlow(CharacterChatUiState())
    val characterChatState = _characterChatState.asStateFlow()
    private val _ttsState = MutableStateFlow(TtsUiState())
    val ttsState = _ttsState.asStateFlow()
    private val _exportState = MutableStateFlow(ExportUiState())
    val exportState = _exportState.asStateFlow()
    private val _memoryPickerState = MutableStateFlow(MemoryPickerUiState())
    val memoryPickerState = _memoryPickerState.asStateFlow()
    private val _memoryActionState = MutableStateFlow(MemoryActionUiState())
    val memoryActionState = _memoryActionState.asStateFlow()
    private val memoryPreferences = application.getSharedPreferences("memory_library_v3", Application.MODE_PRIVATE)
    private val bookDetailStates = mutableMapOf<String, StateFlow<BookDetailUiState>>()
    private val readerStates = mutableMapOf<String, StateFlow<ReaderUiState>>()
    private val storyBrainStates = mutableMapOf<String, StateFlow<StoryBrainUiState>>()
    private val memoryCenterStates = mutableMapOf<String, StateFlow<MemoryCenterUiState>>()

    fun book(bookId: String) = repository.observeBook(bookId)
    fun chapters(bookId: String) = repository.observeChapters(bookId)
    fun chapter(chapterId: String) = repository.observeChapter(chapterId)
    fun characters(bookId: String) = repository.observeCharacters(bookId)
    fun relations(bookId: String) = repository.observeRelations(bookId)
    fun plotNodes(bookId: String) = repository.observePlotNodes(bookId)
    fun chatMessages(sessionId: String) = repository.observeChatMessages(sessionId)
    fun chatSessions(characterId: String) = repository.observeChatSessions(characterId)
    fun memories(bookId: String) = repository.observeMemories(bookId)
    fun memoryCount(bookId: String) = repository.observeMemoryCount(bookId)
    val ttsConfig = ttsSettings.config
    val ttsProfiles = repository.observeTtsProfiles()
    fun ttsVoicePool(profileId: String) = repository.observeTtsVoicePool(profileId)
    fun bookTtsSetting(bookId: String) = repository.observeBookTtsSetting(bookId)
    fun activeVoiceBindings(bookId: String) = repository.observeActiveCharacterVoiceBindings(bookId)

    fun bookDetail(bookId: String): StateFlow<BookDetailUiState> = synchronized(bookDetailStates) {
        bookDetailStates.getOrPut(bookId) {
            combine(
                repository.observeBook(bookId),
                repository.observeChapters(bookId),
                repository.observeTtsProfiles(),
                ttsSettings.config,
                repository.observeBookTtsSetting(bookId)
            ) { book, chapters, profiles, global, setting ->
                BookDetailUiState(book, chapters, profiles, global, setting)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookDetailUiState())
        }
    }

    fun readerDetail(bookId: String, chapterId: String): StateFlow<ReaderUiState> = synchronized(readerStates) {
        readerStates.getOrPut("$bookId:$chapterId") {
            combine(
                repository.observeChapter(chapterId),
                repository.observeChapters(bookId),
                repository.observeCharacters(bookId)
            ) { chapter, chapters, characters -> ReaderUiState(chapter, chapters, characters) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState())
        }
    }

    fun storyBrainDetail(bookId: String): StateFlow<StoryBrainUiState> = synchronized(storyBrainStates) {
        storyBrainStates.getOrPut(bookId) {
            val core = combine(
                repository.observeCharacters(bookId),
                repository.observeRelations(bookId),
                repository.observePlotNodes(bookId),
                repository.observeMemoryCount(bookId)
            ) { characters, relations, nodes, count -> StoryBrainCore(characters, relations, nodes, count) }
            val tts = combine(
                ttsSettings.config,
                repository.observeTtsProfiles(),
                repository.observeBookTtsSetting(bookId),
                repository.observeActiveCharacterVoiceBindings(bookId)
            ) { config, profiles, setting, bindings -> StoryBrainTts(config, profiles, setting, bindings) }
            val pools = combine(
                repository.observeTtsVoicePool(com.storybrain.app.data.TtsProfileIds.EDGE),
                repository.observeTtsVoicePool(com.storybrain.app.data.TtsProfileIds.FISH),
                repository.observeTtsVoicePool(com.storybrain.app.data.TtsProfileIds.OPENAI)
            ) { edge, fish, compatible -> StoryBrainVoicePools(edge, fish, compatible) }
            combine(core, tts, pools) { brain, audio, voices ->
                StoryBrainUiState(
                    brain.characters, brain.relations, brain.nodes, brain.memoryCount,
                    audio.config, audio.profiles, audio.bookSetting, audio.bindings,
                    voices.edge, voices.fish, voices.compatible
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryBrainUiState())
        }
    }

    fun memoryCenterDetail(bookId: String): StateFlow<MemoryCenterUiState> = synchronized(memoryCenterStates) {
        memoryCenterStates.getOrPut(bookId) {
            combine(repository.observeMemories(bookId), repository.observeCharacters(bookId)) { memories, characters ->
                MemoryCenterUiState(memories, characters)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoryCenterUiState())
        }
    }

    fun setBookPrimaryProfile(bookId: String, profileId: String?) {
        viewModelScope.launch { repository.setBookPrimaryProfile(bookId, profileId) }
    }

    fun assignCharacterVoice(
        characterId: String,
        profileId: String,
        voiceId: String,
        voiceName: String
    ) {
        viewModelScope.launch {
            repository.setCharacterVoiceBinding(
                CharacterVoiceBindingEntity(characterId, profileId, voiceId, voiceName)
            )
        }
    }

    fun clearCharacterVoice(characterId: String) {
        viewModelScope.launch { repository.clearCharacterVoiceBinding(characterId) }
    }

    fun assignCharacterVoice(characterId: String, voiceId: String) {
        viewModelScope.launch { repository.updateCharacterVoice(characterId, voiceId) }
    }

    fun markReading(bookId: String, chapterIndex: Int) {
        viewModelScope.launch { repository.updateReadingProgress(bookId, chapterIndex) }
    }

    fun importBookCover(bookId: String, uri: Uri, onResult: (String?) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { coverStore.import(bookId, uri) }
                .onSuccess { onResult(null) }
                .onFailure { onResult(it.message ?: "封面导入失败") }
        }
    }

    fun restoreDefaultCover(bookId: String, currentPath: String?, onResult: (String?) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { coverStore.restoreDefault(bookId, currentPath) }
                .onSuccess { onResult(null) }
                .onFailure { onResult(it.message ?: "恢复默认封面失败") }
        }
    }

    fun generateChapterTts(bookId: String, chapterId: String) {
        viewModelScope.launch {
            playback.pause()
            runCatching { longTasks.enqueueTts(bookId, chapterId) }
                .onSuccess {
                    _ttsState.value = TtsUiState(chapterId = chapterId, message = "配音任务已加入后台队列")
                }.onFailure { error ->
                _ttsState.value = TtsUiState(
                    chapterId = chapterId,
                    message = error.message ?: "章节配音失败",
                    isError = true
                )
                }
        }
    }

    fun cancelChapterTts(chapterId: String) {
        viewModelScope.launch {
            longTasks.cancelTts(chapterId)
            _ttsState.value = TtsUiState(chapterId = chapterId, message = "已取消配音任务")
        }
    }

    fun playChapterTts(chapterId: String, @Suppress("UNUSED_PARAMETER") manifestPath: String) {
        viewModelScope.launch {
            val chapter = repository.getChapter(chapterId)
            val result = chapter?.let { playback.playChapter(it.bookId, it.id) }
                ?: Result.failure(IllegalArgumentException("找不到章节"))
            _ttsState.value = TtsUiState(
                chapterId = chapterId,
                playing = result.isSuccess,
                message = result.exceptionOrNull()?.message ?: "正在播放本章配音",
                isError = result.isFailure
            )
        }
    }

    fun stopChapterTts() {
        playback.pause()
        _ttsState.value = _ttsState.value.copy(playing = false, message = "已停止播放")
    }

    fun analyzeBook(bookId: String, chapterCount: Int? = null) {
        viewModelScope.launch {
            runCatching { longTasks.enqueueAnalysis(bookId, chapterCount) }
                .onSuccess { queued ->
                    _analysisState.value = AnalysisUiState(
                        bookId = bookId,
                        message = if (queued) "分析任务已加入后台队列" else "全书已经分析完成"
                    )
                }
                .onFailure { error ->
                    _analysisState.value = AnalysisUiState(
                        bookId = bookId,
                        message = error.message ?: "LLM 分析失败",
                        isError = true
                    )
                }
        }
    }

    fun cancelAnalysis(bookId: String) {
        viewModelScope.launch {
            longTasks.cancelAnalysis(bookId)
            _analysisState.value = AnalysisUiState(bookId = bookId, message = "已取消分析任务")
        }
    }

    fun deleteBook(bookId: String, onComplete: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                val chapterIds = repository.getChapters(bookId).map { it.id }
                playback.stopIfBook(bookId)
                longTasks.cancelBook(bookId, chapterIds)
                coverStore.restoreDefault(bookId, repository.getBook(bookId)?.coverPath)
                repository.deleteBook(bookId)
                withContext(Dispatchers.IO) { ttsEngine.deleteAudio(chapterIds) }
            }.onSuccess {
                _ttsState.value = TtsUiState()
                onComplete()
            }.onFailure { error ->
                onError(error.message ?: "删除小说失败")
            }
        }
    }

    fun ensureChatSession(bookId: String, characterId: String, onReady: (String, Boolean) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.backfillAnalysisMemories(bookId)
                    val seededKey = "defaults_seeded_$characterId"
                    var seededNow = false
                    if (!memoryPreferences.getBoolean(seededKey, false)) {
                        repository.seedCharacterDefaults(bookId, characterId)
                        memoryPreferences.edit { putBoolean(seededKey, true) }
                        seededNow = true
                    }
                    repository.getOrCreateSession(bookId, characterId).id to seededNow
                }
            }.onSuccess { (sessionId, seededNow) -> onReady(sessionId, seededNow) }.onFailure { error ->
                _characterChatState.value = CharacterChatUiState(characterId, error = error.message ?: "无法创建对话")
            }
        }
    }

    fun createChatSession(bookId: String, characterId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.createSession(bookId, characterId) }
                .onSuccess { onReady(it.id) }
                .onFailure { _characterChatState.value = CharacterChatUiState(characterId, error = it.message) }
        }
    }

    fun renameChatSession(sessionId: String, title: String) {
        viewModelScope.launch { repository.renameSession(sessionId, title) }
    }

    fun deleteChatSession(sessionId: String, bookId: String, characterId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            onReady(repository.getOrCreateSession(bookId, characterId).id)
        }
    }

    fun sendCharacterMessage(
        bookId: String,
        characterId: String,
        sessionId: String,
        text: String,
        onSent: () -> Unit = {}
    ) {
        if (_characterChatState.value.running || text.isBlank()) return
        viewModelScope.launch {
            _characterChatState.value = CharacterChatUiState(characterId = characterId, running = true)
            runCatching {
                withContext(Dispatchers.IO) { characterChat.send(bookId, characterId, sessionId, text) }
            }.onSuccess {
                _characterChatState.value = CharacterChatUiState(characterId = characterId)
                onSent()
            }.onFailure { error ->
                _characterChatState.value = CharacterChatUiState(
                    characterId = characterId,
                    error = error.message ?: "角色对话失败"
                )
            }
        }
    }

    fun clearCharacterChat(sessionId: String) {
        viewModelScope.launch { repository.clearChatMessages(sessionId) }
    }

    fun loadMemoryPicker(
        bookId: String,
        characterId: String,
        sessionId: String,
        query: String = _memoryPickerState.value.query,
        suggestionText: String = ""
    ) {
        viewModelScope.launch {
            _memoryPickerState.value = _memoryPickerState.value.copy(
                bookId = bookId,
                characterId = characterId,
                sessionId = sessionId,
                query = query,
                loading = true,
                message = null
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    val book = repository.getBook(bookId) ?: error("找不到这本小说")
                    val items = repository.memoriesWithSelection(bookId, characterId, sessionId, book.analysisCompleted, query)
                    val suggestions = if (suggestionText.isBlank()) emptyList() else {
                        repository.suggestMemories(bookId, characterId, sessionId, book.analysisCompleted, suggestionText)
                    }
                    items to suggestions
                }
            }.onSuccess { (items, suggestions) ->
                _memoryPickerState.value = _memoryPickerState.value.copy(
                    loading = false,
                    items = items,
                    suggestions = suggestions,
                    isError = false
                )
            }.onFailure { error ->
                _memoryPickerState.value = _memoryPickerState.value.copy(
                    loading = false,
                    message = error.message ?: "记忆加载失败",
                    isError = true
                )
            }
        }
    }

    fun setMemorySelected(memoryId: String, scope: MemorySelectionScope, selected: Boolean) {
        val state = _memoryPickerState.value
        val characterId = state.characterId ?: return
        val sessionId = state.sessionId ?: return
        viewModelScope.launch {
            runCatching {
                when (scope) {
                    MemorySelectionScope.DEFAULT -> repository.setDefaultMemory(characterId, memoryId, selected)
                    MemorySelectionScope.SESSION -> repository.setSessionMemory(characterId, sessionId, memoryId, selected)
                }
            }.onSuccess {
                loadMemoryPicker(state.bookId.orEmpty(), characterId, sessionId, state.query)
            }.onFailure { error ->
                _memoryPickerState.value = _memoryPickerState.value.copy(
                    message = error.message ?: "记忆选择失败",
                    isError = true
                )
            }
        }
    }

    fun saveNewMemory(
        bookId: String,
        type: MemoryType,
        title: String,
        content: String,
        chapterIndex: Int? = null,
        characterIds: List<String> = emptyList(),
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _memoryActionState.value = MemoryActionUiState(running = true)
            runCatching {
                repository.createMemory(bookId, type, title, content, chapterIndex, chapterIndex, characterIds)
            }.onSuccess {
                _memoryActionState.value = MemoryActionUiState(message = "记忆已保存")
                onComplete()
            }.onFailure { error ->
                _memoryActionState.value = MemoryActionUiState(message = error.message ?: "保存记忆失败", isError = true)
            }
        }
    }

    fun updateMemory(memory: MemoryItemEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.updateMemory(memory) }
                .onSuccess { _memoryActionState.value = MemoryActionUiState(message = "记忆已更新"); onComplete() }
                .onFailure { _memoryActionState.value = MemoryActionUiState(message = it.message, isError = true) }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteMemory(memoryId) }
                .onSuccess { _memoryActionState.value = MemoryActionUiState(message = "记忆已删除") }
                .onFailure { _memoryActionState.value = MemoryActionUiState(message = it.message, isError = true) }
        }
    }

    fun prepareNeo4jExport(bookId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            _exportState.value = ExportUiState(running = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val book = repository.getBook(bookId) ?: error("找不到这本小说")
                    Neo4jExporter.export(
                        book,
                        repository.getCharacters(bookId),
                        repository.getRelations(bookId),
                        repository.getPlotNodes(bookId)
                    )
                }
            }.onSuccess { content ->
                _exportState.value = ExportUiState()
                onReady(content)
            }.onFailure { error ->
                _exportState.value = ExportUiState(message = error.message ?: "Neo4j 导出失败", isError = true)
            }
        }
    }

    fun writeNeo4jExport(uri: Uri, content: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入导出文件")
                }
            }.onSuccess {
                _exportState.value = ExportUiState(message = "Neo4j Cypher 已导出")
            }.onFailure { error ->
                _exportState.value = ExportUiState(message = error.message ?: "导出文件写入失败", isError = true)
            }
        }
    }

}
