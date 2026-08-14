package com.storybrain.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 固定每行 5 字符的假测量器（中文场景近似）。 */
class FakeLineMeasurer(private val charsPerLine: Int = 5) : LineMeasurer {
    override fun measure(text: String, maxWidthPx: Int): List<LineRange> {
        if (text.isEmpty()) return emptyList()
        return buildList {
            var start = 0
            while (start < text.length) {
                val end = minOf(start + charsPerLine, text.length)
                add(LineRange(start, end))
                start = end
            }
        }
    }
}

class PaginatorTest {
    private fun params(
        pageHeightLines: Int,
        lineHeight: Float = 10f,
        paragraphSpacing: Float = 0f
    ) = PaginationParams(
        contentWidthPx = 100,
        pageHeightPx = (pageHeightLines * lineHeight + paragraphSpacing).toInt(),
        lineHeightPx = lineHeight,
        paragraphSpacingPx = paragraphSpacing
    )

    private fun blocks(vararg texts: String, startAt: Int = 0): List<ReadingBlock> {
        var cursor = startAt
        return texts.map { text ->
            ReadingBlock.Narration(text, cursor, cursor + text.length).also { cursor += text.length + 1 }
        }
    }

    @Test
    fun fillsPageWithLinesInOrder() {
        val pages = Paginator.paginate(blocks("甲乙丙丁戊己庚辛壬癸"), params(pageHeightLines = 2), FakeLineMeasurer(5))

        assertEquals(1, pages.size)
        assertEquals(listOf("甲乙丙丁戊", "己庚辛壬癸"), pages[0].lines.map { it.text })
        assertEquals(0, pages[0].startOffset)
        assertEquals(10, pages[0].endOffset)
    }

    @Test
    fun linesCarryAbsoluteSourceOffsets() {
        // 第一块从 100 开始（10 字符），第二块从 111 开始
        val blocks = listOf(
            ReadingBlock.Narration("零一二三四五六七八九", 100, 110),
            ReadingBlock.Narration("甲乙丙丁戊", 111, 116)
        )
        val pages = Paginator.paginate(blocks, params(pageHeightLines = 2), FakeLineMeasurer(5))

        // 页1: 零一二三四(100..105) 五六七八九(105..110)
        assertEquals(100, pages[0].startOffset)
        assertEquals(100, pages[0].lines[0].startOffset)
        assertEquals(105, pages[0].lines[0].endOffset)
        assertEquals(105, pages[0].lines[1].startOffset)
        assertEquals(110, pages[0].lines[1].endOffset)
        // 页2: 甲乙丙丁戊(111..116)
        assertEquals(111, pages[1].startOffset)
        assertEquals(116, pages[1].endOffset)
    }

    @Test
    fun paragraphSpacingPushesNextBlockToNewPage() {
        // 页高 = 1 行 + 段距；块1 一行 → 加段距后超出 → 块2 换页
        val pages = Paginator.paginate(
            blocks("甲乙丙丁戊", "己庚辛壬癸"),
            params(pageHeightLines = 1, paragraphSpacing = 10f),
            FakeLineMeasurer(5)
        )
        assertEquals(2, pages.size)
        assertEquals(1, pages[0].lines.size)
        assertEquals(1, pages[1].lines.size)
    }

    @Test
    fun oversizeBlockFlowsContinuouslyAcrossPages() {
        // 12 行文本、每页 3 行 → 4 页，偏移连续
        val text = "字".repeat(60)
        val pages = Paginator.paginate(listOf(ReadingBlock.Narration(text, 0, 60)), params(pageHeightLines = 3), FakeLineMeasurer(5))

        assertEquals(4, pages.size)
        var expectedStart = 0
        pages.forEach { page ->
            assertEquals(expectedStart, page.startOffset)
            expectedStart = page.endOffset
        }
        assertEquals(60, pages.last().endOffset)
        assertEquals(3, pages[0].lines.size)
    }

    @Test
    fun pageStartOffsetAnchorsAtFirstLineOfPage() {
        val blocks = blocks("零一二三四", "五六七八九", "甲乙丙丁戊", "己庚辛壬癸")
        val pages = Paginator.paginate(blocks, params(pageHeightLines = 2), FakeLineMeasurer(5))

        assertEquals(2, pages.size)
        assertEquals(0, pages[0].startOffset)
        // 前两块各 1 行正好填满第 1 页；第 3 块换页
        assertEquals(blocks[2].sourceStart, pages[1].startOffset)
        assertEquals(listOf("甲乙丙丁戊", "己庚辛壬癸"), pages[1].lines.map { it.text })
    }

    @Test
    fun emptyInputYieldsSingleEmptyPage() {
        val pages = Paginator.paginate(emptyList(), params(pageHeightLines = 3), FakeLineMeasurer())
        assertEquals(1, pages.size)
        assertTrue(pages[0].lines.isEmpty())
        assertEquals(0, pages[0].startOffset)
    }
}
