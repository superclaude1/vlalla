package com.storybrain.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextToChatParserTest {
    @Test
    fun extractsDialogueAndNarration() {
        val blocks = TextToChatParser.parse("风吹过树林。张三说道：“我们走吧。”夜色渐深。")
        val dialogue = blocks.filterIsInstance<ReadingBlock.Dialogue>().single()
        assertEquals("张三", dialogue.speaker)
        assertEquals("我们走吧。", dialogue.text)
        assertTrue(blocks.any { it is ReadingBlock.Narration })
    }

    @Test
    fun mapsAnalyzedAliasToCanonicalCharacter() {
        val blocks = TextToChatParser.parse(
            "小三低声说道：“我们走吧。”",
            mapOf("张三" to "张三", "小三" to "张三")
        )

        val dialogue = blocks.filterIsInstance<ReadingBlock.Dialogue>().single()
        assertEquals("张三", dialogue.speaker)
    }

    @Test
    fun findsAnalyzedCharacterInsideNoisySpeakerPrefix() {
        val blocks = TextToChatParser.parse(
            "风吹过树林，张三缓缓说道：“天黑了。”",
            mapOf("张三" to "张三")
        )

        val dialogue = blocks.filterIsInstance<ReadingBlock.Dialogue>().single()
        assertEquals("张三", dialogue.speaker)
    }

    @Test
    fun keepsExplicitSpeakerWhenAnalysisHasNotLearnedThatAlias() {
        val blocks = TextToChatParser.parse(
            "“知道了。”林师弟回答道。",
            mapOf("张小凡" to "张小凡")
        )

        val dialogue = blocks.filterIsInstance<ReadingBlock.Dialogue>().single()
        assertEquals("林师弟", dialogue.speaker)
    }
}
