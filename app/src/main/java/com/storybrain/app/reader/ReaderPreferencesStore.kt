package com.storybrain.app.reader

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ReaderPreferencesStore(context: Context) {
    private val storage = context.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)
    private val _preferences = MutableStateFlow(read())
    val preferences: StateFlow<ReaderPreferences> = _preferences

    fun update(transform: (ReaderPreferences) -> ReaderPreferences) {
        val next = transform(_preferences.value).normalized()
        storage.edit()
            .putInt(ReaderPreferences.KEY_FONT_SIZE, next.fontSizeSp)
            .putInt(ReaderPreferences.KEY_LINE_HEIGHT, next.lineHeightSp)
            .putInt(ReaderPreferences.KEY_HORIZONTAL_PADDING, next.horizontalPaddingDp)
            .putInt(ReaderPreferences.KEY_PARAGRAPH_SPACING, next.paragraphSpacingDp)
            .putString(ReaderPreferences.KEY_DISPLAY_MODE, next.displayMode.name)
            .putString(ReaderPreferences.KEY_THEME, next.theme.name)
            .putBoolean(ReaderPreferences.KEY_SERIF_FONT, next.serifFont)
            .apply()
        _preferences.value = next
    }

    private fun read(): ReaderPreferences = ReaderPreferences.fromMap(
        mapOf(
            ReaderPreferences.KEY_FONT_SIZE to storage
                .getInt(ReaderPreferences.KEY_FONT_SIZE, ReaderPreferences.DEFAULT_FONT_SIZE_SP)
                .toString(),
            ReaderPreferences.KEY_LINE_HEIGHT to storage
                .getInt(ReaderPreferences.KEY_LINE_HEIGHT, ReaderPreferences.DEFAULT_LINE_HEIGHT_SP)
                .toString(),
            ReaderPreferences.KEY_HORIZONTAL_PADDING to storage
                .getInt(
                    ReaderPreferences.KEY_HORIZONTAL_PADDING,
                    ReaderPreferences.DEFAULT_HORIZONTAL_PADDING_DP
                ).toString(),
            ReaderPreferences.KEY_PARAGRAPH_SPACING to storage
                .getInt(
                    ReaderPreferences.KEY_PARAGRAPH_SPACING,
                    ReaderPreferences.DEFAULT_PARAGRAPH_SPACING_DP
                ).toString(),
            ReaderPreferences.KEY_DISPLAY_MODE to storage.getString(
                ReaderPreferences.KEY_DISPLAY_MODE,
                ReaderDisplayMode.PLAIN_TEXT.name
            ),
            ReaderPreferences.KEY_THEME to storage.getString(
                ReaderPreferences.KEY_THEME,
                com.storybrain.app.data.ReaderTheme.PAPER.name
            ),
            ReaderPreferences.KEY_SERIF_FONT to storage
                .getBoolean(ReaderPreferences.KEY_SERIF_FONT, false)
                .toString()
        )
    )

    private companion object {
        const val STORAGE_NAME = "reader_preferences_v1"
    }
}
