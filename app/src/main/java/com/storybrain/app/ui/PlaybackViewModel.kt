package com.storybrain.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.storybrain.app.StoryBrainApplication
import com.storybrain.app.data.SleepTimerMode
import kotlinx.coroutines.launch

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as StoryBrainApplication).playbackRepository
    val uiState = repository.uiState

    fun playChapter(bookId: String, chapterId: String, blockIndex: Int? = null) {
        viewModelScope.launch { repository.playChapter(bookId, chapterId, blockIndex) }
    }

    fun play() = repository.play()
    fun pause() = repository.pause()
    fun stop() = repository.stop()
    fun previousChapter() = repository.previousChapter()
    fun nextChapter() = repository.nextChapter()
    fun seekToChapterPosition(positionMs: Long) = repository.seekToChapterPosition(positionMs)
    fun seekToBlock(blockIndex: Int) = repository.seekToBlock(blockIndex)
    fun setSpeed(speed: Float) = repository.setSpeed(speed)
    fun setSleepTimer(mode: SleepTimerMode, minutes: Int = 0) = repository.setSleepTimer(mode, minutes)
    fun clearError() = repository.clearError()
}
