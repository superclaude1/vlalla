package com.storybrain.app.tts

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSystemTtsProviderTest {
    @Test
    fun providerKindIncludesAndroidSystemAndDefaultVoiceIsChinese() {
        assertTrue(com.storybrain.app.data.TtsProviderKind.ANDROID_SYSTEM.name == "ANDROID_SYSTEM")
        assertTrue(AndroidSystemTtsVoiceSupport.selectChineseVoice(
            listOf(
                AndroidTtsVoiceDescriptor("en-US", "en-US"),
                AndroidTtsVoiceDescriptor("zh-CN", "zh-CN")
            ),
            requestedId = ""
        )?.localeTag?.startsWith("zh") == true)
    }

    @Test
    fun requestedChineseVoiceWinsOverOtherChineseVoice() {
        val selected = AndroidSystemTtsVoiceSupport.selectChineseVoice(
            listOf(
                AndroidTtsVoiceDescriptor("zh-CN", "zh-CN"),
                AndroidTtsVoiceDescriptor("zh-TW", "zh-TW")
            ),
            requestedId = "zh-TW"
        )

        assertEquals("zh-TW", selected?.id)
    }

    @Test
    fun artifactExtensionComesFromArtifactFormat() {
        assertEquals("wav", TtsAudioArtifact(File("segment.any"), "audio/wav", "wav").fileExtension())
        assertEquals("mp3", TtsAudioArtifact(File("segment.any"), "audio/mpeg", "mp3").fileExtension())
    }

    @Test
    fun manifestParserPreservesArtifactFormatAndMime() {
        val manifest = File.createTempFile("tts-manifest", ".json").apply {
            writeText("""{"segments":[{"path":"segment.wav","format":"wav","mimeType":"audio/wav"}]}""")
        }
        try {
            val segment = parseChapterAudioSegments(manifest).single()
            assertEquals("wav", segment.format)
            assertEquals("audio/wav", segment.mimeType)
        } finally {
            manifest.delete()
        }
    }

    @Test
    fun chineseVoiceSupportAcceptsChineseLocalesOnly() {
        assertTrue(AndroidSystemTtsVoiceSupport.isChinese(Locale.SIMPLIFIED_CHINESE))
        assertTrue(AndroidSystemTtsVoiceSupport.isChinese(Locale("zh", "TW")))
        assertTrue(!AndroidSystemTtsVoiceSupport.isChinese(Locale.US))
    }

    @Test
    fun engineDefinedAudioDoesNotInferMp3FromDestination() {
        val artifact = TtsAudioArtifact(File("preview.audio"))

        assertEquals("preview.audio", artifact.file.name)
        assertNull(artifact.mimeType)
        assertNull(artifact.format)
    }
}
