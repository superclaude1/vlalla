package com.storybrain.app.analysis

import org.json.JSONObject

/**
 * Extracts the first balanced JSON object that satisfies the requested shape.
 * Models and gateways often wrap JSON in Markdown or explanatory text; the
 * scanner deliberately understands quoted strings so braces in content do not
 * terminate the object early.
 */
internal fun extractJsonObject(
    raw: String,
    requiredKeys: Set<String> = emptySet()
): String {
    val fenced = Regex(
        "```(?:json)?\\s*([\\s\\S]*?)```",
        RegexOption.IGNORE_CASE
    ).find(raw)?.groupValues?.get(1)
    val source = (fenced ?: raw).removePrefix("\\uFEFF").trim()

    for (start in source.indices) {
        if (source[start] != '{') continue
        val candidate = scanBalancedObject(source, start) ?: continue
        val parsed = runCatching { JSONObject(candidate) }.getOrNull() ?: continue
        if (requiredKeys.all(parsed::has)) return candidate
        if (requiredKeys.isEmpty()) return candidate
    }

    throw IllegalArgumentException("LLM 没有返回可解析的 JSON 对象")
}

private fun scanBalancedObject(source: String, start: Int): String? {
    var depth = 0
    var inString = false
    var escaped = false

    for (index in start until source.length) {
        val char = source[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return source.substring(start, index + 1)
            }
        }
    }
    return null
}
