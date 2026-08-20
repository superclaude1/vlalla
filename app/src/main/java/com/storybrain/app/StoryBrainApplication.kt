package com.storybrain.app

import android.app.Application
import com.storybrain.app.data.AppDatabase
import com.storybrain.app.data.StoryRepository
import com.storybrain.app.analysis.LlmStoryAnalyzer
import com.storybrain.app.settings.LlmSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StoryBrainApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { StoryRepository(database) }
    private val llmSettings by lazy { LlmSettingsStore(this, repository) }

    suspend fun runAnalysis(bookId: String) = withContext(Dispatchers.IO) {
        LlmStoryAnalyzer(repository, llmSettings).analyzeAll(bookId)
    }
}

