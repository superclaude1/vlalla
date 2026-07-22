package com.storybrain.app.work

import android.content.Context
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.storybrain.app.StoryBrainApplication
import com.storybrain.app.data.TaskRecordEntity
import com.storybrain.app.data.TaskStatus
import com.storybrain.app.data.TaskType
import com.storybrain.app.tts.ChapterTtsEngine
import java.io.File
import java.util.concurrent.TimeUnit

class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as StoryBrainApplication
        val repository = app.repository
        val manager = WorkManager.getInstance(applicationContext)
        return runCatching {
            repository.ensureDefaultTtsProfiles()
            ChapterTtsEngine(applicationContext, repository).recoverAndCleanup(repository.getAllChapterIds().toSet())

            repository.deleteOldTaskRecords(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))

            repository.getActiveAnalysisTasks().groupBy { it.bookId }.forEach { (bookId, tasks) ->
                val workName = WorkContracts.analysisName(bookId)
                if (!manager.hasActiveWork(workName)) {
                    repository.updateAnalysisStatus(tasks.map { it.id }, TaskStatus.FAILED)
                    repository.updateTaskRecord(workName, TaskStatus.FAILED, stage = "任务已中断，可重试")
                } else if (repository.getTaskRecord(workName) == null) {
                    val book = repository.getBook(bookId) ?: return@forEach
                    repository.upsertTaskRecord(
                        TaskRecordEntity(
                            workName = workName,
                            type = TaskType.ANALYSIS.name,
                            bookId = bookId,
                            title = "分析《${book.title}》",
                            status = TaskStatus.RUNNING.name,
                            total = tasks.size,
                            stage = "正在恢复任务"
                        )
                    )
                }
            }
            repository.getActiveTtsTasks().forEach { task ->
                val workName = WorkContracts.ttsName(task.id)
                if (!manager.hasActiveWork(workName)) {
                    val chapter = repository.getChapter(task.id)
                    val hasManifest = chapter?.ttsManifestPath?.let(::File)?.exists() == true
                    repository.updateTtsStatus(task.id, if (hasManifest) TaskStatus.COMPLETED else TaskStatus.FAILED)
                    repository.updateTaskRecord(
                        workName,
                        if (hasManifest) TaskStatus.COMPLETED else TaskStatus.FAILED,
                        stage = if (hasManifest) "配音已恢复" else "任务已中断，可重试"
                    )
                } else if (repository.getTaskRecord(workName) == null) {
                    val chapter = repository.getChapter(task.id) ?: return@forEach
                    val book = repository.getBook(task.bookId) ?: return@forEach
                    repository.upsertTaskRecord(
                        TaskRecordEntity(
                            workName = workName,
                            type = TaskType.TTS.name,
                            bookId = task.bookId,
                            chapterId = task.id,
                            title = "《${book.title}》· ${chapter.title}",
                            status = TaskStatus.RUNNING.name,
                            stage = "正在恢复任务"
                        )
                    )
                }
            }

            repository.getActiveTaskRecords().forEach { record ->
                if (!manager.hasActiveWork(record.workName)) {
                    repository.updateTaskRecord(record.workName, TaskStatus.FAILED, stage = "任务已中断，可重试")
                }
            }

            var scheduledIndexes = 0
            for (book in repository.getBooks()) {
                if (scheduledIndexes >= 3) break
                if (repository.countBookChaptersNeedingSearchIndex(book.id) > 0 &&
                    app.longTaskScheduler.enqueueSearchIndex(book.id)
                ) {
                    scheduledIndexes++
                }
            }

            // Compatibility backfill is deliberately bounded to avoid unbounded startup work.
            val preferences = applicationContext.getSharedPreferences("maintenance_v1", Context.MODE_PRIVATE)
            val completed = preferences.getStringSet("memory_backfill_books", emptySet()).orEmpty().toMutableSet()
            repository.getBooks().asSequence().filterNot { it.id in completed }.take(3).forEach { book ->
                runCatching { repository.backfillAnalysisMemories(book.id) }
                    .onSuccess { completed += book.id }
            }
            preferences.edit { putStringSet("memory_backfill_books", completed) }
            LocalDiagnostics.event("maintenance_completed")
            Result.success()
        }.getOrElse { error ->
            LocalDiagnostics.failure("maintenance_failed", error)
            Result.retry()
        }
    }

    private fun WorkManager.hasActiveWork(name: String): Boolean =
        getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS).any { info ->
            info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.BLOCKED
        }
}
