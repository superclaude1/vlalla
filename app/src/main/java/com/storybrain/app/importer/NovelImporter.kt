package com.storybrain.app.importer

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream

data class ParsedChapter(val title: String, val content: String)
data class ImportedNovel(val suggestedTitle: String, val chapters: List<ParsedChapter>)

object NovelTextDecoder {
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, charset("GB18030"))
        }
    }
}

object ChapterSplitter {
    private val heading = Regex(
        pattern = """(?im)^\s*((?:第\s*[0-9零〇一二两三四五六七八九十百千万]+\s*[章节卷回部篇集]|卷\s*[0-9零〇一二两三四五六七八九十百千万]+|(?:序章|楔子|引子|前言|后记|终章|尾声|番外(?:\s*[0-9零〇一二两三四五六七八九十]*)?)|(?:chapter|part)\s*\d+)(?:[\s：:、.．_-]+[^\r\n]{1,40})?)\s*$"""
    )

    fun split(rawText: String, fallbackLength: Int = 4000): List<ParsedChapter> {
        val normalized = rawText.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isBlank()) return emptyList()

        val matches = heading.findAll(normalized).filter { match ->
            val beforeStart = (match.range.first - 300).coerceAtLeast(0)
            val afterEnd = (match.range.last + 301).coerceAtMost(normalized.length)
            val before = normalized.substring(beforeStart, match.range.first)
            val after = normalized.substring(match.range.last + 1, afterEnd)
            before.isBlank() || before.count { it == '\n' } >= 1 || after.count { it == '\n' } >= 1
        }.toList()

        if (matches.isEmpty()) return splitByLength(normalized, fallbackLength)

        val result = mutableListOf<ParsedChapter>()
        val preface = normalized.substring(0, matches.first().range.first).trim()
        if (preface.isNotBlank()) result += ParsedChapter("序章", preface)
        matches.forEachIndexed { index, match ->
            val contentStart = match.range.last + 1
            val contentEnd = matches.getOrNull(index + 1)?.range?.first ?: normalized.length
            val content = normalized.substring(contentStart, contentEnd).trim()
            if (content.isNotBlank()) result += ParsedChapter(match.groupValues[1].trim(), content)
        }
        return result.ifEmpty { splitByLength(normalized, fallbackLength) }
    }

    internal fun headingTitle(line: String): String? =
        heading.matchEntire(line)?.groupValues?.get(1)?.trim()

    private fun splitByLength(text: String, target: Int): List<ParsedChapter> {
        val chapters = mutableListOf<ParsedChapter>()
        var start = 0
        while (start < text.length) {
            val idealEnd = (start + target).coerceAtMost(text.length)
            val end = if (idealEnd == text.length) text.length else findBoundary(text, start, idealEnd)
            val content = text.substring(start, end).trim()
            if (content.isNotBlank()) chapters += ParsedChapter("第${chapters.size + 1}章", content)
            start = end
        }
        return chapters
    }

    private fun findBoundary(text: String, start: Int, idealEnd: Int): Int {
        val lower = (idealEnd - 500).coerceAtLeast(start + 1)
        for (index in idealEnd downTo lower) {
            if (text[index - 1] in charArrayOf('。', '！', '？', '\n')) return index
        }
        return idealEnd
    }
}

/**
 * Imports large novels without first holding the source bytes and several full-text copies in RAM.
 * Only the current chapter is mutable while the input is decoded and split line by line.
 */
object NovelStreamImporter {
    private const val SAMPLE_SIZE = 64 * 1024

    fun parse(input: InputStream, suggestedTitle: String, fallbackLength: Int = 4000): ImportedNovel {
        require(fallbackLength > 0) { "fallbackLength must be positive" }
        val stream = PushbackInputStream(input.buffered(SAMPLE_SIZE), SAMPLE_SIZE)
        val sample = ByteArray(SAMPLE_SIZE)
        val sampleLength = stream.read(sample).coerceAtLeast(0)
        if (sampleLength > 0) stream.unread(sample, 0, sampleLength)

        val encoding = detectEncoding(sample, sampleLength)
        repeat(encoding.bomLength) {
            if (stream.read() == -1) return@repeat
        }

        val chapters = mutableListOf<ParsedChapter>()
        val content = StringBuilder(fallbackLength.coerceAtLeast(4096))
        var currentTitle: String? = null
        var foundHeading = false

        fun addChapter(title: String, text: String) {
            val clean = text.trim()
            if (clean.isNotEmpty()) chapters += ParsedChapter(title, clean)
        }

        fun flushCurrent() {
            if (content.isBlank()) {
                content.setLength(0)
                return
            }
            val title = currentTitle ?: if (foundHeading) "序章" else "第${chapters.size + 1}章"
            addChapter(title, content.toString())
            content.setLength(0)
        }

        fun appendBeforeFirstHeading(text: String) {
            var offset = 0
            while (offset < text.length) {
                val available = fallbackLength - content.length
                val count = minOf(available, text.length - offset)
                content.append(text, offset, offset + count)
                offset += count
                if (content.length >= fallbackLength) flushCurrent()
            }
        }

        InputStreamReader(stream, encoding.charset).buffered(32 * 1024).useLines { lines ->
            lines.forEach { line ->
                val headingTitle = ChapterSplitter.headingTitle(line)
                if (headingTitle != null) {
                    if (!foundHeading) foundHeading = true
                    flushCurrent()
                    currentTitle = headingTitle
                } else if (foundHeading) {
                    content.append(line).append('\n')
                } else {
                    appendBeforeFirstHeading(line)
                    appendBeforeFirstHeading("\n")
                }
            }
        }
        flushCurrent()
        return ImportedNovel(suggestedTitle, chapters)
    }

    private fun detectEncoding(sample: ByteArray, length: Int): Encoding {
        if (length >= 3 && sample[0] == 0xEF.toByte() && sample[1] == 0xBB.toByte() && sample[2] == 0xBF.toByte()) {
            return Encoding(StandardCharsets.UTF_8, 3)
        }
        if (length >= 2 && sample[0] == 0xFF.toByte() && sample[1] == 0xFE.toByte()) {
            return Encoding(StandardCharsets.UTF_16LE, 2)
        }
        if (length >= 2 && sample[0] == 0xFE.toByte() && sample[1] == 0xFF.toByte()) {
            return Encoding(StandardCharsets.UTF_16BE, 2)
        }
        return if (isValidUtf8(sample, length)) {
            Encoding(StandardCharsets.UTF_8, 0)
        } else {
            Encoding(Charset.forName("GB18030"), 0)
        }
    }

    private fun isValidUtf8(sample: ByteArray, length: Int): Boolean {
        if (length == 0) return true
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val result = decoder.decode(
            ByteBuffer.wrap(sample, 0, length),
            CharBuffer.allocate(length),
            false
        )
        return !result.isError
    }

    private data class Encoding(val charset: Charset, val bomLength: Int)
}
