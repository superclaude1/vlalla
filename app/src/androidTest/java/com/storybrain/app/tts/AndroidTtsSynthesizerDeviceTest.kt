package com.storybrain.app.tts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.time.measureTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidTtsSynthesizerDeviceTest {
    @Test
    fun syntheticChineseBlockProducesPlayableLocalFileWithinFiveSeconds() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val synthesizer = AndroidTtsSynthesizer(context)
        val output = File(context.cacheDir, "system-tts-device-test.wav").apply { delete() }
        try {
            withTimeout(10_000L) { synthesizer.warmUp() }
            val elapsed = measureTime {
                withTimeout(5_000L) {
                    synthesizer.synthesize(
                        "这是章境的本地系统朗读测试。雨停以后，窗外的灯光映在安静的街道上。",
                        output
                    )
                }
            }
            assertTrue(output.isFile)
            assertTrue(output.length() > 128L)
            assertTrue(elapsed.inWholeMilliseconds < 5_000L)
        } finally {
            synthesizer.shutdown()
            output.delete()
        }
    }
}
