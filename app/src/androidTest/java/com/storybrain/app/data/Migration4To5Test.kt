package com.storybrain.app.data

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration4To5Test {
    private val databaseName = "migration-4-5-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun preservesBookAndBackfillsStableReadingPosition() {
        helper.createDatabase(databaseName, 4).apply {
            insert("books", 0, ContentValues().apply {
                put("id", "book")
                put("title", "迁移测试小说")
                put("sourceName", "test.txt")
                put("importedAt", 12L)
                put("chapterCount", 2)
                put("totalChars", 20L)
                put("currentChapterIndex", 1)
                put("analysisCompleted", 1)
            })
            repeat(2) { index ->
                insert("chapters", 0, ContentValues().apply {
                    put("id", "chapter-$index")
                    put("bookId", "book")
                    put("chapterIndex", index)
                    put("title", "第${index + 1}章")
                    put("content", "测试正文$index")
                    put("charCount", 5)
                    put("analysisStatus", TaskStatus.COMPLETED.name)
                    put("ttsStatus", TaskStatus.PENDING.name)
                    putNull("ttsManifestPath")
                })
            }
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            AppDatabase.MIGRATION_4_5
        )

        migrated.query("SELECT title, currentChapterIndex FROM books WHERE id = 'book'").use {
            assertTrue(it.moveToFirst())
            assertEquals("迁移测试小说", it.getString(0))
            assertEquals(1, it.getInt(1))
        }
        migrated.query("SELECT chapterId, sourceOffset FROM reading_positions WHERE bookId = 'book'").use {
            assertTrue(it.moveToFirst())
            assertEquals("chapter-1", it.getString(0))
            assertEquals(0, it.getInt(1))
        }
        listOf("reading_preferences", "reading_marks", "task_records", "chapter_search_fts").forEach { table ->
            migrated.query("SELECT COUNT(*) FROM $table").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
        migrated.close()
    }
}
