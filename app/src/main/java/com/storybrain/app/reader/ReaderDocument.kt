package com.storybrain.app.reader

import com.storybrain.app.data.DialogueAnnotationEntity
import com.storybrain.app.ui.ReaderMode

/**
 * 章节阅读文档：正文 + 按模式解析的带偏移阅读块。
 * sourceOffset（字符偏移）是进度与标记的唯一锚。
 */
data class ReaderDocument(
    val source: String,
    val originalBlocks: List<ReadingBlock>,
    val chatBlocks: List<ReadingBlock>
) {
    fun blocks(mode: ReaderMode): List<ReadingBlock> = when (mode) {
        ReaderMode.PLAIN_TEXT -> originalBlocks
        ReaderMode.DIALOGUE -> chatBlocks
    }

    /** 由 sourceOffset 定位当前块索引（找不到精确块时取包含该偏移的最近块）。 */
    fun blockIndexAt(mode: ReaderMode, sourceOffset: Int): Int {
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
        /** Builds the same block model from locally validated source annotations. */
        fun createFromAnnotations(
            source: String,
            annotations: List<DialogueAnnotationEntity>
        ): List<ReadingBlock> {
            val sorted = annotations
                .filter { it.sourceStart >= 0 && it.sourceEnd <= source.length && it.sourceStart < it.sourceEnd }
                .sortedWith(compareBy<DialogueAnnotationEntity> { it.sourceStart }.thenBy { it.sourceEnd })
            if (sorted.isEmpty()) return emptyList()

            val blocks = mutableListOf<ReadingBlock>()
            var cursor = 0
            fun appendNarration(start: Int, end: Int) {
                if (end <= start) return
                val raw = source.substring(start, end)
                val leading = raw.indexOfFirst { !it.isWhitespace() }
                val trailing = raw.indexOfLast { !it.isWhitespace() }
                if (leading >= 0 && trailing >= leading) {
                    blocks += ReadingBlock.Narration(
                        text = raw.substring(leading, trailing + 1),
                        sourceStart = start + leading,
                        sourceEnd = start + trailing + 1
                    )
                }
            }

            sorted.forEach { annotation ->
                if (annotation.sourceStart < cursor) return@forEach
                appendNarration(cursor, annotation.sourceStart)
                val dialogueOffset = annotation.sourceText.indexOf(annotation.dialogueText)
                val dialogueStart = if (dialogueOffset >= 0) {
                    annotation.sourceStart + dialogueOffset
                } else {
                    annotation.sourceStart
                }
                blocks += ReadingBlock.Dialogue(
                    speaker = annotation.speakerName ?: "未识别角色",
                    text = annotation.dialogueText,
                    sourceStart = dialogueStart,
                    sourceEnd = (dialogueStart + annotation.dialogueText.length)
                        .coerceAtMost(annotation.sourceEnd)
                )
                cursor = annotation.sourceEnd
            }
            appendNarration(cursor, source.length)
            return blocks.filter { it.text.isNotBlank() }
        }

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
