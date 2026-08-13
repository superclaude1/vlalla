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

    @Test
    fun treatsQuotedTermAsNarrationRatherThanDialogue() {
        val blocks = TextToChatParser.parse("众人谈论所谓“天命”，却没有人开口。")

        assertTrue("blocks=$blocks", blocks.none { it is ReadingBlock.Dialogue })
        assertTrue(blocks.filterIsInstance<ReadingBlock.Narration>().joinToString("").contains("天命"))
    }

    @Test
    fun recognizesUnquotedColonDialogue() {
        val blocks = TextToChatParser.parse("张三：我们走吧。")

        val dialogue = blocks.filterIsInstance<ReadingBlock.Dialogue>().single()
        assertEquals("张三", dialogue.speaker)
        assertEquals("我们走吧。", dialogue.text)
    }

    @Test
    fun parsesMultipleUnquotedColonDialoguesSeparately() {
        val blocks = TextToChatParser.parse("张三：你好。李四：再见。")

        assertEquals(listOf("张三", "李四"), blocks.filterIsInstance<ReadingBlock.Dialogue>().map { it.speaker })
        assertEquals(listOf("你好。", "再见。"), blocks.filterIsInstance<ReadingBlock.Dialogue>().map { it.text })
    }

    @Test
    fun preservesMixedQuotedNarrationAndFollowingColonDialogue() {
        val blocks = TextToChatParser.parse("旁白说所谓“天命”。 张三：走吧。")

        assertTrue(blocks.any { it is ReadingBlock.Narration && it.text.contains("天命") })
        assertEquals("张三", blocks.filterIsInstance<ReadingBlock.Dialogue>().single().speaker)
        assertEquals("走吧。", blocks.filterIsInstance<ReadingBlock.Dialogue>().single().text)
    }

    @Test
    fun acceptsWhitespaceBeforeUnquotedColonSpeaker() {
        val blocks = TextToChatParser.parse("前言。  张三：我们走吧。")

        assertEquals("张三", blocks.filterIsInstance<ReadingBlock.Dialogue>().single().speaker)
    }

    @Test
    fun recognizesSpeakerAfterQuotedDialogueWithComma() {
        val blocks = TextToChatParser.parse("“好。”，张三说道。")

        assertEquals("张三", blocks.filterIsInstance<ReadingBlock.Dialogue>().single().speaker)
        assertEquals("好。", blocks.filterIsInstance<ReadingBlock.Dialogue>().single().text)
    }

    @Test
    fun recognizesSighingSpeakerWithoutLeakingVerbIntoName() {
        val blocks = TextToChatParser.parse("张三说道：“先走。”李四叹道：“等等。”")

        assertEquals(listOf("张三", "李四"), blocks.filterIsInstance<ReadingBlock.Dialogue>().map { it.speaker })
    }

    @Test
    fun doesNotInheritSpeakerAcrossDistantUnattributedQuote() {
        val blocks = TextToChatParser.parse(
            "张三说道：“我们先走。”这里有一段与对白无关的很长旁白，描述风声、山路、远处的灯火以及众人逐渐消失的脚步，过了很久以后才听见：“那是什么？”"
        )

        assertEquals(
            "未识别角色",
            blocks.filterIsInstance<ReadingBlock.Dialogue>().last().speaker
        )
    }

    @Test
    fun longNarrationChunksDoNotStartWithPunctuationAndReassemblesExactly() {
        val narration = "甲".repeat(100) + "，" + "乙".repeat(220)

        val narrationBlocks = TextToChatParser.parse(narration).filterIsInstance<ReadingBlock.Narration>()

        assertTrue(narrationBlocks.all { it.text.firstOrNull()?.let { first -> first !in "，。！？!?；;：:" } ?: true })
        assertEquals(narration, narrationBlocks.joinToString("") { it.text })
    }
}
