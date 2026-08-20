package com.storybrain.app.analysis

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LlmJsonTest {
    @Test
    fun extractsJsonFromMarkdownAndExplanation() {
        assertEquals("{\"characters\":[]}", extractJsonObject("结果如下：\n```json\n{\"characters\":[]}\n```"))
    }

    @Test
    fun extractsRequiredAnalysisObjectWhenExplanationContainsBraces() {
        val raw = "说明：示例 {不是结果}\n{\"characters\":[],\"relations\":[],\"plotNodes\":[]}\n完成"

        val result = extractJsonObject(
            raw,
            requiredKeys = setOf("characters", "relations", "plotNodes")
        )

        assertEquals(0, JSONObject(result).getJSONArray("characters").length())
    }

    @Test
    fun ignoresBracesInsideJsonStrings() {
        val result = extractJsonObject(
            "前缀 {\"characters\":[],\"relations\":[],\"plotNodes\":[],\"note\":\"文本包含 } 和 {\"}",
            requiredKeys = setOf("characters", "relations", "plotNodes")
        )

        assertEquals("文本包含 } 和 {", JSONObject(result).getString("note"))
    }

    @Test
    fun rejectsTruncatedJson() {
        assertThrows(IllegalArgumentException::class.java) {
            extractJsonObject("{\"characters\":[],\"relations\":[")
        }
    }

    @Test
    fun rejectsResponseWithoutJsonObject() {
        assertThrows(IllegalArgumentException::class.java) { extractJsonObject("模型暂时无法回答") }
    }
}
