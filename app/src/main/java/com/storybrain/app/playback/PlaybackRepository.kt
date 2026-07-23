package com.storybrain.app.playback

import android.content.ComponentName
import android.content.Context
import android.media.MediaExtractor
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.storybrain.app.data.SleepTimerMode
import com.storybrain.app.data.NarrationSource
import com.storybrain.app.data.NarrationStage
import com.storybrain.app.data.NarrationUiState
import com.storybrain.app.data.StoryRepository
import com.storybrain.app.data.TaskStatus
import com.storybrain.app.reader.ReadingBlock
import com.storybrain.app.reader.TextToChatParser
import com.storybrain.app.tts.AndroidTtsSynthesizer
import com.storybrain.app.tts.ChapterTtsEngine
import com.storybrain.app.tts.SystemTtsException
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class PlaybackUiState(
    val connected: Boolean = false,
    val bookId: String? = null,
    val bookTitle: String = "",
    val chapterId: String? = null,
    val chapterTitle: String = "",
    val chapterIndex: Int = -1,
    val segmentIndex: Int = 0,
    val blockIndex: Int = -1,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val chapterPositionMs: Long = 0,
    val chapterDurationMs: Long = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val speed: Float = 1f,
    val sleepTimerMode: SleepTimerMode = SleepTimerMode.OFF,
    val sleepTimerEndAt: Long = 0,
    val missingNextChapterAudio: Boolean = false,
    val error: String? = null,
    val narration: NarrationUiState = NarrationUiState()
) {
    val hasMedia: Boolean get() = chapterId != null
}

