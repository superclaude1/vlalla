package com.storybrain.app.reader

sealed interface ReadingBlock {
    val text: String

    data class Narration(override val text: String) : ReadingBlock
    data class Dialogue(val speaker: String, override val text: String) : ReadingBlock
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
                splitNarration(narration).forEach { blocks += ReadingBlock.Narration(it) }
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
            blocks += ReadingBlock.Dialogue(speaker, match.groupValues[1].trim())
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            splitNarration(text.substring(cursor)).forEach { blocks += ReadingBlock.Narration(it) }
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

    private fun splitNarration(raw: String, targetLength: Int = 100): List<String> {
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
                sentence.chunked(targetLength).forEach { output += it }
            } else {
                current.append(sentence)
            }
        }
        if (current.isNotEmpty()) output += current.toString()
        return output
    }
}
