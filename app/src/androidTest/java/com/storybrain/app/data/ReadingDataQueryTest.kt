package com.storybrain.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingDataQueryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: StoryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = StoryRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun chineseSearchReturnsExactSourceOffset() = runBlocking {
        seedBook()
        repository.reindexChapter("chapter-1")
        repository.reindexChapter("chapter-2")

        val query = "雾中的灯塔"
        val chapterContent = "他看见雾中的灯塔亮了。"
        val hits = repository.searchBook("book", query)

        assertEquals(1, hits.size)
        assertEquals("chapter-2", hits.single().chapterId)
        assertEquals(chapterContent.indexOf(query), hits.single().sourceOffset)
    }

    @Test
    fun positionUpdatesStableOffsetAndLegacyChapterProgress() = runBlocking {
        seedBook()

        repository.saveReadingPosition(ReadingPositionEntity("book", "chapter-2", 5, 18))

        val position = repository.observeReadingPosition("book").first()!!
        val book = repository.getBook("book")!!
        assertEquals("chapter-2", position.chapterId)
        assertEquals(5, position.sourceOffset)
        assertEquals(1, book.currentChapterIndex)
    }

    @Test
    fun taskFeedKeepsActiveAndRecentTerminalRecords() = runBlocking {
        seedBook()
        val now = System.currentTimeMillis()
        repository.upsertTaskRecord(
            TaskRecordEntity("analysis:book", TaskType.ANALYSIS.name, "book", title = "分析", status = TaskStatus.RUNNING.name)
        )
        repository.upsertTaskRecord(
            TaskRecordEntity("tts:chapter-1", TaskType.TTS.name, "book", "chapter-1", "配音", TaskStatus.COMPLETED.name, finishedAt = now)
        )

        val tasks = repository.observeTaskRecords(now - 1_000).first()

        assertEquals(2, tasks.size)
        assertTrue(tasks.first().status in listOf(TaskStatus.RUNNING.name, TaskStatus.QUEUED.name))
    }

    @Test
    fun readingMarkCanBeCreatedEditedAndDeleted() = runBlocking {
        seedBook()
        repository.saveReadingMark(
            ReadingMarkEntity(
                id = "mark",
                bookId = "book",
                chapterId = "chapter-2",
                type = ReadingMarkType.NOTE.name,
                startOffset = 3,
                endOffset = 8,
                excerpt = "雾中的灯塔",
                note = "初稿",
                colorKey = "amber"
            )
        )

        val created = repository.observeChapterReadingMarks("chapter-2").first().single()
        repository.saveReadingMark(created.copy(note = "已修改", colorKey = "blue"))
        val edited = repository.observeChapterReadingMarks("chapter-2").first().single()
        assertEquals("已修改", edited.note)
        assertEquals("blue", edited.colorKey)

        repository.deleteReadingMark("mark")
        assertTrue(repository.observeChapterReadingMarks("chapter-2").first().isEmpty())
    }

    private suspend fun seedBook() {
        val dao = database.storyDao()
        dao.insertBook(BookEntity("book", "测试小说", "test.txt", 1, 2, 40))
        dao.insertChapters(
            listOf(
                ChapterEntity("chapter-1", "book", 0, "第一章", "清晨从河岸开始。", 8),
                ChapterEntity("chapter-2", "book", 1, "第二章", "他看见雾中的灯塔亮了。", 12)
            )
        )
    }
}
