package com.storybrain.app

import android.app.Application
import com.storybrain.app.data.AppDatabase
import com.storybrain.app.data.StoryRepository
import com.storybrain.app.work.LongTaskScheduler
import com.storybrain.app.work.TaskNotifications

class StoryBrainApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { StoryRepository(database) }
    val longTaskScheduler by lazy { LongTaskScheduler(this, repository) }

    override fun onCreate() {
        super.onCreate()
        TaskNotifications.createChannel(this)
        longTaskScheduler.scheduleMaintenance()
    }
}
