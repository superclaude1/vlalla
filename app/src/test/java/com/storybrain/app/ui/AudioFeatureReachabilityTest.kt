package com.storybrain.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFeatureReachabilityTest {
    private val ui = File("src/main/java/com/storybrain/app/ui")
    private val settings = ui.resolve("SettingsScreen.kt").readText()
    private val binding = ui.resolve("CharacterVoiceBindingScreen.kt").readText()
    private val chapters = ui.resolve("InformationArchitectureScreens.kt").readText()

    @Test fun enginesProvidersAndVoiceSourcesRemainReachable() {
        assertTrue(settings.contains("设为全局服务"))
        assertTrue(settings.contains("跟随全局"))
        assertTrue(settings.contains("Fish Audio 音色"))
        assertTrue(settings.contains("我的音色"))
        assertTrue(settings.contains("公开搜索"))
        assertTrue(settings.contains("Voice ID"))
        assertTrue(settings.contains("TtsVoiceRole.entries"))
    }

    @Test fun narratorAndCharactersExposeCurrentBindingSelectAndClear() {
        assertTrue(binding.contains("viewModel.activeNarratorBinding(bookId)"))
        assertTrue(binding.contains("viewModel.assignNarratorVoice"))
        assertTrue(binding.contains("viewModel.clearNarratorVoice"))
        assertTrue(binding.contains("viewModel.assignCharacterVoice"))
        assertTrue(binding.contains("viewModel.clearCharacterVoice"))
        assertTrue(binding.contains("VoiceBindingMenuPolicy.enabled"))
    }

    @Test fun chapterAudioSupportsGenerateRetryPlaybackStopProgressAndErrors() {
        assertTrue(chapters.contains("viewModel.generateChapterTts"))
        assertTrue(chapters.contains("viewModel.playChapterTts"))
        assertTrue(chapters.contains("viewModel.stopChapterTts"))
        assertTrue(chapters.contains("LinearProgressIndicator"))
        assertTrue(chapters.contains("生成失败"))
        assertTrue(chapters.contains("重试"))
    }
}
