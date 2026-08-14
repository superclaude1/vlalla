package com.storybrain.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 阅读主题（全局默认，单书可覆盖）。 */
enum class ReaderTheme { PAPER, SEPIA, NIGHT }

/** 阅读标记类型：书签 / 高亮 / 批注。 */
enum class ReadingMarkType {
    BOOKMARK,
    HIGHLIGHT,
    NOTE;

    companion object {
        fun fromStorage(value: String?): ReadingMarkType? = entries.firstOrNull { it.name == value }
    }
}

/** 单书阅读偏好（覆盖全局设置；useGlobalStyle=true 时不生效）。 */
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
    val mode: String = "PLAIN_TEXT",
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

/**
 * 阅读位置：以正文 sourceOffset（字符偏移）为主锚，
 * 字体/主题变化重新分页后仍能精确恢复到原段落。
 */
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

/** 阅读标记：书签/高亮/批注，按字符偏移区间定位。 */
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
