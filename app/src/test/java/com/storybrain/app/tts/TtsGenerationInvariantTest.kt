package com.storybrain.app.tts

import com.storybrain.app.reader.ReadingBlock
import com.storybrain.app.settings.LlmProfileSnapshot
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TtsGenerationInvariantTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun directingAcceptsTheSnapshotCapturedByItsCaller() = runBlocking {
        val snapshot = LlmProfileSnapshot("profile-a", "https://a.example/v1", "", "model-a")
        val blocks = listOf(ReadingBlock.Narration("夜色很静。"))

        val result = TtsDirectingService(settings = null).direct(blocks, snapshot)

        assertTrue(result.single().usedFallback)
    }

    @Test
    fun cacheIdentityIncludesProfileEndpointAndModel() {
        val original = ttsCacheIdentity("profile-a", "https://a.example/v1", "model-a")

        assertNotEquals(original, ttsCacheIdentity("profile-b", "https://a.example/v1", "model-a"))
        assertNotEquals(original, ttsCacheIdentity("profile-a", "https://b.example/v1", "model-a"))
        assertNotEquals(original, ttsCacheIdentity("profile-a", "https://a.example/v1", "model-b"))
    }

    @Test
    fun knownProviderAudioTypesNeverResolveToBin() {
        val file = File(temporaryFolder.root, "artifact.audio")

        mapOf(
            "audio/flac" to "flac",
            "audio/webm; codecs=opus" to "webm",
            "audio/aac" to "m4a",
            "audio/L16" to "pcm"
        ).forEach { (contentType, extension) ->
            assertTrue(artifactForAudio(file, contentType).fileExtension() == extension)
        }
    }

    @Test
    fun cancellingCannotInterruptSynchronousProviderButDiscardsItsResultAfterReturn() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val provider = object : TtsProvider {
            override val id = "blocking"
            override fun synthesize(request: TtsSynthesisRequest, output: File): TtsAudioArtifact {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                output.writeBytes(byteArrayOf(1))
                return TtsAudioArtifact(output, "audio/mpeg", "mp3")
            }
        }
        val request = TtsSynthesisRequest("正文", "voice", "model")
        val output = File(temporaryFolder.root, "blocking.audio")

        val task = async(Dispatchers.IO) { synthesizeCancellably(provider, request, output) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        task.cancel()
        assertFalse("同步 provider 返回前 Job 无法完成取消", task.isCompleted)
        release.countDown()
        task.cancelAndJoin()

        assertTrue(task.isCancelled)
        assertFalse("取消的同步结果不能进入缓存", output.exists())
    }
}
