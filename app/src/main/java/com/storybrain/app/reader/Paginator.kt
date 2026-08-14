package com.storybrain.app.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints

/** 行区间（[start, end)，相对块文本）。 */
data class LineRange(val start: Int, val end: Int)

/** 页内一行：携带所属块与绝对正文偏移。 */
data class PageLine(
    val blockIndex: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int
)

/**
 * 阅读页：startOffset 为页首行的正文偏移（进度锚），
 * endOffset 为页末行之后的偏移（跨页定位边界）。
 */
data class ReaderPage(
    val index: Int,
    val startOffset: Int,
    val endOffset: Int,
    val lines: List<PageLine>
)

/**
 * 行测量接口（Legado TextChapterLayout 的断行语义）。
 * 生产实现用 Compose TextMeasurer；单元测试注入假实现。
 */
fun interface LineMeasurer {
    fun measure(text: String, maxWidthPx: Int): List<LineRange>
}

data class PaginationParams(
    val contentWidthPx: Int,
    val pageHeightPx: Int,
    val lineHeightPx: Float,
    val paragraphSpacingPx: Float
)

/**
 * Legado 式行级分页（对照 TextChapterLayout.setTypeText）：
 * 1. 逐块断行（LineMeasurer）
 * 2. 逐行填页：当前页放不下整行 → 结页开新页（块可跨页连续流动）
 * 3. 块尾追加段距；每行携带全局正文偏移（块 sourceStart + 行区间）
 */
object Paginator {
    fun paginate(
        blocks: List<ReadingBlock>,
        params: PaginationParams,
        measurer: LineMeasurer
    ): List<ReaderPage> {
        val pages = mutableListOf<ReaderPage>()
        val lines = mutableListOf<PageLine>()
        var pageStartOffset = blocks.firstOrNull()?.sourceStart ?: 0
        var currentY = 0f

        blocks.forEachIndexed { blockIndex, block ->
            val ranges = measurer.measure(block.text, params.contentWidthPx)
            ranges.forEach { range ->
                if (currentY + params.lineHeightPx > params.pageHeightPx && lines.isNotEmpty()) {
                    pages += closePage(pages.size, pageStartOffset, lines)
                    lines.clear()
                    pageStartOffset = block.sourceStart + range.start
                    currentY = 0f
                }
                lines += PageLine(
                    blockIndex = blockIndex,
                    text = block.text.substring(range.start, range.end),
                    startOffset = block.sourceStart + range.start,
                    endOffset = block.sourceStart + range.end
                )
                currentY += params.lineHeightPx
            }
            currentY += params.paragraphSpacingPx
        }
        if (lines.isNotEmpty()) {
            pages += closePage(pages.size, pageStartOffset, lines)
        }
        if (pages.isEmpty()) {
            pages += ReaderPage(index = 0, startOffset = 0, endOffset = 0, lines = emptyList())
        }
        return pages
    }

    private fun closePage(index: Int, startOffset: Int, lines: List<PageLine>): ReaderPage =
        ReaderPage(
            index = index,
            startOffset = startOffset,
            endOffset = lines.lastOrNull()?.endOffset ?: startOffset,
            lines = lines.toList()
        )
}

/** Compose 适配：TextMeasurer → LineMeasurer。 */
class TextMeasurerLineMeasurer(
    private val textMeasurer: TextMeasurer,
    private val style: TextStyle
) : LineMeasurer {
    override fun measure(text: String, maxWidthPx: Int): List<LineRange> {
        if (text.isEmpty()) return emptyList()
        val result: TextLayoutResult = textMeasurer.measure(
            text = AnnotatedString(text),
            style = style,
            constraints = Constraints(maxWidth = maxWidthPx.coerceAtLeast(1)),
            softWrap = true
        )
        return (0 until result.lineCount).map { line ->
            LineRange(start = result.getLineStart(line), end = result.getLineEnd(line))
        }
    }
}
