package com.storybrain.app

import android.app.Application
import com.storybrain.app.data.AppDatabase
import com.storybrain.app.data.StoryRepository

class StoryBrainApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { StoryRepository(database) }
}

