package com.storybrain.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LlmJsonTest {
    @Test
    fun extractsJsonFromMarkdownAndExplanation() {
        assertEquals("{\"characters\":[]}", extractJsonObject("结果如下：\n```json\n{\"characters\":[]}\n```"))
    }

    @Test
    fun rejectsResponseWithoutJsonObject() {
        assertThrows(IllegalArgumentException::class.java) { extractJsonObject("模型暂时无法回答") }
    }
}
