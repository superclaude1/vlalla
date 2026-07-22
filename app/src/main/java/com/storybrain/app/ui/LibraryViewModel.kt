package com.storybrain.app.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.storybrain.app.StoryBrainApplication
import com.storybrain.app.importer.ImportedNovel
import com.storybrain.app.importer.NovelStreamImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportUiState(
    val loading: Boolean = false,
    val sourceName: String = "",
    val title: String = "",
    val novel: ImportedNovel? = null,
    val error: String? = null
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as StoryBrainApplication).repository
    private val _importState = MutableStateFlow(ImportUiState())
    val importState = _importState.asStateFlow()
    val libraryItems = repository.observeLibraryItems()

    fun loadNovel(uri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportUiState(loading = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val sourceName = resolver.query(uri, null, null, null, null)?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                    } ?: "导入小说.txt"
                    val title = sourceName.substringBeforeLast('.').ifBlank { "未命名小说" }
                    val novel = resolver.openInputStream(uri)?.use { input -> NovelStreamImporter.parse(input, title) }
                        ?: error("无法读取该文件")
                    require(novel.chapters.isNotEmpty()) { "文件中没有可读取的正文" }
                    ImportUiState(sourceName = sourceName, title = title, novel = novel)
                }
            }.onSuccess { _importState.value = it }
                .onFailure { _importState.value = ImportUiState(error = it.message ?: "导入失败") }
        }
    }

    fun updateImportTitle(title: String) {
        _importState.value = _importState.value.copy(title = title)
    }

    fun confirmImport(onComplete: (String) -> Unit) {
        val state = _importState.value
        val novel = state.novel ?: return
        viewModelScope.launch {
            _importState.value = state.copy(loading = true)
            runCatching { repository.saveImportedNovel(novel, state.sourceName, state.title) }
                .onSuccess { id ->
                    _importState.value = ImportUiState()
                    onComplete(id)
                }
                .onFailure { _importState.value = state.copy(loading = false, error = it.message) }
        }
    }

    fun cancelImport() {
        _importState.value = ImportUiState()
    }
}
