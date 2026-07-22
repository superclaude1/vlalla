package com.storybrain.app

import android.app.Application
import com.storybrain.app.data.AppDatabase
import com.storybrain.app.data.StoryRepository
import com.storybrain.app.reader.ReaderRepository
import com.storybrain.app.reader.ReaderSettingsStore
import com.storybrain.app.playback.PlaybackRepository
import com.storybrain.app.playback.PlaybackStateStore
import com.storybrain.app.work.LongTaskScheduler
import com.storybrain.app.work.TaskNotifications

class StoryBrainApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { StoryRepository(database) }
    val readerSettingsStore by lazy { ReaderSettingsStore(this) }
    val readerRepository by lazy { ReaderRepository(repository, readerSettingsStore) }
    val playbackStateStore by lazy { PlaybackStateStore(this) }
    val playbackRepository by lazy { PlaybackRepository(this, repository, playbackStateStore) }
    val longTaskScheduler by lazy { LongTaskScheduler(this, repository) }

    override fun onCreate() {
        super.onCreate()
        TaskNotifications.createChannel(this)
        playbackRepository
        longTaskScheduler.scheduleMaintenance()
    }
}
