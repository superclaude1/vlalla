package com.storybrain.app.data

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration5To6Test {
    private val databaseName = "migration-5-6-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun preservesExistingBookAndAddsNullableCoverPath() {
        helper.createDatabase(databaseName, 5).apply {
            insert("books", 0, ContentValues().apply {
                put("id", "book")
                put("title", "迁移测试小说")
                put("sourceName", "test.txt")
                put("importedAt", 12L)
                put("chapterCount", 1)
                put("totalChars", 20L)
                put("currentChapterIndex", 0)
                put("analysisCompleted", 1)
            })
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            AppDatabase.MIGRATION_5_6
        )

        migrated.query("SELECT title, coverPath FROM books WHERE id = 'book'").use {
            assertTrue(it.moveToFirst())
            assertEquals("迁移测试小说", it.getString(0))
            assertNull(it.getString(1))
        }
        migrated.close()
    }
}
