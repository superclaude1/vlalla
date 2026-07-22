package com.storybrain.app.playback

import android.accessibilityservice.AccessibilityService
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.storybrain.app.data.AppDatabase
import com.storybrain.app.data.BookEntity
import com.storybrain.app.data.ChapterEntity
import com.storybrain.app.data.SleepTimerMode
import com.storybrain.app.data.StoryRepository
import com.storybrain.app.data.TaskStatus
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackRepositoryDeviceTest {
    private lateinit var database: AppDatabase
    private lateinit var playback: PlaybackRepository
    private lateinit var directory: File

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        directory = File(context.cacheDir, "playback-device-test").apply { mkdirs() }
        val audio = File(directory, "segment.wav")
        writeSilentWave(audio, durationSeconds = 12)
        val manifest = File(directory, "manifest.json").apply {
            writeText(
                JSONObject().put(
                    "segments",
                    JSONArray().put(
                        JSONObject()
                            .put("index", 0)
                            .put("blockIndex", 3)
                            .put("speaker", "旁白")
                            .put("path", audio.absolutePath)
                    )
                ).toString(),
                Charsets.UTF_8
            )
        }
        database.storyDao().insertBook(BookEntity("book", "播放测试", "test.txt", 1, 2, 20))
        database.storyDao().insertChapters(
            listOf(
                ChapterEntity(
                    "chapter-1",
                    "book",
                    0,
                    "第一章",
                    "本地音频测试",
                    7,
                    ttsStatus = TaskStatus.COMPLETED.name,
                    ttsManifestPath = manifest.absolutePath
                ),
                ChapterEntity("chapter-2", "book", 1, "第二章", "尚无音频", 4)
            )
        )
        playback = PlaybackRepository(context, StoryRepository(database), PlaybackStateStore(context))
        withTimeout(10_000) { playback.uiState.first { it.connected } }
        Unit
    }

    @After
    fun tearDown() {
        playback.stop()
        playback.release()
        database.close()
        directory.deleteRecursively()
    }

    @Test
    fun localQueueSupportsBackgroundSpeedTimerAndMissingNextBoundary() = runBlocking {
        assertTrue(playback.playChapter("book", "chapter-1", startBlockIndex = 3).isSuccess)
        val playing = withTimeout(10_000) { playback.uiState.first { it.isPlaying } }
        assertEquals("chapter-1", playing.chapterId)
        assertEquals(3, playing.blockIndex)

        playback.setSpeed(1.5f)
        assertEquals(1.5f, withTimeout(5_000) { playback.uiState.first { it.speed == 1.5f } }.speed)
        playback.setSleepTimer(SleepTimerMode.MINUTES, 15)
        assertEquals(
            SleepTimerMode.MINUTES,
            withTimeout(5_000) { playback.uiState.first { it.sleepTimerMode == SleepTimerMode.MINUTES } }.sleepTimerMode
        )

        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
            AccessibilityService.GLOBAL_ACTION_HOME
        )
        delay(1_000)
        assertTrue(playback.uiState.value.isPlaying)

        playback.nextChapter()
        val boundary = withTimeout(5_000) { playback.uiState.first { it.missingNextChapterAudio } }
        assertTrue(boundary.error?.contains("下一章") == true)
        assertEquals("chapter-1", boundary.chapterId)
    }

    private fun writeSilentWave(file: File, durationSeconds: Int) {
        val sampleRate = 8_000
        val dataSize = sampleRate * durationSeconds * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()
        FileOutputStream(file).use { output ->
            output.write(header)
            output.write(ByteArray(dataSize))
        }
    }
}
