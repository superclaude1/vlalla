package com.storybrain.app.reader

/** Reader-only paragraph boundary preservation; TTS dialogue parsing remains independent. */
object ReaderParagraphs {
    fun split(content: String): List<String> = content
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split(Regex("\\n\\s*\\n|\\n"))
        .map(String::trim)
        .filter(String::isNotBlank)
}
