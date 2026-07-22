package com.storybrain.app.tts

import com.storybrain.app.reader.ReadingBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsDirectivesTest {
    @Test
    fun fishRendererKeepsOnlySupportedControlTags() {
        val rendered = TtsDirectiveRenderer.fishText(
            "别怕，我在这里。",
            TtsDirectives(
                emotion = "SAD",
                delivery = "whispering",
                pauseBeforeMs = 700,
                pauseAfterMs = 180
            )
        )

        assertEquals("[sad][whispering][long-break]别怕，我在这里。[break]", rendered)
        assertEquals(
            "正文",
            TtsDirectiveRenderer.fishText(
                "正文",
                TtsDirectives(emotion = "unsupported", delivery = "singing")
            )
        )
    }

    @Test
    fun instructionRendererIncludesOnlyMeaningfulDirectives() {
        val instructions = TtsDirectiveRenderer.instructions(
            TtsDirectives(
                emotion = "calm",
                delivery = "soft tone",
                pauseAfterMs = 320,
                rate = 0.85f
            )
        )

        assertTrue(instructions.contains("情绪：calm"))
        assertTrue(instructions.contains("语气：soft tone"))
        assertTrue(instructions.contains("结尾停顿320毫秒"))
        assertTrue(instructions.contains("语速0.85倍"))
        assertFalse(instructions.contains("开头停顿"))
    }

    @Test
    fun localDirectorUsesContextAndPunctuation() {
        val blocks = listOf(
            ReadingBlock.Narration("夜色阴森。"),
            ReadingBlock.Dialogue("林清", "她低声问道：你听见了吗？"),
            ReadingBlock.Narration("远处传来脚步声。")
        )

        val directives = LocalTtsDirector.direct(blocks, 1)

        assertEquals("mysterious", directives.emotion)
        assertEquals("soft tone", directives.delivery)
        assertEquals(280, directives.pauseAfterMs)
    }

    @Test
    fun directivesJsonRoundTripsAndMalformedJsonFallsBack() {
        val original = TtsDirectives("happy", "shouting", 120, 850, 1.2f, 3f)

        assertEquals(original, directivesFromJson(original.toJson()))
        assertEquals(TtsDirectives(), directivesFromJson("not-json"))
    }
}
