package com.storybrain.app.playback

import android.content.ComponentName
import android.content.Context
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
import com.storybrain.app.data.StoryRepository
import com.storybrain.app.data.TaskStatus
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

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
    val error: String? = null
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
    private var lastPersistedAt = 0L
    private var lastChapterId: String? = null

    init {
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
        val controller = controller()
        val prepared = buildQueue(bookId, chapterId)
        require(prepared.items.isNotEmpty()) { "本章没有可播放的配音，请先生成" }
        queue = prepared.refs
        queueEndsAtMissingAudio = prepared.endsAtMissingAudio
        val startIndex = startBlockIndex?.let { block ->
            queue.indexOfFirst { it.chapterId == chapterId && it.blockIndex >= block }.takeIf { it >= 0 }
        } ?: queue.indexOfFirst { it.chapterId == chapterId }.coerceAtLeast(0)
        controller.setMediaItems(prepared.items, startIndex, 0)
        controller.prepare()
        controller.playWhenReady = autoPlay
        lastChapterId = chapterId
        _uiState.value = _uiState.value.copy(missingNextChapterAudio = false, error = null)
        updateState(controller)
        persist(force = true)
    }.onFailure { error ->
        _uiState.value = _uiState.value.copy(error = error.message ?: "音频播放失败")
    }

    fun play() = withController { it.play() }
    fun pause() = withController { it.pause(); persist(force = true) }

    fun stop() = withController { controller ->
        controller.stop()
        controller.clearMediaItems()
        queue = emptyList()
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

    private fun jumpChapter(delta: Int) {
        val current = _uiState.value
        val targetIndex = current.chapterIndex + delta
        val target = queue.firstOrNull { it.chapterIndex == targetIndex } ?: return
        withController { it.seekTo(queue.indexOf(target), 0); it.play() }
    }

    private suspend fun restore(controller: MediaController) {
        val saved = stateStore.load()
        if (saved.bookId.isBlank() || saved.chapterId.isBlank()) return
        val prepared = runCatching { buildQueue(saved.bookId, saved.chapterId) }.getOrNull() ?: return
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
                PlaybackManifestParser.parse(previousManifest).takeIf { it.isNotEmpty() }?.let { parsed ->
                    playableChapters += previous to parsed
                }
            }
        }
        for (chapter in chapters.drop(startIndex)) {
            val manifest = chapter.ttsManifestPath?.let(::File)
            if (TaskStatus.fromStorage(chapter.ttsStatus) != TaskStatus.COMPLETED || manifest?.exists() != true) {
                if (chapter.id == chapterId) error("本章尚未生成配音")
                missing = true
                break
            }
            val parsed = PlaybackManifestParser.parse(manifest)
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
            speed = controller.playbackParameters.speed
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
}
