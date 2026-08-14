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

/** v10 → v11：阅读器表增量迁移，老数据（书/章节）无损。 */
@RunWith(AndroidJUnit4::class)
class Migration10To11Test {
    private val databaseName = "migration-10-11-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun addsReadingTablesAndInitializesPositionsFromCurrentChapter() {
        helper.createDatabase(databaseName, 10).apply {
            insert("books", 0, ContentValues().apply {
                put("id", "book")
                put("title", "诡秘之主")
                put("sourceName", "lord.txt")
                put("importedAt", 100L)
                put("chapterCount", 2)
                put("totalChars", 500L)
                put("currentChapterIndex", 1)
                put("analysisCompleted", 2)
            })
            insert("chapters", 0, ContentValues().apply {
                put("id", "ch1")
                put("bookId", "book")
                put("chapterIndex", 0)
                put("title", "序章")
                put("content", "正文一")
                put("charCount", 3)
                put("analysisStatus", "DONE")
                put("ttsStatus", "NONE")
            })
            insert("chapters", 0, ContentValues().apply {
                put("id", "ch2")
                put("bookId", "book")
                put("chapterIndex", 1)
                put("title", "第一章")
                put("content", "正文二")
                put("charCount", 3)
                put("analysisStatus", "DONE")
                put("ttsStatus", "NONE")
            })
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            11,
            true,
            AppDatabase.MIGRATION_10_11
        )

        migrated.query("SELECT title FROM books WHERE id = 'book'").use {
            assertTrue(it.moveToFirst())
            assertEquals("诡秘之主", it.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM chapters WHERE bookId = 'book'").use {
            assertTrue(it.moveToFirst())
            assertEquals(2, it.getInt(0))
        }
        // 阅读位置按 currentChapterIndex 初始化
        migrated.query("SELECT chapterId, sourceOffset FROM reading_positions WHERE bookId = 'book'").use {
            assertTrue(it.moveToFirst())
            assertEquals("ch2", it.getString(0))
            assertEquals(0, it.getInt(1))
        }
        // 新表存在
        migrated.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('reading_preferences','reading_positions','reading_marks')").use {
            assertTrue(it.count == 3)
        }
        // 老表原样保留
        migrated.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('llm_api_profiles','task_runs')").use {
            assertTrue(it.count == 2)
        }
        migrated.close()
    }
}
