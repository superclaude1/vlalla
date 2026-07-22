package com.storybrain.app.data

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration2To3Test {
    private val databaseName = "migration-2-3-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun preservesOldDataAndMovesMessagesIntoLegacySession() {
        helper.createDatabase(databaseName, 2).apply {
            insert("books", 0, ContentValues().apply {
                put("id", "book")
                put("title", "迁移测试小说")
                put("sourceName", "test.txt")
                put("importedAt", 1L)
                put("chapterCount", 20)
                put("totalChars", 2_000)
                put("currentChapterIndex", 3)
                put("analysisCompleted", 15)
            })
            insert("characters", 0, ContentValues().apply {
                put("id", "character")
                put("bookId", "book")
                put("canonicalName", "林清")
                put("aliasesJson", "[]")
                put("gender", "女")
                put("personality", "冷静")
                putNull("voiceId")
                put("firstChapterIndex", 0)
                put("lastChapterIndex", 14)
                put("confidence", 0.9)
            })
            insert("chat_messages", 0, ContentValues().apply {
                put("id", "message")
                put("bookId", "book")
                put("characterId", "character")
                put("role", "user")
                put("content", "你还记得我吗？")
                put("createdAt", 10L)
            })
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            AppDatabase.MIGRATION_2_3
        )

        migrated.query("SELECT title, analysisCompleted FROM books WHERE id = 'book'").use {
            it.moveToFirst()
            assertEquals("迁移测试小说", it.getString(0))
            assertEquals(15, it.getInt(1))
        }
        migrated.query("SELECT title FROM chat_sessions WHERE id = 'legacy-character'").use {
            it.moveToFirst()
            assertEquals("旧对话", it.getString(0))
        }
        migrated.query("SELECT sessionId, content FROM chat_messages WHERE id = 'message'").use {
            it.moveToFirst()
            assertEquals("legacy-character", it.getString(0))
            assertEquals("你还记得我吗？", it.getString(1))
        }
        migrated.query("SELECT COUNT(*) FROM memory_items").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        migrated.close()
    }
}
