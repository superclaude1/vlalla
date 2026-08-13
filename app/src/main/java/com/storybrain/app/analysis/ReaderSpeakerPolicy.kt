package com.storybrain.app.analysis

import com.storybrain.app.data.StoryCharacterEntity
import org.json.JSONArray

/** Builds parser/TTS speaker names, giving explicit chapter evidence precedence over global rows. */
object ReaderSpeakerPolicy {
    fun buildKnownSpeakers(
        chapterCharacters: List<StoryCharacterEntity>,
        allCharacters: List<StoryCharacterEntity>
    ): Map<String, String> = buildMap {
        (chapterCharacters + allCharacters).distinctBy { it.id }.forEach { character ->
            put(character.canonicalName, character.canonicalName)
            val aliases = runCatching { JSONArray(character.aliasesJson) }.getOrNull() ?: JSONArray()
            for (index in 0 until aliases.length()) {
                aliases.optString(index).trim().takeIf(String::isNotBlank)?.let { alias ->
                    putIfAbsent(alias, character.canonicalName)
                }
            }
        }
    }
}