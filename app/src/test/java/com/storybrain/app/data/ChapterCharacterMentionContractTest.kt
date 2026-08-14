package com.storybrain.app.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterCharacterMentionContractTest {
    @Test
    fun databaseRegistersChapterMentionEntityAnd4To5Migration() {
        val database = File("src/main/java/com/storybrain/app/data/AppDatabase.kt").readText()
        val entities = File("src/main/java/com/storybrain/app/data/Entities.kt").readText()
        assertTrue(entities.contains("data class ChapterCharacterMentionEntity"))
        assertTrue(database.contains("ChapterCharacterMentionEntity::class"))
        assertTrue(database.contains("version = 12"))
        assertTrue(database.contains("Migration(4, 5)"))
        assertTrue(database.contains("Migration(5, 6)"))
        assertTrue(database.contains("Migration(6, 7)"))
        assertTrue(database.contains("Migration(7, 8)"))
        assertTrue(database.contains("Migration(8, 9)"))
        assertTrue(database.contains("Migration(9, 10)"))
        assertTrue(database.contains("chapter_character_mentions"))
    }

    @Test
    fun analysisSaveContractPersistsMentionsAndExposesChapterFlow() {
        val repository = File("src/main/java/com/storybrain/app/data/StoryRepository.kt").readText()
        val dao = File("src/main/java/com/storybrain/app/data/StoryDao.kt").readText()
        val viewModel = File("src/main/java/com/storybrain/app/ui/AppViewModel.kt").readText()
        assertTrue(repository.contains("mentions: List<ChapterCharacterMentionEntity>"))
        assertTrue(repository.contains("insertChapterCharacterMentions(mentions)"))
        assertTrue(dao.contains("observeChapterCharacters"))
        assertTrue(viewModel.contains("chapterCharacters(chapterId: String)"))
    }

    @Test
    fun readerPrioritizesCurrentChapterCandidates() {
        val screens = File("src/main/java/com/storybrain/app/ui/Screens.kt").readText()
        assertTrue(screens.contains("viewModel.chapterCharacters(chapterId)"))
        assertTrue(screens.contains("chapterMentionCharacters"))
        assertTrue(screens.contains("chapterMentionCharacters + characters") || screens.contains("chapterMentionCharacters, characters"))
    }
}
