package com.storybrain.app.reader

import com.storybrain.app.data.ChapterEntity
import com.storybrain.app.data.ReaderTheme
import com.storybrain.app.data.ReadingMarkEntity
import com.storybrain.app.data.ReadingMarkType
import com.storybrain.app.data.ReadingMode
import com.storybrain.app.data.ReadingPositionEntity
import com.storybrain.app.data.ReadingPreferenceEntity
import com.storybrain.app.data.StoryRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class ResolvedReadingPreferences(
    val mode: ReadingMode,
    val theme: ReaderTheme,
    val fontSizeSp: Float,
    val lineHeightMultiplier: Float,
    val paragraphSpacingDp: Float,
    val horizontalPaddingDp: Int,
    val serifFont: Boolean,
    val autoFollowAudio: Boolean,
    val usesGlobalStyle: Boolean
)

class ReaderRepository(
    private val storyRepository: StoryRepository,
    private val settingsStore: ReaderSettingsStore
) {
    fun observePreferences(bookId: String): Flow<ResolvedReadingPreferences> = combine(
        storyRepository.observeReadingPreference(bookId),
        settingsStore.preferences
    ) { bookPreference, defaults ->
        resolve(bookPreference, defaults)
    }

    fun observePosition(bookId: String) = storyRepository.observeReadingPosition(bookId)
    fun observeMarks(bookId: String) = storyRepository.observeReadingMarks(bookId)
    fun observeChapterMarks(chapterId: String) = storyRepository.observeChapterReadingMarks(chapterId)

    suspend fun document(chapterId: String, knownSpeakers: Map<String, String>): ReaderDocument? =
        storyRepository.getChapter(chapterId)?.let { ReaderDocument.create(it.content, knownSpeakers) }

    suspend fun adjacentChapters(bookId: String, chapterIndex: Int): List<ChapterEntity> =
        listOfNotNull(
            storyRepository.getChapterByIndex(bookId, chapterIndex - 1),
            storyRepository.getChapterByIndex(bookId, chapterIndex),
            storyRepository.getChapterByIndex(bookId, chapterIndex + 1)
        )

    suspend fun setMode(bookId: String, mode: ReadingMode) {
        val existing = storyRepository.getReadingPreference(bookId) ?: ReadingPreferenceEntity(bookId)
        storyRepository.saveReadingPreference(existing.copy(mode = mode.name))
    }

    suspend fun saveBookStyle(bookId: String, value: ResolvedReadingPreferences) {
        storyRepository.saveReadingPreference(
            ReadingPreferenceEntity(
                bookId = bookId,
                mode = value.mode.name,
                useGlobalStyle = false,
                theme = value.theme.name,
                fontSizeSp = value.fontSizeSp.coerceIn(14f, 30f),
                lineHeightMultiplier = value.lineHeightMultiplier.coerceIn(1.2f, 2.2f),
                paragraphSpacingDp = value.paragraphSpacingDp.coerceIn(0f, 24f),
                horizontalPaddingDp = value.horizontalPaddingDp.coerceIn(12, 40),
                serifFont = value.serifFont,
                autoFollowAudio = value.autoFollowAudio
            )
        )
    }

    suspend fun resetBookStyle(bookId: String) = storyRepository.resetReadingStyle(bookId)

    suspend fun savePosition(
        bookId: String,
        chapterId: String,
        sourceOffset: Int,
        scrollOffsetPx: Int = 0
    ) = storyRepository.saveReadingPosition(
        ReadingPositionEntity(bookId, chapterId, sourceOffset, scrollOffsetPx)
    )

    suspend fun addMark(
        bookId: String,
        chapterId: String,
        type: ReadingMarkType,
        startOffset: Int,
        endOffset: Int,
        excerpt: String,
        note: String = "",
        colorKey: String = "amber"
    ): ReadingMarkEntity {
        val mark = ReadingMarkEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            chapterId = chapterId,
            type = type.name,
            startOffset = startOffset,
            endOffset = endOffset,
            excerpt = excerpt,
            note = note,
            colorKey = colorKey
        )
        storyRepository.saveReadingMark(mark)
        return mark
    }

    suspend fun updateMark(mark: ReadingMarkEntity) = storyRepository.saveReadingMark(mark)
    suspend fun deleteMark(markId: String) = storyRepository.deleteReadingMark(markId)
    suspend fun search(bookId: String, query: String) = storyRepository.searchBook(bookId, query)

    private fun resolve(
        preference: ReadingPreferenceEntity?,
        defaults: GlobalReadingPreferences
    ): ResolvedReadingPreferences {
        val mode = runCatching {
            ReadingMode.valueOf(preference?.mode ?: ReadingMode.CHAT.name)
        }.getOrDefault(ReadingMode.CHAT)
        if (preference == null || preference.useGlobalStyle) {
            return ResolvedReadingPreferences(
                mode = mode,
                theme = defaults.theme,
                fontSizeSp = defaults.fontSizeSp,
                lineHeightMultiplier = defaults.lineHeightMultiplier,
                paragraphSpacingDp = defaults.paragraphSpacingDp,
                horizontalPaddingDp = defaults.horizontalPaddingDp,
                serifFont = defaults.serifFont,
                autoFollowAudio = defaults.autoFollowAudio,
                usesGlobalStyle = true
            )
        }
        return ResolvedReadingPreferences(
            mode = mode,
            theme = runCatching { ReaderTheme.valueOf(preference.theme) }.getOrDefault(ReaderTheme.PAPER),
            fontSizeSp = preference.fontSizeSp.coerceIn(14f, 30f),
            lineHeightMultiplier = preference.lineHeightMultiplier.coerceIn(1.2f, 2.2f),
            paragraphSpacingDp = preference.paragraphSpacingDp.coerceIn(0f, 24f),
            horizontalPaddingDp = preference.horizontalPaddingDp.coerceIn(12, 40),
            serifFont = preference.serifFont,
            autoFollowAudio = preference.autoFollowAudio,
            usesGlobalStyle = false
        )
    }
}
