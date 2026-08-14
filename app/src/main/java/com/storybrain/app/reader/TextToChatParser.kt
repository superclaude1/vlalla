package com.storybrain.app.reader

/**
 * 带正文字符偏移的阅读块。sourceStart/sourceEnd 用于：
 * 1. 阅读进度锚定（reading_positions.sourceOffset → 对应块）
 * 2. 书签/高亮/批注的精确区间定位
 */
sealed interface ReadingBlock {
    val text: String
    val sourceStart: Int
    val sourceEnd: Int

    data class Narration(
        override val text: String,
        override val sourceStart: Int = 0,
        override val sourceEnd: Int = sourceStart + text.length
    ) : ReadingBlock

    data class Dialogue(
        val speaker: String,
        override val text: String,
        override val sourceStart: Int = 0,
        override val sourceEnd: Int = sourceStart + text.length
    ) : ReadingBlock
}

/**
 * 解析逻辑与 0.4.2 完全一致（对话线索判定、无跨段说话人继承、切块重拼精确），
 * 仅在块上附带正文字符偏移（sourceStart/sourceEnd）。
 */
object TextToChatParser {
    private val quote = Regex("[“「『](.*?)[”」』]", setOf(RegexOption.DOT_MATCHES_ALL))
    private const val speechVerbPattern = "(?:回答道|回应道|开口道|说道|问道|答道|喊道|叫道|吼道|叹道|回答|回应|开口|说|问|道|喊|答|叫|吼|嘀咕)"
    private val speechVerb = Regex(speechVerbPattern)
    private val speakerBefore = Regex("""([\u4e00-\u9fa5·]{1,10}?)(?:轻声|低声|大声|忽然|缓缓|冷冷|笑着|哭着)?(?:地)?$speechVerbPattern[：:,，\s]*$""")
    private val speakerAfter = Regex("""^\s*[,，。！？!?]*(?:是)?([\u4e00-\u9fa5·]{1,10}?)(?:轻声|低声|大声|忽然|缓缓|冷冷|笑着|哭着)?(?:地)?$speechVerbPattern""")

