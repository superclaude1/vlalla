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
class Migration6To7Test {
    private val databaseName = "migration-6-7-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migratesLegacyMemoryChapterRangeAndCharacterIdsIntoTraceableEvidence() {
        helper.createDatabase(databaseName, 6).apply {
            insert("books", 0, ContentValues().apply {
                put("id", "book")
                put("title", "迁移测试小说")
                put("sourceName", "test.txt")
                put("importedAt", 1L)
                put("chapterCount", 10)
                put("totalChars", 1000L)
                put("currentChapterIndex", 2)
                put("analysisCompleted", 5)
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
                put("lastChapterIndex", 9)
                put("confidence", 0.9)
                put("importanceScore", 0.0)
                put("importanceReason", "")
            })
            insert("memory_items", 0, ContentValues().apply {
                put("id", "legacy-memory")
                put("bookId", "book")
                put("type", "PLOT")
                put("title", "旧证据")
                put("content", "林清在渡口救了人")
                put("chapterStartIndex", 1)
                put("chapterEndIndex", 2)
                put("characterIdsJson", "[\"character\"]")
                put("sourceKey", "plot:legacy")
                put("searchTerms", "渡口")
                put("editable", 0)
                put("createdAt", 10L)
                put("updatedAt", 20L)
            })
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            7,
            true,
            AppDatabase.MIGRATION_6_7
        )
        migrated.query(
            "SELECT memoryId, characterId, chapterStartIndex, chapterEndIndex, characterIdsJson, source, confidence, invalidatedAt, spoilerBoundaryChapterIndex FROM character_memory_evidence"
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("legacy-memory", it.getString(0))
            assertEquals("character", it.getString(1))
            assertEquals(1, it.getInt(2))
            assertEquals(2, it.getInt(3))
            assertEquals("[\"character\"]", it.getString(4))
            assertEquals("LEGACY_MEMORY_ITEM", it.getString(5))
            assertEquals(0.5f, it.getFloat(6))
            assertTrue(it.isNull(7))
            assertEquals(2, it.getInt(8))
        }
        migrated.close()
    }
}
