package com.storybrain.app.work

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkContractsTest {
    @Test
    fun namesAreStableAndProgressRoundTrips() {
        assertEquals("analysis:book-1", WorkContracts.analysisName("book-1"))
        assertEquals("tts:chapter-1", WorkContracts.ttsName("chapter-1"))
        assertEquals("search-index:book-1", WorkContracts.searchIndexName("book-1"))
        assertEquals(TaskProgress(2, 5, "分析中"), TaskProgress.from(WorkContracts.progress(2, 5, "分析中")))
    }
}
