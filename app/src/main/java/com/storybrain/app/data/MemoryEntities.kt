package com.storybrain.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index

enum class MemoryType { PLOT, RELATION, EXCERPT, NOTE, CHAT }

enum class MemoryEvidenceSource { ANALYSIS, USER, LEGACY_MEMORY_ITEM }

@Entity(
    tableName = "memory_items",
    primaryKeys = ["id"],
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("bookId"),
        Index("type"),
        Index("sourceKey", unique = true)
    ]
)
data class MemoryItemEntity(
    val id: String,
    val bookId: String,
    val type: String,
    val title: String,
    val content: String,
    val chapterStartIndex: Int? = null,
    val chapterEndIndex: Int? = null,
    val characterIdsJson: String = "[]",
    val sourceKey: String,
    val searchTerms: String,
    val editable: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "character_memory_evidence",
    primaryKeys = ["memoryId", "characterId"],
    foreignKeys = [
        ForeignKey(
            entity = MemoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StoryCharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("memoryId"), Index("characterId")]
)
data class CharacterMemoryEvidenceEntity(
    val memoryId: String,
    val characterId: String,
    val chapterStartIndex: Int?,
    val chapterEndIndex: Int?,
    val characterIdsJson: String,
    val source: String,
    val confidence: Float,
    val invalidatedAt: Long? = null,
    val spoilerBoundaryChapterIndex: Int?
)

@Fts4
@Entity(tableName = "memory_fts")
data class MemoryFtsEntity(
    val memoryId: String,
    val title: String,
    val content: String,
    val searchTerms: String
)

@Entity(
    tableName = "chat_sessions",
    primaryKeys = ["id"],
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
        )
    ],
    indices = [Index("bookId"), Index("characterId"), Index("updatedAt")]
)
data class ChatSessionEntity(
    val id: String,
    val bookId: String,
    val characterId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false
)

@Entity(
    tableName = "character_memory_defaults",
    primaryKeys = ["characterId", "memoryId"],
    foreignKeys = [
        ForeignKey(
            entity = StoryCharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("memoryId")]
)
data class CharacterMemoryDefaultEntity(
    val characterId: String,
    val memoryId: String,
    val createdAt: Long
)

@Entity(
    tableName = "session_memory_links",
    primaryKeys = ["sessionId", "memoryId"],
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("memoryId")]
)
data class SessionMemoryLinkEntity(
    val sessionId: String,
    val memoryId: String,
    val selectedAt: Long
)

data class MemoryWithSelection(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "bookId") val bookId: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "chapterStartIndex") val chapterStartIndex: Int?,
    @ColumnInfo(name = "chapterEndIndex") val chapterEndIndex: Int?,
    @ColumnInfo(name = "characterIdsJson") val characterIdsJson: String,
    @ColumnInfo(name = "sourceKey") val sourceKey: String,
    @ColumnInfo(name = "searchTerms") val searchTerms: String,
    @ColumnInfo(name = "editable") val editable: Boolean,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
    @ColumnInfo(name = "isDefault") val isDefault: Boolean,
    @ColumnInfo(name = "isSession") val isSession: Boolean,
    @ColumnInfo(name = "isLocked") val isLocked: Boolean
) {
    fun asEntity() = MemoryItemEntity(
        id, bookId, type, title, content, chapterStartIndex, chapterEndIndex,
        characterIdsJson, sourceKey, searchTerms, editable, createdAt, updatedAt
    )
}
