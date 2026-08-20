package com.storybrain.app.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.storybrain.app.StoryBrainApplication
import com.storybrain.app.analysis.LlmStoryAnalyzer
import com.storybrain.app.analysis.AnalysisFailureException
import com.storybrain.app.analysis.AnalysisRunResult
import com.storybrain.app.analysis.AnalysisProgress
import com.storybrain.app.analysis.CharacterChatService
import com.storybrain.app.data.TaskStatus
import com.storybrain.app.data.TaskRunType
import com.storybrain.app.data.ChatSessionEntity
import com.storybrain.app.data.MemoryItemEntity
import com.storybrain.app.data.MemoryType
import com.storybrain.app.data.MemoryWithSelection
import com.storybrain.app.data.BookTtsSettingEntity
import com.storybrain.app.data.BookNarratorBindingEntity
import com.storybrain.app.data.CharacterVoiceBindingEntity
import com.storybrain.app.data.TtsProfileVoicePoolEntity
import com.storybrain.app.importer.ImportedNovel
import com.storybrain.app.importer.NovelStreamImporter
import com.storybrain.app.export.Neo4jExporter
import com.storybrain.app.reader.ReaderDisplayMode
import com.storybrain.app.reader.ReaderPosition
import com.storybrain.app.reader.ReaderPositionStore
import com.storybrain.app.reader.ReaderPreferencesStore
import com.storybrain.app.data.ReadingPositionEntity
import com.storybrain.app.data.CoverFileLifecycle
import com.storybrain.app.data.CoverGenerationCoordinator
import com.storybrain.app.data.CoverPublication
import com.storybrain.app.data.ExistingBookCover
import com.storybrain.app.data.PollinationsCoverGenerator
import com.storybrain.app.data.ReadingMarkEntity
import com.storybrain.app.data.ReadingMarkType
import com.storybrain.app.data.ReaderTheme
import com.storybrain.app.tts.BlockSpeakController
import com.storybrain.app.tts.VoiceResolver
import java.util.UUID
import com.storybrain.app.settings.LlmSettingsStore
import com.storybrain.app.settings.NetworkFailureClassifier
import com.storybrain.app.settings.RequestStage
import com.storybrain.app.settings.TtsSettingsStore
import com.storybrain.app.settings.UsageQuality
import com.storybrain.app.tts.ChapterAudioPlayer
import com.storybrain.app.tts.ChapterTtsEngine
import com.storybrain.app.tts.EdgeTtsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

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
    val isError: Boolean = false,
    val failureStage: String? = null,
    val failedBatch: Int? = null,
    val totalBatches: Int? = null,
    val retryAttempt: Int = 0,
    val status: AnalysisStatus = AnalysisStatus.IDLE,
    val completedChapters: Int = 0,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val usageQuality: UsageQuality = UsageQuality.MISSING,
    val cost: String = "费用未知"
)

enum class AnalysisStatus { IDLE, RUNNING, SUCCESS, FAILED, SKIPPED, CANCELLED }

fun AnalysisUiState.withProgress(progress: AnalysisProgress): AnalysisUiState = copy(
    completedChapters = progress.completed,
    promptTokens = progress.usage.promptTokens,
    completionTokens = progress.usage.completionTokens,
    totalTokens = progress.usage.totalTokens,
    usageQuality = progress.usage.quality
)

