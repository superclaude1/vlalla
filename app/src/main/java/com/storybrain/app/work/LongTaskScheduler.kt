package com.storybrain.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.storybrain.app.data.StoryRepository
import com.storybrain.app.data.TaskStatus
import java.util.concurrent.TimeUnit

class LongTaskScheduler(context: Context, private val repository: StoryRepository) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    suspend fun enqueueAnalysis(bookId: String, requestedChapterCount: Int? = null): Boolean {
        val chapterIds = repository.queueAnalysis(bookId, requestedChapterCount)
        if (chapterIds.isEmpty()) return false
        val input = Data.Builder()
            .putString(WorkContracts.KEY_BOOK_ID, bookId)
            .putInt(WorkContracts.KEY_REQUESTED_CHAPTER_COUNT, requestedChapterCount ?: WorkContracts.NO_CHAPTER_COUNT)
            .build()
        val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setInputData(input)
            .addTag(WorkContracts.TAG_ANALYSIS)
            .addTag(WorkContracts.analysisName(bookId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WorkContracts.analysisName(bookId), ExistingWorkPolicy.KEEP, request)
        LocalDiagnostics.event("analysis_enqueued", "bookId" to bookId, "chapters" to chapterIds.size)
        return true
    }

    suspend fun cancelAnalysis(bookId: String) {
        repository.cancelAnalysisTasks(bookId)
        workManager.cancelUniqueWork(WorkContracts.analysisName(bookId))
        LocalDiagnostics.event("analysis_cancelled", "bookId" to bookId)
    }

    suspend fun enqueueTts(bookId: String, chapterId: String) {
        repository.updateTtsStatus(chapterId, TaskStatus.QUEUED)
        val input = Data.Builder()
            .putString(WorkContracts.KEY_BOOK_ID, bookId)
            .putString(WorkContracts.KEY_CHAPTER_ID, chapterId)
            .build()
        val request = OneTimeWorkRequestBuilder<ChapterTtsWorker>()
            .setInputData(input)
            .addTag(WorkContracts.TAG_TTS)
            .addTag(WorkContracts.ttsName(chapterId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WorkContracts.ttsName(chapterId), ExistingWorkPolicy.KEEP, request)
        LocalDiagnostics.event("tts_enqueued", "bookId" to bookId, "chapterId" to chapterId)
    }

    suspend fun cancelTts(chapterId: String) {
        repository.cancelTtsTask(chapterId)
        workManager.cancelUniqueWork(WorkContracts.ttsName(chapterId))
        LocalDiagnostics.event("tts_cancelled", "chapterId" to chapterId)
    }

    fun cancelBook(bookId: String, chapterIds: Iterable<String>) {
        workManager.cancelUniqueWork(WorkContracts.analysisName(bookId))
        chapterIds.forEach { workManager.cancelUniqueWork(WorkContracts.ttsName(it)) }
    }

    fun scheduleMaintenance() {
        val request = OneTimeWorkRequestBuilder<MaintenanceWorker>().build()
        workManager.enqueueUniqueWork(WorkContracts.MAINTENANCE_WORK, ExistingWorkPolicy.KEEP, request)
    }
}