    /** knownSpeakers maps canonical names and aliases to the canonical character name. */
    fun parse(text: String, knownSpeakers: Map<String, String> = emptyMap()): List<ReadingBlock> {
        val matches = quote.findAll(text).toList()
        if (matches.isEmpty()) return parseUnquotedColon(text, 0, knownSpeakers)
        val blocks = mutableListOf<ReadingBlock>()
        var cursor = 0
        val aliases = knownSpeakers.keys.filter { it.isNotBlank() }.sortedByDescending { it.length }
        matches.forEach { match ->
            if (match.range.first > cursor) {
                parseUnquotedColon(text.substring(cursor, match.range.first), cursor, knownSpeakers).forEach { blocks += it }
            }
            val beforeStart = (match.range.first - 80).coerceAtLeast(0)
            val afterEnd = (match.range.last + 81).coerceAtMost(text.length)
            val before = text.substring(beforeStart, match.range.first)
            val after = text.substring(match.range.last + 1, afterEnd)
            val guessed = speakerBefore.find(before)?.groupValues?.get(1)
                ?: speakerAfter.find(after)?.groupValues?.get(1)?.takeIf(::isLikelySpeaker)
            val nearbySpeaker = findKnownSpeakerNearby(before, after, aliases, knownSpeakers)
            val hasDialogueCue = guessed != null || nearbySpeaker != null ||
                before.trimEnd().endsWith("：") || before.trimEnd().endsWith(":")
            if (hasDialogueCue) {
                val speaker = resolveKnownSpeaker(guessed, knownSpeakers)
                    ?: nearbySpeaker
                    ?: guessed
                    ?: "未识别角色"
                val rawDialogue = match.groupValues[1]
                val leadingWhitespace = rawDialogue.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val trailingExclusive = rawDialogue.indexOfLast { !it.isWhitespace() }
                    .let { if (it < 0) leadingWhitespace else it + 1 }
                val groupStart = match.groups[1]?.range?.first ?: match.range.first
                blocks += ReadingBlock.Dialogue(
                    speaker = speaker,
                    text = rawDialogue.trim(),
                    sourceStart = groupStart + leadingWhitespace,
                    sourceEnd = groupStart + trailingExclusive
                )
            } else {
                blocks += ReadingBlock.Narration(match.value, match.range.first, match.range.last + 1)
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            parseUnquotedColon(text.substring(cursor), cursor, knownSpeakers).forEach { blocks += it }
        }
        return blocks.filter { it.text.isNotBlank() }
    }

    private fun parseUnquotedColon(
        text: String,
        base: Int,
        knownSpeakers: Map<String, String>
    ): List<ReadingBlock> {
        val markers = Regex("(?s)(?:^|(?<=[。！？!?；;\\n]))\\s*([\\u4e00-\\u9fa5·]{1,10})[：:]").findAll(text).toList()
        if (markers.isEmpty()) {
            return splitNarration(text, base).map { ReadingBlock.Narration(it.text, it.sourceStart, it.sourceEnd) }
        }
        return buildList {
            val first = markers.first()
            splitNarration(text.substring(0, first.range.first), base).forEach {
                add(ReadingBlock.Narration(it.text, it.sourceStart, it.sourceEnd))
            }
            markers.forEachIndexed { index, marker ->
                val speaker = resolveKnownSpeaker(marker.groupValues[1], knownSpeakers) ?: marker.groupValues[1]
                val contentStart = marker.range.last + 1
                val end = markers.getOrNull(index + 1)?.range?.first ?: text.length
                val content = text.substring(contentStart, end).trim()
                if (content.isNotBlank()) {
                    add(ReadingBlock.Dialogue(speaker, content, base + contentStart, base + end))
                }
            }
        }.filter { it.text.isNotBlank() }
    }

    private fun resolveKnownSpeaker(candidate: String?, knownSpeakers: Map<String, String>): String? {
        if (candidate.isNullOrBlank()) return null
        knownSpeakers[candidate]?.let { return it }
        return knownSpeakers.entries
            .filter { (alias, _) -> alias.length >= 2 && candidate.contains(alias) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    private fun isLikelySpeaker(candidate: String): Boolean =
        candidate.isNotBlank() && listOf("没有人", "有人", "大家", "众人", "人们").none(candidate::contains)

    private fun findKnownSpeakerNearby(
        before: String,
        after: String,
        aliases: List<String>,
        knownSpeakers: Map<String, String>
    ): String? {
        var best: Pair<Int, String>? = null
        aliases.forEach { alias ->
            if (alias.length < 2) return@forEach
            val beforeIndex = before.lastIndexOf(alias)
            if (beforeIndex >= 0) {
                val tail = before.substring(beforeIndex + alias.length)
                if (tail.length <= 20 && (speechVerb.containsMatchIn(tail) || tail.trim().endsWith("：") || tail.trim().endsWith(":"))) {
                    val score = 1_000 + beforeIndex + alias.length
                    if (best == null || score > best!!.first) best = score to knownSpeakers.getValue(alias)
                }
            }
            val afterIndex = after.indexOf(alias)
            if (afterIndex in 0..16) {
                val nearby = after.take((afterIndex + alias.length + 20).coerceAtMost(after.length))
                if (speechVerb.containsMatchIn(nearby)) {
                    val score = 900 - afterIndex + alias.length
                    if (best == null || score > best!!.first) best = score to knownSpeakers.getValue(alias)
                }
            }
        }
        return best?.second
    }

    private data class TextSlice(val text: String, val sourceStart: Int, val sourceEnd: Int)

    /** 切块算法与 0.4.2 完全一致；偏移通过在原串中回查文本定位（归一化仅折叠空白，回查可靠）。 */
    private fun splitNarration(raw: String, rawStart: Int, targetLength: Int = 100): List<TextSlice> {
        val chunks = splitNarrationText(raw, targetLength)
        val slices = mutableListOf<TextSlice>()
        var cursor = 0
        for (chunk in chunks) {
            val idx = raw.indexOf(chunk, cursor)
            val start = if (idx >= 0) idx else cursor
            slices += TextSlice(chunk, rawStart + start, rawStart + start + chunk.length)
            cursor = start + chunk.length
        }
        return slices
    }

    private fun splitNarrationText(raw: String, targetLength: Int = 100): List<String> {
        val compact = raw.replace(Regex("[\\t ]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
        if (compact.isBlank()) return emptyList()
        val sentences = compact.split(Regex("(?<=[。！？!?；;])|\\n{2,}"))
            .map { it.trim() }.filter { it.isNotBlank() }
        val output = mutableListOf<String>()
        var current = StringBuilder()
        for (sentence in sentences) {
            if (current.isNotEmpty() && current.length + sentence.length > targetLength) {
                output += current.toString()
                current = StringBuilder()
            }
            if (sentence.length > targetLength * 2) {
                if (current.isNotEmpty()) {
                    output += current.toString()
                    current = StringBuilder()
                }
                var start = 0
                while (start < sentence.length) {
                    var end = minOf(start + targetLength, sentence.length)
                    while (end < sentence.length && sentence[end] in "，。！？!?；;：:") end++
                    output += sentence.substring(start, end)
                    start = end
                }
            } else {
                current.append(sentence)
            }
        }
        if (current.isNotEmpty()) output += current.toString()
        return output
    }
}
