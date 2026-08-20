package com.storybrain.app.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRunPersistenceContractTest {
    private val sourceRoot = File("src/main/java/com/storybrain/app")

    @Test
    fun roomModelAndMigrationPersistTaskRunsAndEventsFromVersionFive() {
        val entities = sourceRoot.resolve("data/Entities.kt").readText()
        val database = sourceRoot.resolve("data/AppDatabase.kt").readText()
        val dao = sourceRoot.resolve("data/StoryDao.kt").readText()
        val repository = sourceRoot.resolve("data/StoryRepository.kt").readText()

        assertTrue(entities.contains("data class TaskRunEntity"))
        assertTrue(entities.contains("data class TaskEventEntity"))
        assertTrue(database.contains("TaskRunEntity::class"))
        assertTrue(database.contains("TaskEventEntity::class"))
        assertTrue(database.contains("version = 13"))
        assertTrue(database.contains("Migration(5, 6)"))
        assertTrue(database.contains("Migration(8, 9)"))
        assertTrue(database.contains("CREATE TABLE IF NOT EXISTS `task_runs`"))
        assertTrue(database.contains("CREATE TABLE IF NOT EXISTS `task_events`"))
        assertTrue(dao.contains("observeTaskEvents"))
        assertTrue(dao.contains("clearTaskEvents"))
        assertTrue(repository.contains("recordTaskFailure"))
        assertTrue(repository.contains("observeTaskEvents"))
        assertTrue(repository.contains("clearTaskEvents"))
    }

    @Test
    fun persistedFailureContractHasNoCredentialOrPayloadFields() {
        val entities = sourceRoot.resolve("data/Entities.kt").readText()
        val repository = sourceRoot.resolve("data/StoryRepository.kt").readText()
        val forbidden = listOf("apiKey", "API Key", "requestBody", "responseBody", "content: String")
        forbidden.forEach { token ->
            assertTrue("task event model must not contain $token", !entities.substringAfter("data class TaskEventEntity").substringBefore("enum class TaskStatus").contains(token))
            assertTrue("failure persistence must not contain $token", !repository.substringAfter("recordTaskFailure").take(2_000).contains(token))
        }
    }

    @Test
    fun taskEventsCanPersistPerBatchUsageWithoutStoringPayloads() {
        val entities = sourceRoot.resolve("data/Entities.kt").readText()
        val database = sourceRoot.resolve("data/AppDatabase.kt").readText()
        val repository = sourceRoot.resolve("data/StoryRepository.kt").readText()

        assertTrue(entities.contains("promptTokens: Int?"))
        assertTrue(entities.contains("usageQuality: String?"))
        assertTrue(entities.contains("requestId: String?"))
        assertTrue(entities.contains("responseModel: String?"))
        assertTrue(database.contains("MIGRATION_8_9"))
        assertTrue(repository.contains("recordTaskUsage"))
    }

    @Test
    fun analysisDeltaCommitsBatchCompletionInTheSameTransaction() {
        val repository = sourceRoot.resolve("data/StoryRepository.kt").readText()
        val saveDelta = repository.substringAfter("suspend fun saveAnalysisDelta(").substringBefore("suspend fun backfillAnalysisMemories")

        assertTrue(saveDelta.contains("chapterIds: List<String>"))
        assertTrue(saveDelta.contains("database.withTransaction"))
        assertTrue(saveDelta.contains("dao.updateAnalysisStatus(chapterIds, TaskStatus.COMPLETED.name)"))
    }

    @Test
    fun interruptedAnalysisOnlyTransitionsChaptersThatAreStillRunning() {
        val dao = sourceRoot.resolve("data/StoryDao.kt").readText()
        val analyzer = sourceRoot.resolve("analysis/LlmStoryAnalyzer.kt").readText()

        assertTrue(dao.contains("analysisStatus = '${'$'}{TaskStatus.RUNNING.name}'").not())
        assertTrue(dao.contains("AND analysisStatus = 'RUNNING'"))
        assertTrue(analyzer.contains("updateRunningAnalysisStatus"))
    }

    @Test
    fun databaseOpenRecoversInterruptedAnalysisWithoutSchemaUpgrade() {
        val database = sourceRoot.resolve("data/AppDatabase.kt").readText()

        assertTrue(database.contains("version = 13"))
        assertTrue(database.contains("addCallback(ANALYSIS_RECOVERY_CALLBACK)"))
        assertTrue(database.contains("override fun onOpen(db: SupportSQLiteDatabase)"))
        assertTrue(database.contains("analysisStatus = 'COMPLETED'"))
        assertTrue(database.contains("chapters.chapterIndex < books.analysisCompleted"))
        assertTrue(database.contains("analysisStatus = 'PENDING'"))
        assertTrue(database.contains("taskType IN ('ANALYSIS', 'ANALYSIS_VERIFY', 'JSON_REPAIR') AND status = 'RUNNING'"))
        assertTrue(database.contains("status = 'FAILED'"))
    }

    @Test
    fun oneAnalysisActionOwnsOneRunAndAllEventsReferenceIt() {
        val entities = sourceRoot.resolve("data/Entities.kt").readText()
        val repository = sourceRoot.resolve("data/StoryRepository.kt").readText()
        val analyzer = sourceRoot.resolve("analysis/LlmStoryAnalyzer.kt").readText()
        val viewModel = sourceRoot.resolve("ui/AppViewModel.kt").readText()

        assertTrue(entities.contains("enum class TaskRunStatus { RUNNING, FAILED, COMPLETED, CANCELLED }"))
        assertTrue(repository.contains("suspend fun startTaskRun("))
        assertTrue(repository.contains("suspend fun finishTaskRun("))
        assertTrue(repository.substringAfter("suspend fun recordTaskUsage(").substringBefore("suspend fun clearTaskEvents").contains("runId: String"))
        assertTrue(repository.substringAfter("suspend fun recordTaskUsage(").substringBefore("suspend fun clearTaskEvents").contains("insertTaskRun").not())
        assertTrue(analyzer.contains("val runId = repository.startTaskRun(TaskRunType.ANALYSIS, bookId)"))
        assertTrue(analyzer.contains("repository.finishTaskRun(runId, TaskRunStatus.CANCELLED)"))
        assertTrue(analyzer.contains("repository.recordTaskFailure(") && analyzer.contains("runId, TaskRunType.ANALYSIS"))
        assertTrue(viewModel.substringAfter("private fun publishAnalysisFailure").substringBefore("@Synchronized").contains("recordTaskFailure").not())
    }
}
