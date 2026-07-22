package com.storybrain.app.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.storybrain.app.StoryBrainApplication
import com.storybrain.app.analysis.LlmStoryAnalyzer
import com.storybrain.app.analysis.CharacterChatService
import com.storybrain.app.data.TaskStatus
import com.storybrain.app.data.ChatSessionEntity
import com.storybrain.app.data.MemoryItemEntity
import com.storybrain.app.data.MemoryType
import com.storybrain.app.data.MemoryWithSelection
import com.storybrain.app.data.BookTtsSettingEntity
import com.storybrain.app.data.CharacterVoiceBindingEntity
import com.storybrain.app.data.TtsProfileVoicePoolEntity
import com.storybrain.app.importer.ImportedNovel
import com.storybrain.app.importer.NovelStreamImporter
import com.storybrain.app.export.Neo4jExporter
import com.storybrain.app.settings.LlmSettingsStore
import com.storybrain.app.settings.TtsSettingsStore
import com.storybrain.app.tts.ChapterAudioPlayer
import com.storybrain.app.tts.ChapterTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportUiState(
    val loading: Boolean = false,
    val sourceName: String = "",
    val title: String = "",
    val novel: ImportedNovel? = null,
    val error: String? = null
)

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
    private val analyzer = LlmStoryAnalyzer(repository, LlmSettingsStore(application))
    private val characterChat = CharacterChatService(repository, LlmSettingsStore(application))
    private val ttsSettings = TtsSettingsStore(application)
    private val ttsEngine = ChapterTtsEngine(application, repository, ttsSettings)
    private val audioPlayer = ChapterAudioPlayer()
    private val _importState = MutableStateFlow(ImportUiState())
    val importState = _importState.asStateFlow()
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

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureDefaultTtsProfiles()
            runCatching { ttsEngine.recoverAndCleanup(repository.getAllChapterIds().toSet()) }
            repository.getBooks().forEach { book -> runCatching { repository.backfillAnalysisMemories(book.id) } }
        }
    }

    val books = repository.observeBooks()
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

    fun loadNovel(uri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportUiState(loading = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val sourceName = resolver.query(uri, null, null, null, null)?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                    } ?: "导入小说.txt"
                    val title = sourceName.substringBeforeLast('.').ifBlank { "未命名小说" }
                    val novel = resolver.openInputStream(uri)?.use { input ->
                        NovelStreamImporter.parse(input, title)
                    } ?: error("无法读取该文件")
                    require(novel.chapters.isNotEmpty()) { "文件中没有可读取的正文" }
                    ImportUiState(
                        sourceName = sourceName,
                        title = title,
                        novel = novel
                    )
                }
            }.onSuccess { _importState.value = it }
                .onFailure { _importState.value = ImportUiState(error = it.message ?: "导入失败") }
        }
    }

    fun updateImportTitle(title: String) {
        _importState.value = _importState.value.copy(title = title)
    }

    fun confirmImport(onComplete: (String) -> Unit) {
        val state = _importState.value
        val novel = state.novel ?: return
        viewModelScope.launch {
            _importState.value = state.copy(loading = true)
            runCatching { repository.saveImportedNovel(novel, state.sourceName, state.title) }
                .onSuccess { id ->
                    _importState.value = ImportUiState()
                    onComplete(id)
                }
                .onFailure { _importState.value = state.copy(loading = false, error = it.message) }
        }
    }

    fun cancelImport() { _importState.value = ImportUiState() }

    fun markReading(bookId: String, chapterIndex: Int) {
        viewModelScope.launch { repository.updateReadingProgress(bookId, chapterIndex) }
    }

    fun generateChapterTts(bookId: String, chapterId: String) {
        if (_ttsState.value.running) return
        viewModelScope.launch {
            audioPlayer.stop()
            repository.updateTtsStatus(chapterId, TaskStatus.QUEUED)
            _ttsState.value = TtsUiState(chapterId = chapterId, running = true, progress = "准备章节文本…")
            runCatching {
                withContext(Dispatchers.IO) {
                    ttsEngine.generate(
                        bookId,
                        chapterId,
                        onProgress = { completed, total ->
                            _ttsState.value = _ttsState.value.copy(progress = "正在生成 $completed/$total 段")
                        },
                        onStage = { stage -> _ttsState.value = _ttsState.value.copy(progress = stage) }
                    )
                }
            }.onSuccess { result ->
                _ttsState.value = TtsUiState(
                    chapterId = chapterId,
                    message = "本章配音已生成（${result.segmentCount} 段）"
                )
            }.onFailure { error ->
                _ttsState.value = TtsUiState(
                    chapterId = chapterId,
                    message = error.message ?: "章节配音失败",
                    isError = true
                )
            }
        }
    }

    fun playChapterTts(chapterId: String, manifestPath: String) {
        audioPlayer.play(manifestPath) { playing, error ->
            _ttsState.value = TtsUiState(
                chapterId = chapterId,
                playing = playing,
                message = error ?: if (playing) "正在播放本章配音" else "播放完成",
                isError = error != null
            )
        }
    }

    fun stopChapterTts() {
        audioPlayer.stop()
        _ttsState.value = _ttsState.value.copy(playing = false, message = "已停止播放")
    }

    fun analyzeBook(bookId: String, chapterCount: Int? = null) {
        if (_analysisState.value.running) return
        viewModelScope.launch {
            _analysisState.value = AnalysisUiState(bookId = bookId, running = true, message = "正在调用 LLM 分析…")
            runCatching { withContext(Dispatchers.IO) { analyzer.analyzeNext(bookId, chapterCount) } }
                .onSuccess { result ->
                    _analysisState.value = AnalysisUiState(bookId = bookId, message = result.message)
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

    fun deleteBook(bookId: String, onComplete: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                val chapterIds = repository.getChapters(bookId).map { it.id }
                audioPlayer.stop()
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
                        memoryPreferences.edit().putBoolean(seededKey, true).apply()
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

    override fun onCleared() {
        audioPlayer.stop()
        super.onCleared()
    }
}
