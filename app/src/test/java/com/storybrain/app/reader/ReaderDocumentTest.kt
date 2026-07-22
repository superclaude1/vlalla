package com.storybrain.app.reader

import com.storybrain.app.data.ReadingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderDocumentTest {
    @Test
    fun modeSwitchUsesTheSameSourceOffset() {
        val source = "第一段旁白。\n\n林清说道：“我们出发吧。”\n最后一段。"
        val document = ReaderDocument.create(source, mapOf("林清" to "林清"))
        val offset = source.indexOf("我们")

        val chat = document.chatBlocks[document.blockIndexAt(ReadingMode.CHAT, offset)]
        val original = document.originalBlocks[document.blockIndexAt(ReadingMode.ORIGINAL, offset)]

        assertTrue(offset in chat.sourceStart until chat.sourceEnd)
        assertTrue(offset in original.sourceStart until original.sourceEnd)
        assertEquals("我们出发吧。", chat.text)
    }

    @Test
    fun narrationChunksRetainMonotonicSourceRanges() {
        val source = (1..20).joinToString("") { "第${it}句旁白。" }
        val blocks = ReaderDocument.create(source).chatBlocks

        assertTrue(blocks.zipWithNext().all { (left, right) -> left.sourceEnd <= right.sourceStart })
        blocks.forEach { block ->
            assertTrue(block.sourceStart >= 0)
            assertTrue(block.sourceEnd <= source.length)
        }
    }
}
