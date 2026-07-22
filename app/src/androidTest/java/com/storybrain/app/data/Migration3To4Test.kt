package com.storybrain.app.data

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration3To4Test {
    private val databaseName = "migration-3-4-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun preservesStoryDataAndCreatesTtsConfiguration() {
        helper.createDatabase(databaseName, 3).apply {
            insertBook()
            insertChapter()
            insertCharacter("edge-character", "edge:zh-CN-YunxiNeural")
            insertCharacter("local-character", "local:legacy")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            AppDatabase.MIGRATION_3_4
        )

        migrated.query(
            "SELECT canonicalName, importanceScore, importanceReason FROM characters WHERE id = 'edge-character'"
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("林清", it.getString(0))
            assertEquals(0f, it.getFloat(1), 0f)
            assertEquals("", it.getString(2))
        }
        migrated.query("SELECT COUNT(*) FROM tts_provider_profiles").use {
            assertTrue(it.moveToFirst())
            assertEquals(3, it.getInt(0))
        }
        migrated.query(
            "SELECT baseUrl, model FROM tts_provider_profiles WHERE id = 'fish-default'"
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("https://api.fish.audio", it.getString(0))
            assertEquals("s2.1-pro-free", it.getString(1))
        }
        migrated.query("SELECT COUNT(*) FROM tts_profile_voice_pool WHERE profileId = 'edge-default'").use {
            assertTrue(it.moveToFirst())
            assertEquals(8, it.getInt(0))
        }
        migrated.query(
            "SELECT profileId, voiceId, active, userConfirmed FROM character_voice_bindings WHERE characterId = 'edge-character'"
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("edge-default", it.getString(0))
            assertEquals("zh-CN-YunxiNeural", it.getString(1))
            assertEquals(1, it.getInt(2))
            assertEquals(1, it.getInt(3))
        }
        migrated.query("SELECT COUNT(*) FROM character_voice_bindings WHERE characterId = 'local-character'").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM tts_scripts").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        migrated.close()
    }

    private fun SupportSQLiteDatabase.insertBook() {
        insert("books", 0, ContentValues().apply {
            put("id", "book")
            put("title", "迁移测试小说")
            put("sourceName", "test.txt")
            put("importedAt", 1L)
            put("chapterCount", 1)
            put("totalChars", 100L)
            put("currentChapterIndex", 0)
            put("analysisCompleted", 1)
        })
    }

    private fun SupportSQLiteDatabase.insertChapter() {
        insert("chapters", 0, ContentValues().apply {
            put("id", "chapter")
            put("bookId", "book")
            put("chapterIndex", 0)
            put("title", "第一章")
            put("content", "测试正文")
            put("charCount", 4)
            put("analysisStatus", TaskStatus.COMPLETED.name)
            put("ttsStatus", TaskStatus.PENDING.name)
            putNull("ttsManifestPath")
        })
    }

    private fun SupportSQLiteDatabase.insertCharacter(id: String, voiceId: String) {
        insert("characters", 0, ContentValues().apply {
            put("id", id)
            put("bookId", "book")
            put("canonicalName", if (id == "edge-character") "林清" else "周远")
            put("aliasesJson", "[]")
            put("gender", if (id == "edge-character") "FEMALE" else "MALE")
            put("personality", "冷静")
            put("voiceId", voiceId)
            put("firstChapterIndex", 0)
            put("lastChapterIndex", 0)
            put("confidence", 0.9f)
        })
    }
}
