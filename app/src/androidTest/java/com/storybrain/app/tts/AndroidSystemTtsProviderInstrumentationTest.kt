package com.storybrain.app.tts

import android.speech.tts.TextToSpeech
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSystemTtsProviderInstrumentationTest {
    @Test
    fun installedEngineBoundaryExposesChineseVoiceOrSkips() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ready = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        val tts = TextToSpeech(context) { status = it; ready.countDown() }
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            assumeTrue(status == TextToSpeech.SUCCESS)
            assumeTrue(tts.voices.orEmpty().any { AndroidSystemTtsVoiceSupport.isChinese(it.locale) })
        } finally {
            tts.shutdown()
        }
    }
}
