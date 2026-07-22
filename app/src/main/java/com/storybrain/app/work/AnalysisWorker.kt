package com.storybrain.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.storybrain.app.StoryBrainApplication
import com.storybrain.app.analysis.LlmStoryAnalyzer
import com.storybrain.app.data.TaskStatus
import com.storybrain.app.settings.LlmSettingsStore
import java.io.IOException
import kotlinx.coroutines.CancellationException

class AnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val bookId = inputData.getString(WorkContracts.KEY_BOOK_ID) ?: return Result.failure()
        val requested = inputData.getInt(WorkContracts.KEY_REQUESTED_CHAPTER_COUNT, WorkContracts.NO_CHAPTER_COUNT)
            .takeUnless { it == WorkContracts.NO_CHAPTER_COUNT }
        val repository = (applicationContext as StoryBrainApplication).repository
        val workName = WorkContracts.analysisName(bookId)
        val analyzer = LlmStoryAnalyzer(repository, LlmSettingsStore(applicationContext))
        repository.updateTaskRecord(workName, TaskStatus.RUNNING, stage = "准备章节")
        setForeground(TaskNotifications.foreground(applicationContext, bookId.hashCode(), "正在分析小说", "准备章节…"))
        return try {
            analyzer.analyzeNext(bookId, requested) { completed, total ->
                val stage = "已完成 $completed/$total 章"
                setProgress(WorkContracts.progress(completed, total, stage))
                repository.updateTaskRecord(workName, TaskStatus.RUNNING, completed, total, stage)
                setForeground(TaskNotifications.foreground(applicationContext, bookId.hashCode(), "正在分析小说", stage, completed, total))
            }
            val task = repository.getTaskRecord(workName)
            repository.updateTaskRecord(
                workName,
                TaskStatus.COMPLETED,
                completed = task?.total ?: task?.completed,
                stage = "分析已完成"
            )
            LocalDiagnostics.event("analysis_completed", "bookId" to bookId)
            Result.success()
        } catch (cancelled: CancellationException) {
            repository.cancelAnalysisTasks(bookId)
            repository.updateTaskRecord(workName, TaskStatus.CANCELLED, stage = "已取消")
            throw cancelled
        } catch (error: Throwable) {
            LocalDiagnostics.failure("analysis_failed", error, "bookId" to bookId, "attempt" to runAttemptCount)
            if (error is IOException && runAttemptCount < 2) {
                repository.queueAnalysis(bookId, requested)
                repository.updateTaskRecord(
                    workName,
                    TaskStatus.QUEUED,
                    stage = "网络异常，等待重试",
                    errorCode = error::class.simpleName,
                    errorMessage = error.message
                )
                Result.retry()
            } else {
                repository.failAnalysisTasks(bookId)
                repository.updateTaskRecord(
                    workName,
                    TaskStatus.FAILED,
                    stage = "分析失败",
                    errorCode = error::class.simpleName,
                    errorMessage = error.message
                )
                Result.failure()
            }
        }
    }
}
