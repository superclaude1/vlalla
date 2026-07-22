package com.storybrain.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.storybrain.app.StoryBrainApplication
import com.storybrain.app.data.BookEntity
import com.storybrain.app.data.ChapterEntity
import com.storybrain.app.data.ChapterListItem
import com.storybrain.app.data.ChapterSearchHit
import com.storybrain.app.data.ReadingMarkEntity
import com.storybrain.app.data.ReadingMarkType
import com.storybrain.app.data.ReadingMode
import com.storybrain.app.data.ReadingPositionEntity
import com.storybrain.app.data.StoryCharacterEntity
import com.storybrain.app.data.TaskStatus
import com.storybrain.app.reader.ReaderDocument
import com.storybrain.app.reader.ResolvedReadingPreferences
import com.storybrain.app.work.WorkContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderExperienceUiState(
    val book: BookEntity? = null,
    val chapter: ChapterEntity? = null,
    val chapters: List<ChapterListItem> = emptyList(),
    val characters: List<StoryCharacterEntity> = emptyList(),
    val document: ReaderDocument? = null,
    val preloadedDocuments: Map<String, ReaderDocument> = emptyMap(),
    val preferences: ResolvedReadingPreferences? = null,
    val position: ReadingPositionEntity? = null,
    val marks: List<ReadingMarkEntity> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<ChapterSearchHit> = emptyList(),
    val searching: Boolean = false,
    val searchIndexing: Boolean = false,
    val searchIndexCompleted: Int = 0,
    val searchIndexTotal: Int = 0,
    val searchIndexStage: String = "",
    val message: String? = null
)

class ReaderViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    val bookId: String = savedStateHandle.get<String>("bookId").orEmpty()
    val chapterId: String = savedStateHandle.get<String>("chapterId").orEmpty()
    val requestedOffset: Int = savedStateHandle.get<Int>("offset") ?: -1

    private val app = application as StoryBrainApplication
    private val storyRepository = app.repository
    private val readerRepository = app.readerRepository
    private val searchQuery = MutableStateFlow("")
    private val searchResults = MutableStateFlow<List<ChapterSearchHit>>(emptyList())
    private val searching = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val indexTask = storyRepository.observeTaskRecord(WorkContracts.searchIndexName(bookId))

    init {
        viewModelScope.launch { app.longTaskScheduler.enqueueSearchIndex(bookId) }
    }

    private val content = combine(
        storyRepository.observeBook(bookId),
        storyRepository.observeChapter(chapterId),
        storyRepository.observeChapterList(bookId),
        storyRepository.observeCharacters(bookId)
    ) { book, chapter, chapters, characters ->
        val speakers = buildMap {
            characters.forEach { character ->
                put(character.canonicalName, character.canonicalName)
                com.storybrain.app.data.MemorySearch.jsonStrings(character.aliasesJson).forEach { alias ->
                    putIfAbsent(alias, character.canonicalName)
                }
            }
        }
        val preloaded = chapter?.let { current ->
            readerRepository.adjacentChapters(bookId, current.chapterIndex).associate { adjacent ->
                adjacent.id to ReaderDocument.create(adjacent.content, speakers)
            }
        }.orEmpty()
        ReaderContent(
            book,
            chapter,
            chapters,
            characters,
            chapter?.let { preloaded[it.id] ?: ReaderDocument.create(it.content, speakers) },
            preloaded
        )
    }.flowOn(Dispatchers.Default)

    private val reading = combine(
        readerRepository.observePreferences(bookId),
        readerRepository.observePosition(bookId),
        readerRepository.observeMarks(bookId)
    ) { preferences, position, marks -> ReaderReading(preferences, position, marks) }

    val uiState: StateFlow<ReaderExperienceUiState> = combine(
        combine(content, reading) { content, reading -> content to reading },
        combine(searchQuery, searchResults, searching) { query, results, loading -> Triple(query, results, loading) },
        combine(message, indexTask) { currentMessage, task -> currentMessage to task }
    ) { (content, reading), search, (currentMessage, task) ->
        val indexStatus = task?.let { TaskStatus.fromStorage(it.status) }
        ReaderExperienceUiState(
            book = content.book,
            chapter = content.chapter,
            chapters = content.chapters,
            characters = content.characters,
            document = content.document,
            preloadedDocuments = content.preloadedDocuments,
            preferences = reading.preferences,
            position = reading.position,
            marks = reading.marks,
            searchQuery = search.first,
            searchResults = search.second,
            searching = search.third,
            searchIndexing = indexStatus == TaskStatus.QUEUED || indexStatus == TaskStatus.RUNNING,
            searchIndexCompleted = task?.completed ?: 0,
            searchIndexTotal = task?.total ?: 0,
            searchIndexStage = task?.stage.orEmpty(),
            message = currentMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderExperienceUiState())

    fun setMode(mode: ReadingMode) {
        viewModelScope.launch { readerRepository.setMode(bookId, mode) }
    }

    fun savePosition(sourceOffset: Int, scrollOffsetPx: Int = 0) {
        if (chapterId.isBlank()) return
        viewModelScope.launch {
            readerRepository.savePosition(bookId, chapterId, sourceOffset, scrollOffsetPx)
        }
    }

    fun saveStyle(value: ResolvedReadingPreferences) {
        viewModelScope.launch { readerRepository.saveBookStyle(bookId, value) }
    }

    fun resetStyle() {
        viewModelScope.launch { readerRepository.resetBookStyle(bookId) }
    }

    fun addMark(
        type: ReadingMarkType,
        startOffset: Int,
        endOffset: Int,
        excerpt: String,
        note: String = "",
        colorKey: String = "amber"
    ) {
        viewModelScope.launch {
            readerRepository.addMark(bookId, chapterId, type, startOffset, endOffset, excerpt, note, colorKey)
            message.value = when (type) {
                ReadingMarkType.BOOKMARK -> "书签已添加"
                ReadingMarkType.HIGHLIGHT -> "高亮已添加"
                ReadingMarkType.NOTE -> "批注已保存"
            }
        }
    }

    fun updateMark(mark: ReadingMarkEntity) {
        viewModelScope.launch { readerRepository.updateMark(mark) }
    }

    fun deleteMark(markId: String) {
        viewModelScope.launch { readerRepository.deleteMark(markId) }
    }

    fun search(query: String) {
        searchQuery.value = query
        if (query.isBlank()) {
            searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            searching.value = true
            searchResults.value = runCatching { readerRepository.search(bookId, query) }
                .onFailure { message.value = it.message ?: "搜索失败" }
                .getOrDefault(emptyList())
            searching.value = false
        }
    }

    fun clearMessage() {
        message.value = null
    }

    private data class ReaderContent(
        val book: BookEntity?,
        val chapter: ChapterEntity?,
        val chapters: List<ChapterListItem>,
        val characters: List<StoryCharacterEntity>,
        val document: ReaderDocument?,
        val preloadedDocuments: Map<String, ReaderDocument>
    )

    private data class ReaderReading(
        val preferences: ResolvedReadingPreferences,
        val position: ReadingPositionEntity?,
        val marks: List<ReadingMarkEntity>
    )
}
