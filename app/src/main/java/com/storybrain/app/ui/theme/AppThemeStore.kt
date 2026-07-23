package com.storybrain.app.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppThemeMode { DARK, LIGHT, SYSTEM }

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance_v6")

class AppThemeStore(private val context: Context) {
    val mode: Flow<AppThemeMode> = context.appearanceDataStore.data.map { values ->
        runCatching { AppThemeMode.valueOf(values[MODE] ?: AppThemeMode.DARK.name) }
            .getOrDefault(AppThemeMode.DARK)
    }

    suspend fun save(mode: AppThemeMode) {
        context.appearanceDataStore.edit { it[MODE] = mode.name }
    }

    private companion object {
        val MODE = stringPreferencesKey("app_theme_mode")
    }
}
