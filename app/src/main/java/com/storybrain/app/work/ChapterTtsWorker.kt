package com.storybrain.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.storybrain.app.StoryBrainApplication
import com.storybrain.app.data.TaskStatus
import com.storybrain.app.settings.TtsSettingsStore
import com.storybrain.app.tts.ChapterTtsEngine
import com.storybrain.app.tts.TtsProviderException
import java.io.File
import kotlinx.coroutines.CancellationException

class ChapterTtsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val bookId = inputData.getString(WorkContracts.KEY_BOOK_ID) ?: return Result.failure()
        val chapterId = inputData.getString(WorkContracts.KEY_CHAPTER_ID) ?: return Result.failure()
        val repository = (applicationContext as StoryBrainApplication).repository
        val engine = ChapterTtsEngine(applicationContext, repository, TtsSettingsStore(applicationContext))
        setForeground(TaskNotifications.foreground(applicationContext, chapterId.hashCode(), "正在生成章节配音", "准备文本…"))
        return try {
            engine.generate(
                bookId,
                chapterId,
                onProgress = { completed, total ->
                    setProgress(WorkContracts.progress(completed, total, "正在生成 $completed/$total 段"))
                },
                onStage = { stage ->
                    setProgress(WorkContracts.progress(0, 0, stage))
                    setForeground(TaskNotifications.foreground(applicationContext, chapterId.hashCode(), "正在生成章节配音", stage))
                }
            )
            LocalDiagnostics.event("tts_completed", "bookId" to bookId, "chapterId" to chapterId)
            Result.success()
        } catch (cancelled: CancellationException) {
            repository.cancelTtsTask(chapterId)
            throw cancelled
        } catch (error: Throwable) {
            LocalDiagnostics.failure("tts_failed", error, "chapterId" to chapterId, "attempt" to runAttemptCount)
            val retryable = (error as? TtsProviderException)?.retryable == true
            if (retryable && runAttemptCount < 2) {
                repository.updateTtsStatus(chapterId, TaskStatus.QUEUED)
                Result.retry()
            } else {
                val chapter = repository.getChapter(chapterId)
                val hasPreviousAudio = chapter?.ttsManifestPath?.let(::File)?.exists() == true
                repository.updateTtsStatus(chapterId, if (hasPreviousAudio) TaskStatus.COMPLETED else TaskStatus.FAILED)
                Result.failure()
            }
        }
    }
}
