package com.storybrain.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ReadingMode { CHAT, ORIGINAL }

enum class ReaderTheme { PAPER, SEPIA, NIGHT }

enum class ReadingMarkType {
    BOOKMARK,
    HIGHLIGHT,
    NOTE;

    companion object {
        fun fromStorage(value: String?): ReadingMarkType? = entries.firstOrNull { it.name == value }
    }
}

enum class TaskType {
    ANALYSIS,
    TTS,
    SEARCH_INDEX;

    companion object {
        fun fromStorage(value: String?): TaskType? = entries.firstOrNull { it.name == value }
    }
}

enum class SleepTimerMode { OFF, MINUTES, END_OF_CHAPTER }

@Entity(
    tableName = "reading_preferences",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ReadingPreferenceEntity(
    @PrimaryKey val bookId: String,
    val mode: String = ReadingMode.CHAT.name,
    val useGlobalStyle: Boolean = true,
    val theme: String = ReaderTheme.PAPER.name,
    val fontSizeSp: Float = 18f,
    val lineHeightMultiplier: Float = 1.6f,
    val paragraphSpacingDp: Float = 8f,
    val horizontalPaddingDp: Int = 20,
    val serifFont: Boolean = false,
    val autoFollowAudio: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "reading_positions",
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
        )
    ],
    indices = [Index("chapterId")]
)
data class ReadingPositionEntity(
    @PrimaryKey val bookId: String,
    val chapterId: String,
    val sourceOffset: Int = 0,
    val scrollOffsetPx: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "reading_marks",
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
        )
    ],
    indices = [Index("bookId"), Index("chapterId"), Index(value = ["bookId", "type"])]
)
data class ReadingMarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterId: String,
    val type: String,
    val startOffset: Int,
    val endOffset: Int,
    val excerpt: String,
    val note: String = "",
    val colorKey: String = "amber",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "task_records",
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
        )
    ],
    indices = [Index("bookId"), Index("chapterId"), Index("status"), Index("updatedAt")]
)
data class TaskRecordEntity(
    @PrimaryKey val workName: String,
    val type: String,
    val bookId: String,
    val chapterId: String? = null,
    val title: String,
    val status: String,
    val completed: Int = 0,
    val total: Int = 0,
    val stage: String = "",
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null
)

@Fts4
@Entity(tableName = "chapter_search_fts")
data class ChapterSearchFtsEntity(
    val chapterId: String,
    val bookId: String,
    val title: String,
    val content: String,
    val searchTerms: String
)

data class ChapterSearchHit(
    val chapterId: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val sourceOffset: Int,
    val excerpt: String
)
