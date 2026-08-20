package com.storybrain.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException

/** Durable analysis worker; enqueueing is wired by AppViewModel/WorkManager. */
class AnalysisWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
        val application = applicationContext as? com.storybrain.app.StoryBrainApplication
            ?: return Result.failure()
        return try {
            application.runAnalysis(bookId)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (retryable: RetryableTaskException) {
            Result.retry()
        } catch (error: Throwable) {
            Result.failure(workDataOf(KEY_ERROR to (error.message ?: "分析失败")))
        }
    }

    companion object {
        const val KEY_BOOK_ID = "book_id"
        const val KEY_ERROR = "error"
    }
}

class RetryableTaskException(message: String, cause: Throwable? = null) : Exception(message, cause)
