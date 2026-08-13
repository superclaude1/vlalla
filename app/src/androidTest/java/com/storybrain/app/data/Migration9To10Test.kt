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
class Migration9To10Test {
    private val databaseName = "migration-9-10-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun addsSelectedModelToEveryApiProfile() {
        val old = helper.createDatabase(databaseName, 9)
        old.insert("llm_api_profiles", 0, ContentValues().apply {
            put("id", "api-a")
            put("displayName", "API A")
            put("baseUrl", "https://a.example/v1")
            put("createdAt", 1L)
            put("updatedAt", 1L)
        })
        old.close()

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            10,
            true,
            AppDatabase.MIGRATION_9_10
        )

        migrated.query("SELECT selectedModel FROM llm_api_profiles WHERE id = 'api-a'").use {
            it.moveToFirst()
            assertEquals("", it.getString(0))
        }
        migrated.close()
    }
}
