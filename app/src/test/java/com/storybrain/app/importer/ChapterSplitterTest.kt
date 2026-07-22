package com.storybrain.app.importer

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterSplitterTest {
    @Test
    fun recognizesChineseChapterHeadingsAndPreface() {
        val text = """
            这是书籍简介。

            第一章 初见
            张三走进城门。“终于到了。”他说。

            第二章 重逢
            李四站在桥边。
        """.trimIndent()

        val chapters = ChapterSplitter.split(text)

        assertEquals(3, chapters.size)
        assertEquals("序章", chapters[0].title)
        assertEquals("第一章 初见", chapters[1].title)
        assertEquals("第二章 重逢", chapters[2].title)
    }

    @Test
    fun fallsBackToLengthBasedChapters() {
        val text = "这是一句话。".repeat(1000)
        val chapters = ChapterSplitter.split(text, fallbackLength = 500)
        assertTrue(chapters.size > 1)
        assertTrue(chapters.all { it.content.isNotBlank() })
    }

    @Test
    fun streamsLargeFileWithoutCreatingOneHugeChapter() {
        val bytes = ByteArray(16 * 1024 * 1024) { 'a'.code.toByte() }

        val novel = NovelStreamImporter.parse(ByteArrayInputStream(bytes), "大文件", fallbackLength = 4000)

        assertTrue(novel.chapters.size > 4000)
        assertTrue(novel.chapters.all { it.content.length <= 4000 })
        assertEquals(bytes.size, novel.chapters.sumOf { it.content.length })
    }

    @Test
    fun streamingImporterRecognizesUtf8Headings() {
        val text = "序言\n第一章 开始\n正文一\n第二章 继续\n正文二"

        val novel = NovelStreamImporter.parse(ByteArrayInputStream(text.toByteArray()), "测试")

        assertEquals(listOf("序章", "第一章 开始", "第二章 继续"), novel.chapters.map { it.title })
    }
}
