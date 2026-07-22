package com.storybrain.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.storybrain.app.StoryBrainApplication
import com.storybrain.app.data.TaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class SearchIndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val bookId = inputData.getString(WorkContracts.KEY_BOOK_ID) ?: return Result.failure()
        val workName = WorkContracts.searchIndexName(bookId)
        val repository = (applicationContext as StoryBrainApplication).repository
        val initialRemaining = repository.countBookChaptersNeedingSearchIndex(bookId)
        val recordedTotal = repository.getTaskRecord(workName)?.total ?: initialRemaining
        val total = maxOf(recordedTotal, initialRemaining)
        repository.updateTaskRecord(workName, TaskStatus.RUNNING, total = total, stage = "正在建立全文索引")
        return try {
            var processedThisRun = 0
            var remaining = initialRemaining
            while (remaining > 0 && processedThisRun < MAX_CHAPTERS_PER_RUN) {
                currentCoroutineContext().ensureActive()
                val processed = repository.indexNextBookChapters(bookId, BATCH_SIZE)
                if (processed <= 0) break
                processedThisRun += processed
                remaining = repository.countBookChaptersNeedingSearchIndex(bookId)
                val completed = (total - remaining).coerceIn(0, total)
                val stage = "已索引 $completed/$total 章"
                setProgress(WorkContracts.progress(completed, total, stage))
                repository.updateTaskRecord(
                    workName,
                    TaskStatus.RUNNING,
                    completed = completed,
                    total = total,
                    stage = stage
                )
            }
            remaining = repository.countBookChaptersNeedingSearchIndex(bookId)
            if (remaining > 0) {
                repository.updateTaskRecord(
                    workName,
                    TaskStatus.QUEUED,
                    completed = (total - remaining).coerceAtLeast(0),
                    total = total,
                    stage = "等待继续索引"
                )
                Result.retry()
            } else {
                repository.updateTaskRecord(
                    workName,
                    TaskStatus.COMPLETED,
                    completed = total,
                    total = total,
                    stage = "全文索引已完成"
                )
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            repository.updateTaskRecord(workName, TaskStatus.CANCELLED, stage = "已取消")
            throw cancelled
        } catch (error: Throwable) {
            repository.updateTaskRecord(
                workName,
                TaskStatus.FAILED,
                stage = "全文索引失败",
                errorCode = error::class.simpleName,
                errorMessage = error.message
            )
            Result.failure()
        }
    }

    companion object {
        private const val BATCH_SIZE = 20
        private const val MAX_CHAPTERS_PER_RUN = 500
    }
}
