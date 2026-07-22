package com.storybrain.app.reader

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

object TextToChatParser {
    private val quote = Regex("[“「『](.*?)[”」』]", setOf(RegexOption.DOT_MATCHES_ALL))
    private const val speechVerbPattern = "(?:回答道|回应道|开口道|说道|问道|答道|喊道|叫道|吼道|回答|回应|开口|说|问|道|喊|答|叫|吼|嘀咕)"
    private val speechVerb = Regex(speechVerbPattern)
    private val speakerBefore = Regex("""([\u4e00-\u9fa5·]{1,10}?)(?:轻声|低声|大声|忽然|缓缓|冷冷|笑着|哭着)?(?:地)?$speechVerbPattern[：:,，\s]*$""")
    private val speakerAfter = Regex("""^\s*[,，。！？!?]*(?:是)?([\u4e00-\u9fa5·]{1,10}?)(?:轻声|低声|大声|忽然|缓缓|冷冷|笑着|哭着)?(?:地)?$speechVerbPattern""")

    /** knownSpeakers maps canonical names and aliases to the canonical character name. */
    fun parse(text: String, knownSpeakers: Map<String, String> = emptyMap()): List<ReadingBlock> {
        val blocks = mutableListOf<ReadingBlock>()
        var cursor = 0
        var lastKnownSpeaker = "未识别角色"
        val aliases = knownSpeakers.keys.filter { it.isNotBlank() }.sortedByDescending { it.length }
        quote.findAll(text).forEach { match ->
            if (match.range.first > cursor) {
                val narration = text.substring(cursor, match.range.first)
                splitNarration(narration, cursor).forEach {
                    blocks += ReadingBlock.Narration(it.text, it.sourceStart, it.sourceEnd)
                }
            }
            val beforeStart = (match.range.first - 80).coerceAtLeast(0)
            val afterEnd = (match.range.last + 81).coerceAtMost(text.length)
            val before = text.substring(beforeStart, match.range.first)
            val after = text.substring(match.range.last + 1, afterEnd)
            val guessed = speakerBefore.find(before)?.groupValues?.get(1)
                ?: speakerAfter.find(after)?.groupValues?.get(1)
            val speaker = resolveKnownSpeaker(guessed, knownSpeakers)
                ?: findKnownSpeakerNearby(before, after, aliases, knownSpeakers)
                ?: guessed
                ?: lastKnownSpeaker
            if (speaker != "未识别角色") lastKnownSpeaker = speaker
            val group = match.groups[1]
            val rawDialogue = match.groupValues[1]
            val leadingWhitespace = rawDialogue.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val trailingExclusive = rawDialogue.indexOfLast { !it.isWhitespace() }
                .let { if (it < 0) leadingWhitespace else it + 1 }
            val groupStart = group?.range?.first ?: match.range.first
            blocks += ReadingBlock.Dialogue(
                speaker = speaker,
                text = rawDialogue.trim(),
                sourceStart = groupStart + leadingWhitespace,
                sourceEnd = groupStart + trailingExclusive
            )
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            splitNarration(text.substring(cursor), cursor).forEach {
                blocks += ReadingBlock.Narration(it.text, it.sourceStart, it.sourceEnd)
            }
        }
        return blocks.filter { it.text.isNotBlank() }
    }

    private fun resolveKnownSpeaker(candidate: String?, knownSpeakers: Map<String, String>): String? {
        if (candidate.isNullOrBlank()) return null
        knownSpeakers[candidate]?.let { return it }
        return knownSpeakers.entries
            .filter { (alias, _) -> alias.length >= 2 && candidate.contains(alias) }
            .maxByOrNull { it.key.length }
            ?.value
    }

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

    private fun splitNarration(raw: String, rawStart: Int, targetLength: Int = 100): List<TextSlice> {
        if (raw.isBlank()) return emptyList()
        val output = mutableListOf<TextSlice>()
        var chunkStart = 0
        while (chunkStart < raw.length) {
            while (chunkStart < raw.length && raw[chunkStart].isWhitespace()) chunkStart++
            if (chunkStart >= raw.length) break
            var chunkEnd = (chunkStart + targetLength).coerceAtMost(raw.length)
            if (chunkEnd < raw.length) {
                val boundary = raw.indexOfAny(charArrayOf('。', '！', '？', '!', '?', '\n'), chunkEnd)
                val maximum = (chunkStart + targetLength * 2).coerceAtMost(raw.length)
                if (boundary in chunkEnd until maximum) chunkEnd = boundary + 1
            }
            while (chunkEnd > chunkStart && raw[chunkEnd - 1].isWhitespace()) chunkEnd--
            if (chunkEnd > chunkStart) {
                val value = raw.substring(chunkStart, chunkEnd).replace(Regex("[\\t ]+"), " ").trim()
                if (value.isNotBlank()) output += TextSlice(value, rawStart + chunkStart, rawStart + chunkEnd)
            }
            chunkStart = chunkEnd.coerceAtLeast(chunkStart + 1)
        }
        return output
    }
}
