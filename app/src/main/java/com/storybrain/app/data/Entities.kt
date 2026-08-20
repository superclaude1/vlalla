package com.storybrain.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "llm_api_profiles", indices = [Index("updatedAt")])
data class LlmApiProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val baseUrl: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val selectedModel: String = ""
)

@Entity(
    tableName = "llm_models",
    primaryKeys = ["apiProfileId", "modelId"],
    foreignKeys = [ForeignKey(
        entity = LlmApiProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["apiProfileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("apiProfileId")]
)
data class LlmModelEntity(
    val apiProfileId: String,
    val modelId: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sourceName: String,
    val importedAt: Long,
    val chapterCount: Int,
    val totalChars: Long,
    val currentChapterIndex: Int = 0,
    val analysisCompleted: Int = 0,
    val coverPath: String? = null
)

@Entity(
    tableName = "chapters",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["bookId", "chapterIndex"], unique = true)]
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val title: String,
    val content: String,
    val charCount: Int,
    val analysisStatus: String = TaskStatus.PENDING.name,
    val ttsStatus: String = TaskStatus.PENDING.name,
    val ttsManifestPath: String? = null
)

@Entity(
    tableName = "characters",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class StoryCharacterEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val canonicalName: String,
    val aliasesJson: String = "[]",
    val gender: String = "UNKNOWN",
    val personality: String = "",
    val voiceId: String? = null,
    val firstChapterIndex: Int,
    val lastChapterIndex: Int,
    val confidence: Float = 0f,
    val importanceScore: Float = 0f,
    val importanceReason: String = ""
)

@Entity(
    tableName = "chapter_character_mentions",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StoryCharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("chapterId"),
        Index("characterId"),
        Index(value = ["chapterId", "characterId", "sourceHash", "analysisVersion"], unique = true)
    ]
)
data class ChapterCharacterMentionEntity(
    @PrimaryKey val id: String,
    val chapterId: String,
    val characterId: String,
    val evidence: String,
    val confidence: Float,
    val sourceHash: String,
    val analysisVersion: Int
)

@Entity(
    tableName = "relations",
    indices = [Index("bookId"), Index("fromCharacterId"), Index("toCharacterId")]
)
data class StoryRelationEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val fromCharacterId: String,
    val toCharacterId: String,
    val relationType: String,
    val strength: Float,
    val startChapterIndex: Int,
    val endChapterIndex: Int? = null,
    val evidence: String = "",
    val confidence: Float = 0f
)

@Entity(tableName = "plot_nodes", indices = [Index("bookId")])
data class PlotNodeEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val title: String,
    val summary: String,
    val startChapterIndex: Int,
    val endChapterIndex: Int? = null,
    val parentIdsJson: String = "[]",
    val childIdsJson: String = "[]",
    val participantIdsJson: String = "[]",
    val locationName: String? = null,
    val confidence: Float = 0f
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StoryCharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId"), Index("characterId"), Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val characterId: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val createdAt: Long
)

enum class TaskStatus { PENDING, QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }

enum class TaskRunType {
    ANALYSIS,
    ANALYSIS_VERIFY,
    JSON_REPAIR,
    TTS,
    TTS_PREVIEW,
    COVER_GENERATION,
    IMAGE_API_TEST
}

enum class TaskRunStatus { RUNNING, FAILED, COMPLETED, CANCELLED }

@Entity(tableName = "task_runs", indices = [Index("createdAt"), Index("taskType")])
data class TaskRunEntity(
    @PrimaryKey val id: String,
    val taskType: String,
    val targetId: String,
    val status: String = TaskRunStatus.RUNNING.name,
    val createdAt: Long,
    val finishedAt: Long? = null
)

@Entity(
    tableName = "task_events",
    foreignKeys = [ForeignKey(
        entity = TaskRunEntity::class,
        parentColumns = ["id"],
        childColumns = ["runId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("runId"), Index("createdAt"), Index("eventType")]
)
data class TaskEventEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val taskType: String,
    val targetId: String,
    val eventType: String,
    val stage: String,
    val retryable: Boolean,
    val statusCode: Int? = null,
    val attempt: Int = 1,
    val message: String,
    val createdAt: Long,
    val finishedAt: Long? = null,
    val durationMs: Long? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val usageQuality: String? = null,
    val requestId: String? = null,
    val responseModel: String? = null
)

/** Persisted batch state lets analysis resume after process death without replaying completed batches. */
@Entity(
    tableName = "analysis_batches",
    primaryKeys = ["runId", "batchIndex"],
    indices = [Index("bookId"), Index("status"), Index("updatedAt")]
)
data class AnalysisBatchEntity(
    val runId: String,
    val batchIndex: Int,
    val bookId: String,
    val chapterIdsJson: String,
    val status: String,
    val attempt: Int = 1,
    val error: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

/** Validated source-level dialogue annotation shared by the reader and TTS. */
@Entity(
    tableName = "dialogue_annotations",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StoryCharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["speakerCharacterId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("bookId"),
        Index("chapterId"),
        Index("speakerCharacterId"),
        Index(value = ["chapterId", "sourceStart", "sourceEnd"])
    ]
)
data class DialogueAnnotationEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterId: String,
    val chapterIndex: Int,
    val speakerCharacterId: String?,
    val speakerName: String?,
    val dialogueText: String,
    val sourceText: String,
    val speakerEvidence: String,
    val sourceStart: Int,
    val sourceEnd: Int,
    val confidence: Float,
    val validationStatus: String,
    val validationIssuesJson: String = "[]",
    val sourceHash: String,
    val analysisVersion: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
