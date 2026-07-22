package com.storybrain.app.reader

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.storybrain.app.data.ReaderTheme
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.readerDataStore by preferencesDataStore(name = "reader_settings_v5")

data class GlobalReadingPreferences(
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val fontSizeSp: Float = 18f,
    val lineHeightMultiplier: Float = 1.6f,
    val paragraphSpacingDp: Float = 8f,
    val horizontalPaddingDp: Int = 20,
    val serifFont: Boolean = false,
    val autoFollowAudio: Boolean = true
)

class ReaderSettingsStore(private val context: Context) {
    val preferences: Flow<GlobalReadingPreferences> = context.readerDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { values ->
            GlobalReadingPreferences(
                theme = runCatching {
                    ReaderTheme.valueOf(values[Keys.THEME] ?: ReaderTheme.PAPER.name)
                }.getOrDefault(ReaderTheme.PAPER),
                fontSizeSp = (values[Keys.FONT_SIZE] ?: 18f).coerceIn(14f, 30f),
                lineHeightMultiplier = (values[Keys.LINE_HEIGHT] ?: 1.6f).coerceIn(1.2f, 2.2f),
                paragraphSpacingDp = (values[Keys.PARAGRAPH_SPACING] ?: 8f).coerceIn(0f, 24f),
                horizontalPaddingDp = (values[Keys.HORIZONTAL_PADDING] ?: 20).coerceIn(12, 40),
                serifFont = values[Keys.SERIF_FONT] ?: false,
                autoFollowAudio = values[Keys.AUTO_FOLLOW_AUDIO] ?: true
            )
        }

    suspend fun save(value: GlobalReadingPreferences) {
        context.readerDataStore.edit { values ->
            values[Keys.THEME] = value.theme.name
            values[Keys.FONT_SIZE] = value.fontSizeSp.coerceIn(14f, 30f)
            values[Keys.LINE_HEIGHT] = value.lineHeightMultiplier.coerceIn(1.2f, 2.2f)
            values[Keys.PARAGRAPH_SPACING] = value.paragraphSpacingDp.coerceIn(0f, 24f)
            values[Keys.HORIZONTAL_PADDING] = value.horizontalPaddingDp.coerceIn(12, 40)
            values[Keys.SERIF_FONT] = value.serifFont
            values[Keys.AUTO_FOLLOW_AUDIO] = value.autoFollowAudio
        }
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val FONT_SIZE = floatPreferencesKey("font_size_sp")
        val LINE_HEIGHT = floatPreferencesKey("line_height_multiplier")
        val PARAGRAPH_SPACING = floatPreferencesKey("paragraph_spacing_dp")
        val HORIZONTAL_PADDING = intPreferencesKey("horizontal_padding_dp")
        val SERIF_FONT = booleanPreferencesKey("serif_font")
        val AUTO_FOLLOW_AUDIO = booleanPreferencesKey("auto_follow_audio")
    }
}
