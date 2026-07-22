package com.storybrain.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.storybrain.app.StoryBrainApplication
import com.storybrain.app.reader.GlobalReadingPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReaderDefaultsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = (application as StoryBrainApplication).readerSettingsStore
    val preferences = store.preferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        GlobalReadingPreferences()
    )

    fun save(value: GlobalReadingPreferences) {
        viewModelScope.launch { store.save(value) }
    }
}