class PlaybackRepository(
    context: Context,
    private val storyRepository: StoryRepository,
    private val stateStore: PlaybackStateStore
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerFuture: ListenableFuture<MediaController> = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
    ).buildAsync()
    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()
    private var queue: List<SegmentRef> = emptyList()
    private var queueEndsAtMissingAudio = false
    private var sleepJob: Job? = null
    private var systemGenerationJob: Job? = null
    private val systemTts = AndroidTtsSynthesizer(appContext)
    private val automaticEdgeEngine = ChapterTtsEngine(appContext, storyRepository)
    private val automaticEdgeJobs = mutableMapOf<String, Job>()
    private val edgeCooldown = appContext.getSharedPreferences("edge_auto_cooldown_v6", Context.MODE_PRIVATE)
    private var lastPersistedAt = 0L
    private var lastChapterId: String? = null

    init {
        scope.launch(Dispatchers.IO) {
            runCatching { withTimeout(TTS_WARM_UP_TIMEOUT_MS) { systemTts.warmUp() } }
        }
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }
                    .onSuccess { controller ->
                        controller.addListener(listener)
                        _uiState.value = _uiState.value.copy(connected = true)
                        scope.launch { restore(controller) }
                        scope.launch { progressLoop(controller) }
                    }
                    .onFailure { _uiState.value = _uiState.value.copy(error = "无法连接播放器：${it.message}") }
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    suspend fun playChapter(
        bookId: String,
        chapterId: String,
        startBlockIndex: Int? = null,
        autoPlay: Boolean = true
    ): Result<Unit> = runCatching {
        systemGenerationJob?.cancel()
        val prepared = withContext(Dispatchers.IO) {
            runCatching { buildQueue(bookId, chapterId) }.getOrNull()
        }
        if (prepared?.items?.isNotEmpty() == true) {
            withContext(Dispatchers.Main.immediate) {
                val controller = controller()
                queue = prepared.refs
                queueEndsAtMissingAudio = prepared.endsAtMissingAudio
                val startIndex = startBlockIndex?.let { block ->
                    queue.indexOfFirst { it.chapterId == chapterId && it.blockIndex >= block }.takeIf { it >= 0 }
                } ?: queue.indexOfFirst { it.chapterId == chapterId }.coerceAtLeast(0)
                controller.setMediaItems(prepared.items, startIndex, 0)
                controller.prepare()
                controller.playWhenReady = autoPlay
                lastChapterId = chapterId
                _uiState.value = _uiState.value.copy(
                    missingNextChapterAudio = false,
                    error = null,
                    narration = NarrationUiState(
                        source = NarrationSource.PREMIUM_CACHE,
                        stage = NarrationStage.PREPARING,
                        blockIndex = startBlockIndex ?: -1,
                        completedSegments = prepared.items.size,
                        totalSegments = prepared.items.size,
                        detail = "正在准备精品配音"
                    )
                )
                updateState(controller)
                persist(force = true)
            }
        } else {
            playWithSystemTts(bookId, chapterId, startBlockIndex, autoPlay)
        }
    }.onFailure { error ->
        _uiState.value = _uiState.value.copy(
            error = error.message ?: "音频播放失败",
            narration = _uiState.value.narration.copy(
                stage = NarrationStage.FAILED,
                detail = error.message ?: "音频播放失败",
                canRetry = true,
                needsVoiceData = (error as? SystemTtsException)?.missingVoiceData == true
            )
        )
    }

    fun play() = withController { it.play() }
    fun pause() = withController { it.pause(); persist(force = true) }

    fun stop() = withController { controller ->
        controller.stop()
        controller.clearMediaItems()
        queue = emptyList()
        systemGenerationJob?.cancel()
        queueEndsAtMissingAudio = false
        _uiState.value = PlaybackUiState(connected = true, speed = _uiState.value.speed)
        persist(force = true)
    }

    fun stopIfBook(bookId: String) {
        if (_uiState.value.bookId == bookId) stop()
    }

    fun seekToChapterPosition(positionMs: Long) = withController { controller ->
        val chapterId = _uiState.value.chapterId ?: return@withController
        var remaining = positionMs.coerceAtLeast(0)
        val window = Timeline.Window()
        queue.forEachIndexed { index, ref ->
            if (ref.chapterId != chapterId) return@forEachIndexed
            val duration = controller.currentTimeline.getWindow(index, window).durationMs.safeDuration()
            if (duration <= 0 || remaining <= duration) {
                controller.seekTo(index, remaining.coerceAtMost(duration.takeIf { it > 0 } ?: remaining))
                return@withController
            }
            remaining -= duration
        }
    }

    fun seekToBlock(blockIndex: Int) = withController { controller ->
        val chapterId = _uiState.value.chapterId ?: return@withController
        val index = queue.indexOfFirst { it.chapterId == chapterId && it.blockIndex >= blockIndex }
        if (index >= 0) {
            controller.seekTo(index, 0)
            controller.play()
        }
    }

    fun previousChapter() = jumpChapter(-1)

    fun nextChapter() {
        val current = _uiState.value
        val next = queue.firstOrNull { it.chapterIndex > current.chapterIndex }
        if (next == null) {
            pause()
            _uiState.value = current.copy(
                missingNextChapterAudio = queueEndsAtMissingAudio,
                error = if (queueEndsAtMissingAudio) "下一章尚未生成配音" else current.error
            )
        } else {
            withController { it.seekTo(queue.indexOf(next), 0); it.play() }
        }
    }

    fun setSpeed(speed: Float) = withController { controller ->
        val safe = speed.coerceIn(.75f, 2f)
        controller.setPlaybackSpeed(safe)
        _uiState.value = _uiState.value.copy(speed = safe)
        persist(force = true)
    }

    fun setSleepTimer(mode: SleepTimerMode, minutes: Int = 0) {
        sleepJob?.cancel()
        val endAt = if (mode == SleepTimerMode.MINUTES) {
            System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        } else 0L
        _uiState.value = _uiState.value.copy(sleepTimerMode = mode, sleepTimerEndAt = endAt)
        if (mode == SleepTimerMode.MINUTES) scheduleSleep(endAt)
        persist(force = true)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, missingNextChapterAudio = false)
    }

    fun release() {
        sleepJob?.cancel()
        systemGenerationJob?.cancel()
        automaticEdgeJobs.values.forEach(Job::cancel)
        automaticEdgeJobs.clear()
        systemTts.shutdown()
        scope.cancel()
        ContextCompat.getMainExecutor(appContext).execute {
            MediaController.releaseFuture(controllerFuture)
        }
    }

    private fun jumpChapter(delta: Int) {
        val current = _uiState.value
        val targetIndex = current.chapterIndex + delta
        val target = queue.firstOrNull { it.chapterIndex == targetIndex } ?: return
        withController { it.seekTo(queue.indexOf(target), 0); it.play() }
    }

    private suspend fun restore(controller: MediaController) {
        val saved = stateStore.load()
        if (saved.bookId.isBlank() || saved.chapterId.isBlank()) return
        val prepared = runCatching {
            withContext(Dispatchers.IO) { buildQueue(saved.bookId, saved.chapterId) }
        }.getOrNull() ?: return
        if (prepared.items.isEmpty()) return
        queue = prepared.refs
        queueEndsAtMissingAudio = prepared.endsAtMissingAudio
        val itemIndex = queue.indexOfFirst {
            it.chapterId == saved.chapterId && it.segmentIndex == saved.segmentIndex
        }.coerceAtLeast(0)
        controller.setMediaItems(prepared.items, itemIndex, saved.positionMs)
        controller.setPlaybackSpeed(saved.speed)
        controller.prepare()
        controller.playWhenReady = false
        _uiState.value = _uiState.value.copy(
            speed = saved.speed,
            sleepTimerMode = saved.sleepTimerMode,
            sleepTimerEndAt = saved.sleepTimerEndAt
        )
        if (saved.sleepTimerMode == SleepTimerMode.MINUTES && saved.sleepTimerEndAt > System.currentTimeMillis()) {
            scheduleSleep(saved.sleepTimerEndAt)
        } else if (saved.sleepTimerMode == SleepTimerMode.MINUTES) {
            setSleepTimer(SleepTimerMode.OFF)
        }
        updateState(controller)
    }

    private suspend fun buildQueue(bookId: String, chapterId: String): PreparedQueue {
        val book = storyRepository.getBook(bookId) ?: error("找不到小说")
        val chapters = storyRepository.getChapters(bookId)
        val startIndex = chapters.indexOfFirst { it.id == chapterId }
        require(startIndex >= 0) { "找不到章节" }
        val refs = mutableListOf<SegmentRef>()
        val items = mutableListOf<MediaItem>()
        var missing = false
        val playableChapters = mutableListOf<Pair<com.storybrain.app.data.ChapterEntity, List<PlaybackManifestSegment>>>()
        chapters.getOrNull(startIndex - 1)?.let { previous ->
            val previousManifest = previous.ttsManifestPath?.let(::File)
            if (TaskStatus.fromStorage(previous.ttsStatus) == TaskStatus.COMPLETED && previousManifest?.exists() == true) {
                PlaybackManifestParser.parse(previousManifest)
                    .filter { isDecodableAudio(File(it.path)) }
                    .takeIf { it.isNotEmpty() }?.let { parsed ->
                    playableChapters += previous to parsed
                }
            }
        }
        for (chapter in chapters.drop(startIndex)) {
            val manifest = chapter.ttsManifestPath?.let(::File)
            if (TaskStatus.fromStorage(chapter.ttsStatus) != TaskStatus.COMPLETED || manifest?.exists() != true) {
                if (chapter.id == chapterId) {
                    val partial = storyRepository.getTtsScript(chapter.id)
                        ?.let { storyRepository.getTtsScriptSegments(it.id) }
                        .orEmpty()
                        .filter { TaskStatus.fromStorage(it.status) == TaskStatus.COMPLETED }
                        .mapNotNull { segment ->
                            val audio = segment.audioPath?.let(::File)?.takeIf(::isDecodableAudio) ?: return@mapNotNull null
                            PlaybackManifestSegment(segment.segmentIndex, segment.blockIndex, segment.speaker, audio.absolutePath)
                        }
                    if (partial.isNotEmpty()) {
                        playableChapters += chapter to partial
                        missing = true
                        break
                    }
                    error("本章尚未生成配音")
                }
                missing = true
                break
            }
            val parsed = PlaybackManifestParser.parse(manifest).filter { isDecodableAudio(File(it.path)) }
            if (parsed.isEmpty()) {
                if (chapter.id == chapterId) error("本章配音清单无有效音频")
                missing = true
                break
            }
            playableChapters += chapter to parsed
        }
        playableChapters.forEach { (chapter, parsed) ->
            parsed.forEach { segment ->
                val ref = SegmentRef(
                    bookId = bookId,
                    bookTitle = book.title,
                    chapterId = chapter.id,
                    chapterTitle = chapter.title,
                    chapterIndex = chapter.chapterIndex,
                    segmentIndex = segment.index,
                    blockIndex = segment.blockIndex
                )
                refs += ref
                items += MediaItem.Builder()
                    .setMediaId(ref.encode())
                    .setUri(File(segment.path).toUri())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(chapter.title)
                            .setArtist(book.title)
                            .setSubtitle(segment.speaker)
                            .build()
                    )
                    .build()
            }
        }
        return PreparedQueue(refs, items, missing)
    }

    /**
     * Starts with one locally synthesized block, then appends later blocks to Media3 as they
     * become available. This path never invokes LLM, Fish Audio or OpenAI-compatible TTS.
     */
    private suspend fun playWithSystemTts(
        bookId: String,
        chapterId: String,
        startBlockIndex: Int?,
        autoPlay: Boolean
    ) {
        _uiState.value = _uiState.value.copy(
            error = null,
            narration = NarrationUiState(
                source = NarrationSource.SYSTEM_TTS,
                stage = NarrationStage.PREPARING,
                blockIndex = startBlockIndex ?: 0,
                detail = "正在启动系统中文朗读"
            )
        )
        val prepared = withContext(Dispatchers.IO) {
            val book = storyRepository.getBook(bookId) ?: error("找不到小说")
            val chapter = storyRepository.getChapter(chapterId) ?: error("找不到章节")
            val aliases = buildMap {
                storyRepository.getCharacters(bookId).forEach { character ->
                    put(character.canonicalName, character.canonicalName)
                    val values = runCatching { JSONArray(character.aliasesJson) }.getOrNull() ?: JSONArray()
                    repeat(values.length()) { index ->
                        values.optString(index).trim().takeIf(String::isNotBlank)?.let { putIfAbsent(it, character.canonicalName) }
                    }
                }
            }
            val blocks = TextToChatParser.parse(chapter.content, aliases)
            require(blocks.isNotEmpty()) { "本章没有可朗读内容" }
            val start = (startBlockIndex ?: 0).coerceIn(blocks.indices)
            SystemNarration(book.id, book.title, chapter.id, chapter.title, chapter.chapterIndex, blocks, start)
        }
        val firstBlock = prepared.blocks[prepared.startIndex]
        val firstFile = systemAudioFile(chapterId, prepared.startIndex, firstBlock.text)
        withTimeout(FIRST_SOUND_TIMEOUT_MS) { systemTts.synthesize(firstBlock.text, firstFile) }
        val firstRef = prepared.ref(prepared.startIndex)
        val firstItem = prepared.item(firstRef, firstBlock, firstFile)
        withContext(Dispatchers.Main.immediate) {
            val controller = controller()
            queue = listOf(firstRef)
            queueEndsAtMissingAudio = false
            controller.setMediaItem(firstItem)
            controller.prepare()
            controller.playWhenReady = autoPlay
            lastChapterId = chapterId
            _uiState.value = _uiState.value.copy(
                narration = _uiState.value.narration.copy(
                    stage = NarrationStage.PLAYING,
                    blockIndex = prepared.startIndex,
                    completedSegments = 1,
                    totalSegments = prepared.blocks.size - prepared.startIndex,
                    detail = "系统朗读已就绪"
                )
            )
            updateState(controller)
            persist(force = true)
        }

        systemGenerationJob = scope.launch(Dispatchers.IO) {
            prepared.blocks.indices.drop(prepared.startIndex + 1).forEach { index ->
                val block = prepared.blocks[index]
                val output = systemAudioFile(chapterId, index, block.text)
                runCatching { systemTts.synthesize(block.text, output) }
                    .onFailure { error ->
                        withContext(Dispatchers.Main.immediate) {
                            _uiState.value = _uiState.value.copy(
                                narration = _uiState.value.narration.copy(
                                    stage = NarrationStage.FAILED,
                                    detail = "第 ${index + 1} 段系统朗读失败：${error.message}",
                                    canRetry = true,
                                    needsVoiceData = (error as? SystemTtsException)?.missingVoiceData == true
                                ),
                                error = error.message
                            )
                        }
                        return@launch
                    }
                val ref = prepared.ref(index)
                val item = prepared.item(ref, block, output)
                withContext(Dispatchers.Main.immediate) {
                    val controller = controller()
                    queue = queue + ref
                    controller.addMediaItem(item)
                    _uiState.value = _uiState.value.copy(
                        narration = _uiState.value.narration.copy(
                            stage = if (controller.isPlaying) NarrationStage.PLAYING else NarrationStage.GENERATING,
                            completedSegments = index - prepared.startIndex + 1,
                            detail = "已准备 ${index - prepared.startIndex + 1}/${prepared.blocks.size - prepared.startIndex} 段"
                        )
                    )
                }
            }
        }
        startAutomaticEdge(bookId, chapterId)
    }

    /** Free Edge enhancement runs independently and never interrupts the current system voice. */
    private fun startAutomaticEdge(bookId: String, chapterId: String) {
        if (automaticEdgeJobs[chapterId]?.isActive == true) return
        val now = System.currentTimeMillis()
        if (edgeCooldown.getLong("cooldown:$chapterId", 0L) > now) {
            _uiState.value = _uiState.value.copy(
                narration = _uiState.value.narration.copy(detail = "系统朗读中；Edge 暂停自动重试 30 分钟")
            )
            return
        }
        automaticEdgeJobs[chapterId] = scope.launch(Dispatchers.IO) {
            runCatching {
                automaticEdgeEngine.generate(
                    bookId = bookId,
                    chapterId = chapterId,
                    onProgress = { completed, total ->
                        _uiState.value = _uiState.value.copy(
                            narration = _uiState.value.narration.copy(
                                completedSegments = completed,
                                totalSegments = total,
                                detail = "系统朗读中；Edge 精品缓存 $completed/$total"
                            )
                        )
                    },
                    onStage = { stage ->
                        _uiState.value = _uiState.value.copy(
                            narration = _uiState.value.narration.copy(detail = "系统朗读中；Edge $stage")
                        )
                    },
                    forceFreeEdge = true
                )
            }.onSuccess {
                edgeCooldown.edit().putInt("failures:$chapterId", 0).remove("cooldown:$chapterId").apply()
                _uiState.value = _uiState.value.copy(
                    narration = _uiState.value.narration.copy(detail = "系统朗读中；Edge 精品缓存已完成，下次播放使用")
                )
            }.onFailure { error ->
                val failures = edgeCooldown.getInt("failures:$chapterId", 0) + 1
                edgeCooldown.edit()
                    .putInt("failures:$chapterId", failures)
                    .apply {
                        if (failures >= 2) putLong("cooldown:$chapterId", System.currentTimeMillis() + EDGE_COOLDOWN_MS)
                    }
                    .apply()
                _uiState.value = _uiState.value.copy(
                    narration = _uiState.value.narration.copy(
                        detail = "系统朗读不受影响；Edge 自动缓存失败：${error.message}",
                        canRetry = true
                    )
                )
            }
            automaticEdgeJobs.remove(chapterId)
        }
    }

    private fun systemAudioFile(chapterId: String, blockIndex: Int, text: String): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)
        return File(appContext.filesDir, "system-tts/$chapterId/${"%04d".format(blockIndex)}-$hash.wav")
    }

    private fun isDecodableAudio(file: File): Boolean {
        if (!file.isFile || file.length() <= 128L) return false
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            extractor.trackCount > 0
        } catch (_: Throwable) {
            false
        } finally {
            extractor.release()
        }
    }

    private suspend fun controller(): MediaController = suspendCancellableCoroutine { continuation ->
        if (controllerFuture.isDone) {
            runCatching { controllerFuture.get() }
                .onSuccess { continuation.resume(it) }
                .onFailure { continuation.resumeWithException(it) }
        } else {
            controllerFuture.addListener(
                {
                    runCatching { controllerFuture.get() }
                        .onSuccess { continuation.resume(it) }
                        .onFailure { continuation.resumeWithException(it) }
                },
                ContextCompat.getMainExecutor(appContext)
            )
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        scope.launch { runCatching { block(controller()) }.onFailure { _uiState.value = _uiState.value.copy(error = it.message) } }
    }

    private suspend fun progressLoop(controller: MediaController) {
        while (scope.isActive) {
            updateState(controller)
            if (controller.isPlaying) persist(force = false)
            delay(500)
        }
    }

    private fun updateState(controller: MediaController) {
        val itemIndex = controller.currentMediaItemIndex
        val ref = queue.getOrNull(itemIndex) ?: return
        val window = Timeline.Window()
        var chapterPosition = 0L
        var chapterDuration = 0L
        queue.forEachIndexed { index, item ->
            if (item.chapterId != ref.chapterId) return@forEachIndexed
            val duration = if (index < controller.currentTimeline.windowCount) {
                controller.currentTimeline.getWindow(index, window).durationMs.safeDuration()
            } else 0L
            chapterDuration += duration
            if (index < itemIndex) chapterPosition += duration
        }
        chapterPosition += controller.currentPosition.coerceAtLeast(0)
        _uiState.value = _uiState.value.copy(
            connected = true,
            bookId = ref.bookId,
            bookTitle = ref.bookTitle,
            chapterId = ref.chapterId,
            chapterTitle = ref.chapterTitle,
            chapterIndex = ref.chapterIndex,
            segmentIndex = ref.segmentIndex,
            blockIndex = ref.blockIndex,
            positionMs = controller.currentPosition.coerceAtLeast(0),
            durationMs = controller.duration.safeDuration(),
            chapterPositionMs = chapterPosition,
            chapterDurationMs = chapterDuration,
            isPlaying = controller.isPlaying,
            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
            speed = controller.playbackParameters.speed,
            narration = _uiState.value.narration.copy(
                blockIndex = ref.blockIndex,
                stage = when {
                    controller.playbackState == Player.STATE_BUFFERING -> NarrationStage.BUFFERING
                    controller.isPlaying -> NarrationStage.PLAYING
                    else -> _uiState.value.narration.stage
                }
            )
        )
    }

    private fun persist(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistedAt < 2_000) return
        lastPersistedAt = now
        val state = _uiState.value
        scope.launch(Dispatchers.IO) {
            stateStore.save(
                SavedPlaybackState(
                    bookId = state.bookId.orEmpty(),
                    chapterId = state.chapterId.orEmpty(),
                    segmentIndex = state.segmentIndex,
                    positionMs = state.positionMs,
                    speed = state.speed,
                    sleepTimerMode = state.sleepTimerMode,
                    sleepTimerEndAt = state.sleepTimerEndAt
                )
            )
        }
    }

    private fun scheduleSleep(endAt: Long) {
        sleepJob?.cancel()
        sleepJob = scope.launch {
            delay((endAt - System.currentTimeMillis()).coerceAtLeast(0))
            pause()
            _uiState.value = _uiState.value.copy(sleepTimerMode = SleepTimerMode.OFF, sleepTimerEndAt = 0)
            persist(force = true)
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateState(controllerFuture.get())
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val controller = controllerFuture.get()
            val currentChapter = queue.getOrNull(controller.currentMediaItemIndex)?.chapterId
            if (_uiState.value.sleepTimerMode == SleepTimerMode.END_OF_CHAPTER &&
                lastChapterId != null && currentChapter != null && currentChapter != lastChapterId
            ) {
                controller.pause()
                controller.seekTo(controller.currentMediaItemIndex, 0)
                _uiState.value = _uiState.value.copy(sleepTimerMode = SleepTimerMode.OFF)
                lastChapterId = currentChapter
                persist(force = true)
            } else {
                lastChapterId = currentChapter
            }
            updateState(controller)
            persist(force = true)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                if (_uiState.value.sleepTimerMode == SleepTimerMode.END_OF_CHAPTER) {
                    _uiState.value = _uiState.value.copy(sleepTimerMode = SleepTimerMode.OFF)
                    persist(force = true)
                }
                if (queueEndsAtMissingAudio) {
                    _uiState.value = _uiState.value.copy(
                        missingNextChapterAudio = true,
                        error = "下一章尚未生成配音"
                    )
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.value = _uiState.value.copy(error = "音频播放失败：${error.errorCodeName}")
        }
    }

    private data class PreparedQueue(val refs: List<SegmentRef>, val items: List<MediaItem>, val endsAtMissingAudio: Boolean)
    private data class SystemNarration(
        val bookId: String,
        val bookTitle: String,
        val chapterId: String,
        val chapterTitle: String,
        val chapterIndex: Int,
        val blocks: List<ReadingBlock>,
        val startIndex: Int
    ) {
        fun ref(index: Int) = SegmentRef(bookId, bookTitle, chapterId, chapterTitle, chapterIndex, index, index)

        fun item(ref: SegmentRef, block: ReadingBlock, file: File): MediaItem = MediaItem.Builder()
            .setMediaId(ref.encode())
            .setUri(file.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(chapterTitle)
                    .setArtist(bookTitle)
                    .setSubtitle((block as? ReadingBlock.Dialogue)?.speaker ?: "旁白")
                    .build()
            )
            .build()
    }
    private data class SegmentRef(
        val bookId: String,
        val bookTitle: String,
        val chapterId: String,
        val chapterTitle: String,
        val chapterIndex: Int,
        val segmentIndex: Int,
        val blockIndex: Int
    ) {
        fun encode(): String = listOf(bookId, chapterId, chapterIndex, segmentIndex, blockIndex).joinToString("|")
    }

    private fun Long.safeDuration(): Long = takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L

    private companion object {
        const val TTS_WARM_UP_TIMEOUT_MS = 10_000L
        const val FIRST_SOUND_TIMEOUT_MS = 5_000L
        const val EDGE_COOLDOWN_MS = 30 * 60_000L
    }
}
