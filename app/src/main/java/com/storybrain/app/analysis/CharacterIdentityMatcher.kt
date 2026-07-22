package com.storybrain.app.analysis

import com.storybrain.app.data.StoryCharacterEntity

/** Resolves a character by canonical name or any known alias without Android runtime APIs. */
internal object CharacterIdentityMatcher {
    fun names(character: StoryCharacterEntity): Set<String> = buildSet {
        character.canonicalName.trim().takeIf(String::isNotBlank)?.let(::add)
        addAll(parseJsonStrings(character.aliasesJson))
    }

    fun find(
        existing: List<StoryCharacterEntity>,
        incomingName: String,
        incomingAliases: List<String>
    ): StoryCharacterEntity? {
        val candidates = (listOf(incomingName) + incomingAliases).map(String::trim).filter(String::isNotBlank).toSet()
        return existing.firstOrNull { names(it).any(candidates::contains) }
    }

    fun mergedAliases(
        old: StoryCharacterEntity?,
        incomingName: String,
        incomingAliases: List<String>,
        canonicalName: String
    ): List<String> = buildSet {
        old?.let { addAll(names(it)) }
        addAll(incomingAliases.map(String::trim).filter(String::isNotBlank))
        incomingName.trim().takeIf { it.isNotBlank() && it != canonicalName }?.let(::add)
        remove(canonicalName)
    }.sorted()

    private fun parseJsonStrings(json: String): List<String> =
        Regex("\"((?:\\\\.|[^\"\\\\])*)\"").findAll(json).map { match ->
            match.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .trim()
        }.filter(String::isNotBlank).toList()
}
