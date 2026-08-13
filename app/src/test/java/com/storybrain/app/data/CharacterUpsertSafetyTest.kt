package com.storybrain.app.data

import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterUpsertSafetyTest {
    @Test
    fun daoUsesUpsertForCharactersInsteadOfReplaceDeleteInsert() {
        val source = java.io.File("src/main/java/com/storybrain/app/data/StoryDao.kt").readText()
        val start = source.indexOf("@Upsert\n    suspend fun insertCharacters")
        val end = source.indexOf("@Insert(onConflict = OnConflictStrategy.REPLACE)\n    suspend fun insertRelations", start)
        val method = source.substring(start, end)
        assertTrue(method.contains("suspend fun insertCharacters"))
        assertTrue(!method.contains("OnConflictStrategy.REPLACE"))
    }
}
