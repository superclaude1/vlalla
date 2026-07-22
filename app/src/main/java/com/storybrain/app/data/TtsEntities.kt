package com.storybrain.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

object TtsProfileIds {
    const val EDGE = "edge-default"
    const val FISH = "fish-default"
    const val OPENAI = "openai-compatible-default"
}

enum class TtsProviderKind { EDGE, FISH_AUDIO, OPENAI_COMPATIBLE }
enum class TtsVoiceRole { NARRATOR, MALE, FEMALE, UNKNOWN }

@Entity(tableName = "tts_provider_profiles")
data class TtsProviderProfileEntity(
    @androidx.room.PrimaryKey val id: String,
    val kind: String,
    val displayName: String,
    val baseUrl: String,
    val model: String,
    val supportsInstructions: Boolean = false,
    val enabled: Boolean = true,
    val updatedAt: Long = 0L
)

@Entity(
    tableName = "tts_profile_voice_pool",
    primaryKeys = ["profileId", "voiceId", "role"],
    foreignKeys = [ForeignKey(
        entity = TtsProviderProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("profileId")]
)
data class TtsProfileVoicePoolEntity(
    val profileId: String,
    val voiceId: String,
    val role: String,
    val voiceName: String,
    val gender: String = "UNKNOWN",
    val ageGroup: String = "UNKNOWN",
    val language: String = "zh",
    val tagsJson: String = "[]",
    val source: String = "BUILT_IN",
    val favorite: Boolean = false,
    val updatedAt: Long = 0L
)

@Entity(
    tableName = "book_tts_settings",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class BookTtsSettingEntity(
    @androidx.room.PrimaryKey val bookId: String,
    /** Null means follow the global profile stored in DataStore. */
    val primaryProfileId: String? = null,
    val updatedAt: Long = 0L
)

@Entity(
    tableName = "character_voice_bindings",
    primaryKeys = ["characterId", "profileId"],
    foreignKeys = [ForeignKey(
        entity = StoryCharacterEntity::class,
        parentColumns = ["id"],
        childColumns = ["characterId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("characterId"), Index("profileId")]
)
data class CharacterVoiceBindingEntity(
    val characterId: String,
    val profileId: String,
    val voiceId: String,
    val voiceName: String,
    val active: Boolean = true,
    val userConfirmed: Boolean = true,
    val updatedAt: Long = 0L
)

@Entity(
    tableName = "book_narrator_bindings",
    primaryKeys = ["bookId", "profileId"],
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId"), Index("profileId")]
)
data class BookNarratorBindingEntity(
    val bookId: String,
    val profileId: String,
    val voiceId: String,
    val voiceName: String,
    val active: Boolean = true,
    val updatedAt: Long = 0L
)

@Entity(
    tableName = "tts_scripts",
    foreignKeys = [ForeignKey(
        entity = ChapterEntity::class,
        parentColumns = ["id"],
        childColumns = ["chapterId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId"), Index(value = ["chapterId"], unique = true)]
)
data class TtsScriptEntity(
    @androidx.room.PrimaryKey val id: String,
    val bookId: String,
    val chapterId: String,
    val sourceHash: String,
    val llmModel: String,
    val promptVersion: Int,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "tts_script_segments",
    foreignKeys = [ForeignKey(
        entity = TtsScriptEntity::class,
        parentColumns = ["id"],
        childColumns = ["scriptId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("scriptId"), Index(value = ["scriptId", "segmentIndex"], unique = true)]
)
data class TtsScriptSegmentEntity(
    @androidx.room.PrimaryKey val id: String,
    val scriptId: String,
    val segmentIndex: Int,
    val blockIndex: Int,
    val speaker: String,
    val rawText: String,
    val directivesJson: String,
    val profileId: String = "",
    val model: String = "",
    val voiceId: String = "",
    val renderedText: String = "",
    val cacheKey: String = "",
    val status: String = TaskStatus.PENDING.name,
    val audioPath: String? = null,
    val error: String? = null,
    val updatedAt: Long = 0L
)
