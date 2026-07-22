package com.storybrain.app.data

import org.json.JSONArray

object MemorySearch {
    private val latinWord = Regex("[a-z0-9_]{2,}")
    private val cjkRun = Regex("[\\u3400-\\u9fff]+")

    fun terms(vararg values: String): String = tokenize(values.joinToString(" ")).joinToString(" ")

    fun matchQuery(value: String): String = tokenize(value)
        .take(8)
        .joinToString(" AND ") { token -> "\"${token.replace("\"", "\"\"")}\"" }

    fun tokenize(value: String): List<String> {
        val normalized = value.lowercase()
        val tokens = linkedSetOf<String>()
        latinWord.findAll(normalized).forEach { tokens += it.value }
        cjkRun.findAll(normalized).forEach { match ->
            val text = match.value
            if (text.length == 1) tokens += text
            else text.windowed(2).forEach { tokens += it }
        }
        return tokens.toList()
    }

    fun jsonStrings(json: String): List<String> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())

    fun json(values: Iterable<String>): String = JSONArray(values.distinct().toList()).toString()
}
