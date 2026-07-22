package com.storybrain.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySearchTest {
    @Test
    fun createsChineseBigramsAndLatinWords() {
        val tokens = MemorySearch.tokenize("张小凡 protects XiaoFan_1")
        assertTrue("张小" in tokens)
        assertTrue("小凡" in tokens)
        assertTrue("protects" in tokens)
        assertTrue("xiaofan_1" in tokens)
    }

    @Test
    fun createsSafeAndQuery() {
        assertEquals("\"青云\" AND \"云门\"", MemorySearch.matchQuery("青云门"))
    }
}
