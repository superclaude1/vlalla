package com.storybrain.app.playback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.storybrain.app.data.SleepTimerMode
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playbackDataStore by preferencesDataStore(name = "playback_state_v5")

data class SavedPlaybackState(
    val bookId: String = "",
    val chapterId: String = "",
    val segmentIndex: Int = 0,
    val positionMs: Long = 0,
    val speed: Float = 1f,
    val sleepTimerMode: SleepTimerMode = SleepTimerMode.OFF,
    val sleepTimerEndAt: Long = 0
)

class PlaybackStateStore(private val context: Context) {
    suspend fun load(): SavedPlaybackState = context.playbackDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { values ->
            SavedPlaybackState(
                bookId = values[Keys.BOOK_ID].orEmpty(),
                chapterId = values[Keys.CHAPTER_ID].orEmpty(),
                segmentIndex = values[Keys.SEGMENT_INDEX] ?: 0,
                positionMs = values[Keys.POSITION_MS] ?: 0,
                speed = (values[Keys.SPEED] ?: 1f).coerceIn(.75f, 2f),
                sleepTimerMode = runCatching {
                    SleepTimerMode.valueOf(values[Keys.SLEEP_MODE] ?: SleepTimerMode.OFF.name)
                }.getOrDefault(SleepTimerMode.OFF),
                sleepTimerEndAt = values[Keys.SLEEP_END_AT] ?: 0
            )
        }.first()

    suspend fun save(value: SavedPlaybackState) {
        context.playbackDataStore.edit { values ->
            values[Keys.BOOK_ID] = value.bookId
            values[Keys.CHAPTER_ID] = value.chapterId
            values[Keys.SEGMENT_INDEX] = value.segmentIndex.coerceAtLeast(0)
            values[Keys.POSITION_MS] = value.positionMs.coerceAtLeast(0)
            values[Keys.SPEED] = value.speed.coerceIn(.75f, 2f)
            values[Keys.SLEEP_MODE] = value.sleepTimerMode.name
            values[Keys.SLEEP_END_AT] = value.sleepTimerEndAt
        }
    }

    private object Keys {
        val BOOK_ID = stringPreferencesKey("book_id")
        val CHAPTER_ID = stringPreferencesKey("chapter_id")
        val SEGMENT_INDEX = intPreferencesKey("segment_index")
        val POSITION_MS = longPreferencesKey("position_ms")
        val SPEED = floatPreferencesKey("speed")
        val SLEEP_MODE = stringPreferencesKey("sleep_mode")
        val SLEEP_END_AT = longPreferencesKey("sleep_end_at")
    }
}
