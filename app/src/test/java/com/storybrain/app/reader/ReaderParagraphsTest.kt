package com.storybrain.app.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderParagraphsTest {
    @Test
    fun preservesOriginalParagraphBoundariesWithoutHundredCharacterChunking() {
        val longParagraph = "长".repeat(240)
        val text = "$longParagraph\n\n第二段。\n第三段。"

        assertEquals(listOf(longParagraph, "第二段。", "第三段。"), ReaderParagraphs.split(text))
    }

    @Test
    fun normalizesLineEndingsAndDropsBlankParagraphs() {
        assertEquals(listOf("第一段", "第二段"), ReaderParagraphs.split("  第一段  \r\n\r\n \r\n第二段\r\n"))
    }
}
