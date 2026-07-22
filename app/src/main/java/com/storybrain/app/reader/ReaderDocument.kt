package com.storybrain.app.reader

import com.storybrain.app.data.ReadingMode

data class ReaderDocument(
    val source: String,
    val originalBlocks: List<ReadingBlock>,
    val chatBlocks: List<ReadingBlock>
) {
    fun blocks(mode: ReadingMode): List<ReadingBlock> = when (mode) {
        ReadingMode.CHAT -> chatBlocks
        ReadingMode.ORIGINAL -> originalBlocks
    }

    fun blockIndexAt(mode: ReadingMode, sourceOffset: Int): Int {
        val blocks = blocks(mode)
        if (blocks.isEmpty()) return 0
        val offset = sourceOffset.coerceIn(0, source.length)
        val exact = blocks.indexOfFirst {
            offset in it.sourceStart until it.sourceEnd.coerceAtLeast(it.sourceStart + 1)
        }
        if (exact >= 0) return exact
        return blocks.indexOfLast { it.sourceStart <= offset }.coerceAtLeast(0)
    }

    companion object {
        fun create(source: String, knownSpeakers: Map<String, String> = emptyMap()): ReaderDocument {
            val original = Regex("[^\\r\\n]+").findAll(source).mapNotNull { match ->
                val raw = match.value
                val leading = raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val trailing = raw.indexOfLast { !it.isWhitespace() }
                if (trailing < leading) null else ReadingBlock.Narration(
                    text = raw.substring(leading, trailing + 1),
                    sourceStart = match.range.first + leading,
                    sourceEnd = match.range.first + trailing + 1
                )
            }.toList().ifEmpty {
                listOf(ReadingBlock.Narration(source, 0, source.length)).filter { source.isNotBlank() }
            }
            return ReaderDocument(
                source = source,
                originalBlocks = original,
                chatBlocks = TextToChatParser.parse(source, knownSpeakers)
            )
        }
    }
}
