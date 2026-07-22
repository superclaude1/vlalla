package com.storybrain.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.storybrain.app.StoryBrainApplication
import com.storybrain.app.data.TaskRecordEntity
import com.storybrain.app.data.TaskStatus
import com.storybrain.app.data.TaskType
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StoryBrainApplication
    private val repository = app.repository
    private val scheduler = app.longTaskScheduler
    private val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)

    val tasks = repository.observeTaskRecords(cutoff)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel(task: TaskRecordEntity) {
        viewModelScope.launch {
            when (TaskType.fromStorage(task.type) ?: return@launch) {
                TaskType.ANALYSIS -> scheduler.cancelAnalysis(task.bookId)
                TaskType.TTS -> task.chapterId?.let { scheduler.cancelTts(it) }
                TaskType.SEARCH_INDEX -> scheduler.cancelSearchIndex(task.bookId)
            }
        }
    }

    fun retry(task: TaskRecordEntity) {
        viewModelScope.launch {
            when (TaskType.fromStorage(task.type) ?: return@launch) {
                TaskType.ANALYSIS -> scheduler.enqueueAnalysis(task.bookId)
                TaskType.TTS -> task.chapterId?.let { scheduler.enqueueTts(task.bookId, it) }
                TaskType.SEARCH_INDEX -> scheduler.enqueueSearchIndex(task.bookId)
            }
        }
    }

    fun clearFinished() {
        viewModelScope.launch { repository.clearFinishedTaskRecords() }
    }

    fun isActive(task: TaskRecordEntity): Boolean =
        TaskStatus.fromStorage(task.status) in setOf(TaskStatus.QUEUED, TaskStatus.RUNNING)
}
