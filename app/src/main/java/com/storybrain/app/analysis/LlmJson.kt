package com.storybrain.app.analysis

internal fun extractJsonObject(raw: String): String {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    require(start >= 0 && end > start) { "LLM 没有返回可解析的 JSON 对象" }
    return raw.substring(start, end + 1)
}