fun AnalysisUiState.asFailure(message: String, failure: AnalysisFailureException?): AnalysisUiState = copy(
    running = false,
    message = message,
    isError = true,
    status = AnalysisStatus.FAILED,
    failureStage = failure?.failure?.stage?.let { stage ->
        when (stage) {
            RequestStage.REQUEST -> "请求"
            RequestStage.RESPONSE -> "响应"
            RequestStage.PARSE -> "解析"
        }
    },
    failedBatch = failure?.failedBatch,
    totalBatches = failure?.totalBatches,
    retryAttempt = failure?.failure?.attempt ?: 0
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

data class ExportUiState(
    val bookId: String? = null,
    val requestId: Long = 0L,
    val running: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

object Neo4jExportRequestPolicy {
    fun canStart(state: ExportUiState): Boolean = !state.running
    fun matches(state: ExportUiState, bookId: String, requestId: Long): Boolean =
        state.bookId == bookId && state.requestId == requestId
}

enum class MemorySelectionScope { DEFAULT, SESSION }

data class MemoryPickerUiState(
    val bookId: String? = null,
    val characterId: String? = null,
    val sessionId: String? = null,
    val query: String = "",
    val requestId: Long = 0L,
    val loading: Boolean = false,
    val items: List<MemoryWithSelection> = emptyList(),
    val suggestions: List<MemoryWithSelection> = emptyList(),
    val message: String? = null,
    val isError: Boolean = false
)

object MemoryPickerRequestPolicy {
    fun matches(state: MemoryPickerUiState, bookId: String, characterId: String, sessionId: String, query: String, requestId: Long): Boolean =
        state.bookId == bookId && state.characterId == characterId && state.sessionId == sessionId &&
            state.query == query && state.requestId == requestId
}

data class MemoryActionUiState(val running: Boolean = false, val message: String? = null, val isError: Boolean = false)

enum class ReaderMode { PLAIN_TEXT, DIALOGUE }

/** 书签跳转目标（书签列表点击后一次性消费）。 */
data class JumpTarget(val bookId: String, val chapterId: String, val sourceOffset: Int)

/** 对话块点读状态（正在朗读的块）。 */
data class BlockSpeakUiState(val chapterId: String, val blockIndex: Int, val playing: Boolean)

private fun ReaderMode.toPreferenceMode(): ReaderDisplayMode = when (this) {
    ReaderMode.PLAIN_TEXT -> ReaderDisplayMode.PLAIN_TEXT
    ReaderMode.DIALOGUE -> ReaderDisplayMode.DIALOGUE
}

private fun ReaderDisplayMode.toReaderMode(): ReaderMode = when (this) {
    ReaderDisplayMode.PLAIN_TEXT -> ReaderMode.PLAIN_TEXT
    ReaderDisplayMode.DIALOGUE -> ReaderMode.DIALOGUE
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as StoryBrainApplication).repository
    private val llmSettings = LlmSettingsStore(application, repository)
    private val analyzer = LlmStoryAnalyzer(repository, llmSettings)
    private val characterChat = CharacterChatService(repository, llmSettings)
    private val ttsSettings = TtsSettingsStore(application)
    private val ttsEngine = ChapterTtsEngine(application, repository, ttsSettings)
    private val audioPlayer = ChapterAudioPlayer()
    private val _importState = MutableStateFlow(ImportUiState())
    val importState = _importState.asStateFlow()
    private var importInProgress = false
    private var deletingBookId: String? = null
    private val _analysisState = MutableStateFlow(AnalysisUiState())
    val analysisState = _analysisState.asStateFlow()
    private var analysisJob: Job? = null
    private val _characterChatState = MutableStateFlow(CharacterChatUiState())
    val characterChatState = _characterChatState.asStateFlow()
    private val _ttsState = MutableStateFlow(TtsUiState())
    val ttsState = _ttsState.asStateFlow()
    private var ttsJob: Job? = null
    private val _exportState = MutableStateFlow(ExportUiState())
    val exportState = _exportState.asStateFlow()
    private var exportRequestSequence = 0L
    private val _memoryPickerState = MutableStateFlow(MemoryPickerUiState())
    val memoryPickerState = _memoryPickerState.asStateFlow()
    private var memoryPickerRequestSequence = 0L
    private val _memoryActionState = MutableStateFlow(MemoryActionUiState())
    val memoryActionState = _memoryActionState.asStateFlow()
    private val readerPreferencesStore = ReaderPreferencesStore(application)
    val readerPreferences = readerPreferencesStore.preferences
    private val readerPositionStore = ReaderPositionStore(application)
    private val _readerMode = MutableStateFlow(readerPreferences.value.displayMode.toReaderMode())
    val readerMode = _readerMode.asStateFlow()
    private val memoryPreferences = application.getSharedPreferences("memory_library_v3", Application.MODE_PRIVATE)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureDefaultTtsProfiles()
            runCatching { ttsEngine.recoverAndCleanup(repository.getAllChapterIds().toSet()) }
            runCatching {
                CoverFileLifecycle.recoverAndCleanup(
                    getApplication<Application>().filesDir,
                    repository.getBooks().mapNotNull { it.coverPath }.toSet()
                )
            }
            repository.getBooks().forEach { book -> runCatching { repository.backfillAnalysisMemories(book.id) } }
        }
    }

    val books = repository.observeBooks()
    fun book(bookId: String) = repository.observeBook(bookId)
    fun chapters(bookId: String) = repository.observeChapters(bookId)
    fun chapter(chapterId: String) = repository.observeChapter(chapterId)
    fun characters(bookId: String) = repository.observeCharacters(bookId)
    fun chapterCharacters(chapterId: String) = repository.observeChapterCharacters(chapterId)
    fun dialogueAnnotations(chapterId: String) = repository.observeDialogueAnnotations(chapterId)
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
    fun activeNarratorBinding(bookId: String) = repository.observeActiveNarratorBinding(bookId)
    val taskEvents = repository.observeTaskEvents()

    fun clearTaskEvents() {
        viewModelScope.launch { repository.clearTaskEvents() }
    }
    fun enqueueAnalysis(bookId: String) {
        val request = OneTimeWorkRequestBuilder<com.storybrain.app.data.AnalysisWorker>()
            .setInputData(workDataOf(com.storybrain.app.data.AnalysisWorker.KEY_BOOK_ID to bookId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(getApplication<Application>()).enqueueUniqueWork(
            "analysis:$bookId",
            ExistingWorkPolicy.KEEP,
            request
        )
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

    fun assignNarratorVoice(bookId: String, profileId: String, voiceId: String, voiceName: String) {
        viewModelScope.launch {
            repository.setNarratorBinding(BookNarratorBindingEntity(bookId, profileId, voiceId, voiceName))
        }
    }

    fun clearNarratorVoice(bookId: String) {
        viewModelScope.launch { repository.clearNarratorBinding(bookId) }
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

    @Synchronized
    fun confirmImport(onComplete: (String) -> Unit) {
        if (importInProgress) return
        val state = _importState.value
        val novel = state.novel ?: return
        importInProgress = true
        _importState.value = state.copy(loading = true)
        viewModelScope.launch {
            runCatching { repository.saveImportedNovel(novel, state.sourceName, state.title) }
                .onSuccess { id ->
                    importInProgress = false
                    _importState.value = ImportUiState()
                    // 导入成功后自动生成封面（稳定 seed，后台执行不阻塞）
                    generateBookCover(id, state.title, regenerate = false)
                    onComplete(id)
                }
                .onFailure { importInProgress = false; _importState.value = state.copy(loading = false, error = it.message) }
        }
    }

    fun cancelImport() { _importState.value = ImportUiState() }

    fun markReading(bookId: String, chapterIndex: Int) {
        viewModelScope.launch { repository.updateReadingProgress(bookId, chapterIndex) }
    }

    fun setReaderMode(mode: ReaderMode) {
        readerPreferencesStore.update { it.withDisplayMode(mode.toPreferenceMode()) }
        _readerMode.value = mode
    }

    fun adjustReaderFontSize(delta: Int) {
        readerPreferencesStore.update { it.adjustFontSize(delta) }
    }

    fun adjustReaderLineHeight(delta: Int) {
        readerPreferencesStore.update { it.adjustLineHeight(delta) }
    }

    fun adjustReaderParagraphSpacing(delta: Int) {
        readerPreferencesStore.update { it.adjustParagraphSpacing(delta) }
    }

    fun adjustReaderHorizontalPadding(delta: Int) {
        readerPreferencesStore.update { it.adjustHorizontalPadding(delta) }
    }

    fun setReaderTheme(theme: ReaderTheme) {
        readerPreferencesStore.update { it.withTheme(theme) }
    }

    fun setReaderSerifFont(enabled: Boolean) {
        readerPreferencesStore.update { it.withSerifFont(enabled) }
    }

    /** 字符偏移进度（分页阅读）。 */
    fun saveReadingOffset(bookId: String, chapterId: String, sourceOffset: Int) {
        viewModelScope.launch {
            repository.upsertReadingPosition(
                ReadingPositionEntity(
                    bookId = bookId,
                    chapterId = chapterId,
                    sourceOffset = sourceOffset,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun observeReadingPosition(bookId: String) = repository.observeReadingPosition(bookId)

    // ---- 书签 / 跳转 ----

    private val _jumpTarget = MutableStateFlow<JumpTarget?>(null)
    val jumpTarget: StateFlow<JumpTarget?> = _jumpTarget

    fun jumpTo(bookId: String, chapterId: String, sourceOffset: Int) {
        _jumpTarget.value = JumpTarget(bookId, chapterId, sourceOffset)
    }

    fun consumeJumpTarget() {
        _jumpTarget.value = null
    }

    fun observeReadingMarks(bookId: String) = repository.observeReadingMarks(bookId)

    fun addBookmark(bookId: String, chapterId: String, startOffset: Int, excerpt: String) {
        viewModelScope.launch {
            repository.upsertReadingMark(
                ReadingMarkEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    chapterId = chapterId,
                    type = ReadingMarkType.BOOKMARK.name,
                    startOffset = startOffset,
                    endOffset = startOffset,
                    excerpt = excerpt.take(120)
                )
            )
        }
    }

    fun deleteReadingMark(markId: String) {
        viewModelScope.launch { repository.deleteReadingMark(markId) }
    }

    // ---- 对话块点读（2.5）----

    private val _blockSpeak = MutableStateFlow<BlockSpeakUiState?>(null)
    val blockSpeak: StateFlow<BlockSpeakUiState?> = _blockSpeak

    private val _blockSpeakError = MutableStateFlow<String?>(null)
    val blockSpeakError: StateFlow<String?> = _blockSpeakError

    private val blockSpeakController by lazy {
        BlockSpeakController(
            context = application,
            repository = repository,
            settings = TtsSettingsStore(application),
            resolver = VoiceResolver(repository, TtsSettingsStore(application))
        )
    }

    fun speakDialogueBlock(bookId: String, chapterId: String, blockIndex: Int, speaker: String, text: String) {
        val current = _blockSpeak.value
        if (current?.playing == true && current.chapterId == chapterId && current.blockIndex == blockIndex) {
            stopBlockSpeak()
            return
        }
        viewModelScope.launch {
            blockSpeakController.stop()
            _blockSpeak.value = BlockSpeakUiState(chapterId, blockIndex, playing = true)
            val characters = repository.getCharacters(bookId)
            val character = characters.firstOrNull { it.canonicalName == speaker }
            runCatching {
                blockSpeakController.speak(bookId, speaker, character, text) {
                    if (_blockSpeak.value?.chapterId == chapterId && _blockSpeak.value?.blockIndex == blockIndex) {
                        _blockSpeak.value = null
                    }
                }
            }.onFailure { error ->
                _blockSpeak.value = null
                _blockSpeakError.value = error.message ?: "朗读失败"
            }
        }
    }

    fun stopBlockSpeak() {
        blockSpeakController.stop()
        _blockSpeak.value = null
    }

    fun clearBlockSpeakError() {
        _blockSpeakError.value = null
    }

    // ---- 书籍封面（Pollinations.ai）----

    private val coverCoordinator = CoverGenerationCoordinator()
    private val coverGenerator by lazy { PollinationsCoverGenerator(application) }

    private val _coverError = MutableStateFlow<String?>(null)
    val coverError: StateFlow<String?> = _coverError

    /** 生成/重新生成书籍封面。默认 seed 由 bookId 稳定派生；regenerate 换随机 seed。 */
    fun generateBookCover(bookId: String, title: String, regenerate: Boolean = false) {
        if (coverCoordinator.isDeleting(bookId)) return
        _coverError.value = null
        val generationId = coverCoordinator.beginGeneration(bookId) ?: return
        val seed = if (regenerate) (System.currentTimeMillis() % 100_000).toInt()
        else PollinationsCoverGenerator.stableSeed(bookId)
        coverGenerator.generate(bookId, title, seed) { result ->
            result.onSuccess { artifact ->
                viewModelScope.launch {
                    try {
                        withContext(NonCancellable + Dispatchers.IO) {
                            CoverPublication.publishIfCurrent(
                                filesDir = getApplication<Application>().filesDir,
                                bookId = bookId,
                                generationId = generationId,
                                artifact = artifact,
                                coordinator = coverCoordinator,
                                readBook = {
                                    val book = repository.getBook(bookId)
                                    ExistingBookCover(book != null, book?.coverPath)
                                },
                                updatePath = { path -> repository.updateBookCover(bookId, path) }
                            )
                        }
                    } catch (error: Throwable) {
                        withContext(Dispatchers.IO) { artifact.temporary.delete() }
                        _coverError.value = error.message ?: "封面生成失败"
                    } finally {
                        coverCoordinator.finishGeneration(bookId, generationId)
                    }
                }
            }
            result.onFailure { error ->
                if (coverCoordinator.finishGeneration(bookId, generationId)) {
                    viewModelScope.launch { _coverError.value = error.message ?: "封面生成失败" }
                }
            }
        }
    }

    fun readerPosition(
        bookId: String,
        chapterId: String,
        displayMode: ReaderDisplayMode
    ): ReaderPosition? = readerPositionStore.read(bookId, chapterId, displayMode)

    fun saveReaderPosition(
        bookId: String,
        chapterId: String,
        displayMode: ReaderDisplayMode,
        itemIndex: Int,
        itemOffsetPx: Int
    ) {
        val position = ReaderPosition(
            bookId = bookId,
            chapterId = chapterId,
            displayMode = displayMode,
            itemIndex = itemIndex,
            itemOffsetPx = itemOffsetPx
        )
        readerPositionStore.save(position)
    }

    @Synchronized
    fun generateChapterTts(bookId: String, chapterId: String) {
        if (_ttsState.value.running || deletingBookId != null) return
        // Publish ownership before any suspend point so generate/delete are mutually exclusive.
        _ttsState.value = TtsUiState(chapterId = chapterId, running = true, progress = "准备章节文本…")
        ttsJob = viewModelScope.launch {
            audioPlayer.stop()
            runCatching {
                repository.updateTtsStatus(chapterId, TaskStatus.QUEUED)
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
                if (error is CancellationException) {
                    _ttsState.value = TtsUiState(chapterId = chapterId, message = "已取消章节配音生成")
                    return@onFailure
                }
                if (error is EdgeTtsException) {
                    val network = NetworkFailureClassifier.classify(
                        error,
                        stage = RequestStage.RESPONSE
                    )
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.recordTaskFailure(
                            taskType = TaskRunType.TTS,
                            targetId = chapterId,
                            eventType = "ERROR",
                            stage = network.stage.name,
                            retryable = error.retryable && network.retryable,
                            statusCode = network.statusCode,
                            attempt = network.attempt,
                            message = network.message
                        )
                    }
                }
                _ttsState.value = TtsUiState(
                    chapterId = chapterId,
                    message = error.message ?: "章节配音失败",
                    isError = true
                )
            }
        }
    }

    fun cancelChapterTtsGeneration() {
        if (_ttsState.value.running) ttsJob?.cancel()
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
        analysisJob = viewModelScope.launch {
            _analysisState.value = AnalysisUiState(bookId = bookId, running = true, status = AnalysisStatus.RUNNING, message = "正在调用 LLM 分析…")
            runCatching { withContext(Dispatchers.IO) { analyzer.analyzeNext(bookId, chapterCount) { progress ->
                _analysisState.value = _analysisState.value.withProgress(progress)
            } } }
                .onSuccess { result ->
                    publishAnalysisSuccess(bookId, result)
                }
                .onFailure { error ->
                    if (error is CancellationException) {
                        _analysisState.value = _analysisState.value.copy(running = false, status = AnalysisStatus.CANCELLED, message = "已取消分析")
                        return@onFailure
                    }
                    publishAnalysisFailure(error)
                }
        }
    }

    fun analyzeAll(bookId: String) {
        if (_analysisState.value.running) return
        analysisJob = viewModelScope.launch {
            _analysisState.value = AnalysisUiState(bookId = bookId, running = true, status = AnalysisStatus.RUNNING, message = "正在分析剩余全部章节…")
            runCatching { withContext(Dispatchers.IO) { analyzer.analyzeAll(bookId) { progress ->
                _analysisState.value = _analysisState.value.withProgress(progress)
            } } }
                .onSuccess { result -> publishAnalysisSuccess(bookId, result) }
                .onFailure { error ->
                    if (error is CancellationException) {
                        _analysisState.value = _analysisState.value.copy(running = false, status = AnalysisStatus.CANCELLED, message = "已取消分析")
                        return@onFailure
                    }
                    publishAnalysisFailure(error)
                }
        }
    }

    fun cancelAnalysis() {
        if (_analysisState.value.running) analysisJob?.cancel()
    }

    private fun publishAnalysisSuccess(bookId: String, result: AnalysisRunResult) {
        _analysisState.value = AnalysisUiState(
            bookId = bookId,
            status = if (result.chapterCount == 0) AnalysisStatus.SKIPPED else AnalysisStatus.SUCCESS,
            message = result.message,
            completedChapters = result.completed,
            promptTokens = result.usage.promptTokens,
            completionTokens = result.usage.completionTokens,
            totalTokens = result.usage.totalTokens,
            usageQuality = result.usage.quality,
            cost = "费用未知"
        )
    }

    private fun publishAnalysisFailure(error: Throwable) {
                    val analysisFailure = error as? AnalysisFailureException
                    _analysisState.value = _analysisState.value.asFailure(error.message ?: "LLM 分析失败", analysisFailure)
    }

    @Synchronized
    fun deleteBook(bookId: String, onComplete: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (_ttsState.value.running) { onError("请等待当前章节配音生成完成后再删除"); return }
        if (deletingBookId != null) return
        deletingBookId = bookId
        coverCoordinator.markDeleting(bookId)
        viewModelScope.launch {
            runCatching {
                repository.getBook(bookId) ?: error("找不到这本小说")
                val chapterIds = repository.getChapters(bookId).map { it.id }
                audioPlayer.stop()
                withContext(NonCancellable + Dispatchers.IO) {
                    coverCoordinator.withBookLock(bookId) {
                        val currentBook = repository.getBook(bookId) ?: error("找不到这本小说")
                        val ttsStage = ttsEngine.stageAudioDeletion(bookId, chapterIds)
                        val coverStage = runCatching {
                            CoverFileLifecycle.stageDeletion(
                                getApplication<Application>().filesDir,
                                currentBook.coverPath
                            )
                        }.getOrElse { error ->
                            ttsEngine.restoreAudioDeletion(ttsStage)
                            throw error
                        }
                        try {
                            repository.deleteBook(bookId)
                        } catch (error: Throwable) {
                            coverStage?.let(CoverFileLifecycle::restoreDeletion)
                            ttsEngine.restoreAudioDeletion(ttsStage)
                            throw error
                        }
                        ttsEngine.commitAudioDeletion(ttsStage)
                        runCatching { CoverFileLifecycle.commitDeletion(coverStage) }
                    }
                }
            }.onSuccess {
                coverCoordinator.clearDeleting(bookId)
                deletingBookId = null
                _ttsState.value = TtsUiState()
                onComplete()
            }.onFailure { error ->
                coverCoordinator.clearDeleting(bookId)
                deletingBookId = null
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
                .onFailure { _characterChatState.value = CharacterChatUiState(characterId, error = it.message ?: "创建对话失败") }
        }
    }

    fun renameChatSession(sessionId: String, characterId: String, title: String) {
        viewModelScope.launch {
            runCatching { repository.renameSession(sessionId, title) }
                .onFailure { _characterChatState.value = CharacterChatUiState(characterId = characterId, error = it.message ?: "重命名对话失败") }
        }
    }

    fun deleteChatSession(sessionId: String, bookId: String, characterId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                repository.deleteSession(sessionId)
                repository.getOrCreateSession(bookId, characterId).id
            }.onSuccess(onReady)
                .onFailure { _characterChatState.value = CharacterChatUiState(characterId = characterId, error = it.message ?: "删除对话失败") }
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

    fun clearCharacterChat(sessionId: String, characterId: String) {
        viewModelScope.launch {
            runCatching { repository.clearChatMessages(sessionId) }
                .onFailure { _characterChatState.value = CharacterChatUiState(characterId = characterId, error = it.message ?: "清空对话失败") }
        }
    }

    fun loadMemoryPicker(
        bookId: String,
        characterId: String,
        sessionId: String,
        query: String = _memoryPickerState.value.query,
        suggestionText: String = ""
    ) {
        val requestId = ++memoryPickerRequestSequence
        _memoryPickerState.value = _memoryPickerState.value.copy(
            bookId = bookId,
            characterId = characterId,
            sessionId = sessionId,
            query = query,
            requestId = requestId,
            loading = true,
            items = emptyList(),
            suggestions = emptyList(),
            message = null
        )
        viewModelScope.launch {
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
                if (!MemoryPickerRequestPolicy.matches(_memoryPickerState.value, bookId, characterId, sessionId, query, requestId)) return@onSuccess
                _memoryPickerState.value = _memoryPickerState.value.copy(
                    loading = false,
                    items = items,
                    suggestions = suggestions,
                    isError = false
                )
            }.onFailure { error ->
                if (!MemoryPickerRequestPolicy.matches(_memoryPickerState.value, bookId, characterId, sessionId, query, requestId)) return@onFailure
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
                if (_memoryPickerState.value.requestId != state.requestId || _memoryPickerState.value.sessionId != sessionId) return@onSuccess
                loadMemoryPicker(state.bookId.orEmpty(), characterId, sessionId, state.query)
            }.onFailure { error ->
                if (_memoryPickerState.value.requestId != state.requestId || _memoryPickerState.value.sessionId != sessionId) return@onFailure
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

    @Synchronized
    fun prepareNeo4jExport(bookId: String, onReady: (Long, String) -> Unit): Long? {
        if (!Neo4jExportRequestPolicy.canStart(_exportState.value)) return null
        val requestId = ++exportRequestSequence
        _exportState.value = ExportUiState(bookId = bookId, requestId = requestId, running = true)
        viewModelScope.launch {
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
                if (!Neo4jExportRequestPolicy.matches(_exportState.value, bookId, requestId)) return@onSuccess
                // Keep the request busy while the system document picker is open.
                _exportState.value = ExportUiState(bookId = bookId, requestId = requestId, running = true)
                onReady(requestId, content)
            }.onFailure { error ->
                if (Neo4jExportRequestPolicy.matches(_exportState.value, bookId, requestId)) {
                    _exportState.value = ExportUiState(bookId, requestId, message = error.message ?: "Neo4j 导出失败", isError = true)
                }
            }
        }
        return requestId
    }

    fun cancelNeo4jExport(bookId: String, requestId: Long) {
        if (Neo4jExportRequestPolicy.matches(_exportState.value, bookId, requestId)) {
            _exportState.value = ExportUiState()
        }
    }

    fun writeNeo4jExport(bookId: String, requestId: Long, uri: Uri, content: String) {
        if (!Neo4jExportRequestPolicy.matches(_exportState.value, bookId, requestId)) return
        _exportState.value = _exportState.value.copy(running = true, message = null, isError = false)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入导出文件")
                }
            }.onSuccess {
                if (Neo4jExportRequestPolicy.matches(_exportState.value, bookId, requestId)) {
                    _exportState.value = ExportUiState(bookId, requestId, message = "Neo4j Cypher 已导出")
                }
            }.onFailure { error ->
                if (Neo4jExportRequestPolicy.matches(_exportState.value, bookId, requestId)) {
                    _exportState.value = ExportUiState(bookId, requestId, message = error.message ?: "导出文件写入失败", isError = true)
                }
            }
        }
    }

    override fun onCleared() {
        audioPlayer.stop()
        super.onCleared()
    }
}
