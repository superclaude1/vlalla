package com.storybrain.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.storybrain.app.StoryBrainApplication
import com.storybrain.app.data.TaskStatus
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

            repository.getActiveAnalysisTasks().groupBy { it.bookId }.forEach { (bookId, tasks) ->
                if (!manager.hasActiveWork(WorkContracts.analysisName(bookId))) {
                    repository.updateAnalysisStatus(tasks.map { it.id }, TaskStatus.FAILED)
                }
            }
            repository.getActiveTtsTasks().forEach { task ->
                if (!manager.hasActiveWork(WorkContracts.ttsName(task.id))) {
                    val chapter = repository.getChapter(task.id)
                    val hasManifest = chapter?.ttsManifestPath?.let(::File)?.exists() == true
                    repository.updateTtsStatus(task.id, if (hasManifest) TaskStatus.COMPLETED else TaskStatus.FAILED)
                }
            }

            // Compatibility backfill is deliberately bounded to avoid unbounded startup work.
            val preferences = applicationContext.getSharedPreferences("maintenance_v1", Context.MODE_PRIVATE)
            val completed = preferences.getStringSet("memory_backfill_books", emptySet()).orEmpty().toMutableSet()
            repository.getBooks().asSequence().filterNot { it.id in completed }.take(3).forEach { book ->
                runCatching { repository.backfillAnalysisMemories(book.id) }
                    .onSuccess { completed += book.id }
            }
            preferences.edit().putStringSet("memory_backfill_books", completed).apply()
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
