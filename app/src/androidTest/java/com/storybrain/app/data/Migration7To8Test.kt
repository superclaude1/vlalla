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
class Migration7To8Test {
    private val databaseName = "migration-7-8-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun createsLlmProfilesAndCompositeModelIdentity() {
        helper.createDatabase(databaseName, 7).close()

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            8,
            true,
            AppDatabase.MIGRATION_7_8
        )
        migrated.insert("llm_api_profiles", 0, ContentValues().apply {
            put("id", "api-a")
            put("displayName", "主 API")
            put("baseUrl", "https://a.example/v1")
            put("createdAt", 1L)
            put("updatedAt", 1L)
        })
        listOf("same-model", "other-model").forEachIndexed { index, model ->
            migrated.insert("llm_models", 0, ContentValues().apply {
                put("apiProfileId", "api-a")
                put("modelId", model)
                put("updatedAt", index.toLong())
            })
        }
        migrated.insert("llm_api_profiles", 0, ContentValues().apply {
            put("id", "api-b")
            put("displayName", "备用 API")
            put("baseUrl", "https://b.example/v1")
            put("createdAt", 2L)
            put("updatedAt", 2L)
        })
        migrated.insert("llm_models", 0, ContentValues().apply {
            put("apiProfileId", "api-b")
            put("modelId", "same-model")
            put("updatedAt", 2L)
        })

        migrated.query("SELECT apiProfileId, modelId FROM llm_models ORDER BY apiProfileId, modelId").use {
            assertEquals(3, it.count)
            assertTrue(it.moveToFirst())
            assertEquals("api-a", it.getString(0))
            assertEquals("other-model", it.getString(1))
        }
        migrated.close()
    }
}
