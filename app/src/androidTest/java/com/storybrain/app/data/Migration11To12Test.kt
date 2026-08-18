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
class Migration11To12Test {
    private val databaseName = "migration-11-12-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun addsNullableCoverPathWithoutChangingExistingBooks() {
        helper.createDatabase(databaseName, 11).apply {
            insert("books", 0, ContentValues().apply {
                put("id", "book")
                put("title", "旧书")
                put("sourceName", "old.txt")
                put("importedAt", 100L)
                put("chapterCount", 1)
                put("totalChars", 10L)
                put("currentChapterIndex", 0)
                put("analysisCompleted", 0)
            })
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            12,
            true,
            AppDatabase.MIGRATION_11_12
        )

        migrated.query("SELECT title, coverPath FROM books WHERE id = 'book'").use {
            assertTrue(it.moveToFirst())
            assertEquals("旧书", it.getString(0))
            assertTrue(it.isNull(1))
        }
        migrated.close()
    }
}
