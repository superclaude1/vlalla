package com.storybrain.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BookHeaderParserTest {
    @Test
    fun extractsAuthorFromImportedTitle() {
        assertEquals(
            BookHeader("电子哪吒", "张小花"),
            BookHeaderParser.parse("电子哪吒 作者：张小花")
        )
    }

    @Test
    fun keepsPlainTitleWithoutInventingAuthor() {
        assertEquals(BookHeader("夜航船", null), BookHeaderParser.parse("夜航船"))
    }

    @Test
    fun supportsAsciiColonAndWhitespace() {
        assertEquals(BookHeader("旧霜", "沈明"), BookHeaderParser.parse(" 旧霜  作者: 沈明 "))
    }

    @Test
    fun keepsParenthesizedAuthorNameInsteadOfTreatingItAsRegion() {
        assertEquals(BookHeader("夜航船", "（张三）"), BookHeaderParser.parse("《夜航船》 作者：（张三）"))
    }

    @Test
    fun removesOnlyKnownRegionPrefix() {
        assertEquals(BookHeader("银河铁道之夜", "宫泽贤治"), BookHeaderParser.parse("《银河铁道之夜》 作者：[日] 宫泽贤治"))
    }
}
