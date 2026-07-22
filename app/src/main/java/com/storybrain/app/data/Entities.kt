package com.storybrain.app.data

import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sourceName: String,
    val importedAt: Long,
    val chapterCount: Int,
    val totalChars: Long,
    val currentChapterIndex: Int = 0,
    val analysisCompleted: Int = 0
)

/** Single-query bookshelf model. Counts are calculated by Room instead of per-card Flows. */
data class BookLibraryItem(
    @Embedded val book: BookEntity,
    val ttsCompleted: Int,
    val activeAnalysisTasks: Int,
    val activeTtsTasks: Int
)

data class ChapterTaskRef(
    val id: String,
    val bookId: String
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

/** Lightweight chapter navigation row that deliberately excludes full chapter content. */
data class ChapterListItem(
    val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val title: String,
    val charCount: Int,
    val analysisStatus: String,
    val ttsStatus: String,
    val ttsManifestPath: String?
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

enum class TaskStatus {
    PENDING,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    companion object {
        /** Unknown values from a newer or damaged database remain safely retryable. */
        fun fromStorage(value: String?): TaskStatus =
            entries.firstOrNull { it.name == value } ?: FAILED
    }
}
