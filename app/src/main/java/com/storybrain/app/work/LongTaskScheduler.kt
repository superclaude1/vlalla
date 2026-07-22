package com.storybrain.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.storybrain.app.data.StoryRepository
import com.storybrain.app.data.TaskRecordEntity
import com.storybrain.app.data.TaskStatus
import com.storybrain.app.data.TaskType
import java.util.concurrent.TimeUnit

class LongTaskScheduler(context: Context, private val repository: StoryRepository) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    suspend fun enqueueAnalysis(bookId: String, requestedChapterCount: Int? = null): Boolean {
        val workName = WorkContracts.analysisName(bookId)
        val activeRecord = repository.getTaskRecord(workName)?.let { TaskStatus.fromStorage(it.status) }
        if (activeRecord == TaskStatus.QUEUED || activeRecord == TaskStatus.RUNNING) return false
        val chapterIds = repository.queueAnalysis(bookId, requestedChapterCount)
        if (chapterIds.isEmpty()) return false
        val book = repository.getBook(bookId) ?: return false
        val now = System.currentTimeMillis()
        repository.upsertTaskRecord(
            TaskRecordEntity(
                workName = workName,
                type = TaskType.ANALYSIS.name,
                bookId = bookId,
                title = "分析《${book.title}》",
                status = TaskStatus.QUEUED.name,
                total = chapterIds.size,
                stage = "等待开始",
                createdAt = now,
                updatedAt = now
            )
        )
        val input = Data.Builder()
            .putString(WorkContracts.KEY_BOOK_ID, bookId)
            .putInt(WorkContracts.KEY_REQUESTED_CHAPTER_COUNT, requestedChapterCount ?: WorkContracts.NO_CHAPTER_COUNT)
            .build()
        val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setInputData(input)
            .addTag(WorkContracts.TAG_ANALYSIS)
            .addTag(workName)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
        LocalDiagnostics.event("analysis_enqueued", "bookId" to bookId, "chapters" to chapterIds.size)
        return true
    }

    suspend fun cancelAnalysis(bookId: String) {
        repository.cancelAnalysisTasks(bookId)
        val workName = WorkContracts.analysisName(bookId)
        repository.updateTaskRecord(workName, TaskStatus.CANCELLED, stage = "已取消")
        workManager.cancelUniqueWork(workName)
        LocalDiagnostics.event("analysis_cancelled", "bookId" to bookId)
    }

    suspend fun enqueueTts(bookId: String, chapterId: String) {
        val workName = WorkContracts.ttsName(chapterId)
        val activeRecord = repository.getTaskRecord(workName)?.let { TaskStatus.fromStorage(it.status) }
        if (activeRecord == TaskStatus.QUEUED || activeRecord == TaskStatus.RUNNING) return
        val chapter = repository.getChapter(chapterId) ?: return
        val book = repository.getBook(bookId) ?: return
        repository.updateTtsStatus(chapterId, TaskStatus.QUEUED)
        val now = System.currentTimeMillis()
        repository.upsertTaskRecord(
            TaskRecordEntity(
                workName = workName,
                type = TaskType.TTS.name,
                bookId = bookId,
                chapterId = chapterId,
                title = "《${book.title}》· ${chapter.title}",
                status = TaskStatus.QUEUED.name,
                stage = "等待生成",
                createdAt = now,
                updatedAt = now
            )
        )
        val input = Data.Builder()
            .putString(WorkContracts.KEY_BOOK_ID, bookId)
            .putString(WorkContracts.KEY_CHAPTER_ID, chapterId)
            .build()
        val request = OneTimeWorkRequestBuilder<ChapterTtsWorker>()
            .setInputData(input)
            .addTag(WorkContracts.TAG_TTS)
            .addTag(workName)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
        LocalDiagnostics.event("tts_enqueued", "bookId" to bookId, "chapterId" to chapterId)
    }

    suspend fun cancelTts(chapterId: String) {
        repository.cancelTtsTask(chapterId)
        val workName = WorkContracts.ttsName(chapterId)
        repository.updateTaskRecord(workName, TaskStatus.CANCELLED, stage = "已取消")
        workManager.cancelUniqueWork(workName)
        LocalDiagnostics.event("tts_cancelled", "chapterId" to chapterId)
    }

    fun cancelBook(bookId: String, chapterIds: Iterable<String>) {
        workManager.cancelUniqueWork(WorkContracts.analysisName(bookId))
        chapterIds.forEach { workManager.cancelUniqueWork(WorkContracts.ttsName(it)) }
    }

    suspend fun enqueueSearchIndex(bookId: String): Boolean {
        val total = repository.countBookChaptersNeedingSearchIndex(bookId)
        if (total <= 0) return false
        val book = repository.getBook(bookId) ?: return false
        val workName = WorkContracts.searchIndexName(bookId)
        val activeRecord = repository.getTaskRecord(workName)?.let { TaskStatus.fromStorage(it.status) }
        if (activeRecord == TaskStatus.QUEUED || activeRecord == TaskStatus.RUNNING) return false
        val now = System.currentTimeMillis()
        repository.upsertTaskRecord(
            TaskRecordEntity(
                workName = workName,
                type = TaskType.SEARCH_INDEX.name,
                bookId = bookId,
                title = "索引《${book.title}》",
                status = TaskStatus.QUEUED.name,
                total = total,
                stage = "等待建立全文索引",
                createdAt = now,
                updatedAt = now
            )
        )
        val request = OneTimeWorkRequestBuilder<SearchIndexWorker>()
            .setInputData(Data.Builder().putString(WorkContracts.KEY_BOOK_ID, bookId).build())
            .addTag(WorkContracts.TAG_SEARCH_INDEX)
            .addTag(workName)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
        return true
    }

    suspend fun cancelSearchIndex(bookId: String) {
        val workName = WorkContracts.searchIndexName(bookId)
        repository.updateTaskRecord(workName, TaskStatus.CANCELLED, stage = "已取消")
        workManager.cancelUniqueWork(workName)
    }

    fun scheduleMaintenance() {
        val request = OneTimeWorkRequestBuilder<MaintenanceWorker>().build()
        workManager.enqueueUniqueWork(WorkContracts.MAINTENANCE_WORK, ExistingWorkPolicy.KEEP, request)
    }
}
