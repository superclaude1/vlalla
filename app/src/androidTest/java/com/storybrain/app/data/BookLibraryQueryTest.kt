package com.storybrain.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookLibraryQueryTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun returnsProgressForAllBooksFromOneObservedQuery() = runBlocking {
        val dao = database.storyDao()
        dao.insertBook(BookEntity("book", "测试", "test.txt", 1, 3, 30))
        dao.insertChapters(
            listOf(
                ChapterEntity("c1", "book", 0, "一", "a", 1, TaskStatus.COMPLETED.name, TaskStatus.COMPLETED.name),
                ChapterEntity("c2", "book", 1, "二", "b", 1, TaskStatus.RUNNING.name, TaskStatus.QUEUED.name),
                ChapterEntity("c3", "book", 2, "三", "c", 1)
            )
        )

        val item = dao.observeLibraryItems().first().single()

        assertEquals("book", item.book.id)
        assertEquals(1, item.ttsCompleted)
        assertEquals(1, item.activeAnalysisTasks)
        assertEquals(1, item.activeTtsTasks)
    }
}
