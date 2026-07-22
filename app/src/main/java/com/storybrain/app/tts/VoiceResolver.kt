package com.storybrain.app.tts

import com.storybrain.app.data.StoryCharacterEntity
import com.storybrain.app.data.StoryRepository
import com.storybrain.app.data.TtsProfileIds
import com.storybrain.app.data.TtsProviderKind
import com.storybrain.app.data.TtsProviderProfileEntity
import com.storybrain.app.data.TtsVoiceRole
import com.storybrain.app.settings.TtsSettingsStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray

data class ResolvedTtsVoice(
    val profile: TtsProviderProfileEntity,
    val voiceId: String,
    val voiceName: String,
    val explicit: Boolean
)

class VoiceResolver(
    private val repository: StoryRepository,
    private val settings: TtsSettingsStore
) {
    suspend fun resolve(bookId: String, speaker: String?, character: StoryCharacterEntity?): ResolvedTtsVoice {
        if (character != null) {
            repository.getActiveCharacterVoiceBinding(character.id)?.let { binding ->
                val profile = repository.getTtsProfile(binding.profileId) ?: error("角色配音平台已不存在")
                return ResolvedTtsVoice(profile, binding.voiceId, binding.voiceName, true)
            }
        } else {
            repository.getActiveNarratorBinding(bookId)?.let { binding ->
                val profile = repository.getTtsProfile(binding.profileId) ?: error("旁白配音平台已不存在")
                return ResolvedTtsVoice(profile, binding.voiceId, binding.voiceName, true)
            }
        }
        val globalId = settings.config.first().globalProfileId
        val primaryId = repository.getBookTtsSetting(bookId)?.primaryProfileId ?: globalId
        val profile = repository.getTtsProfile(primaryId)
            ?: repository.getTtsProfile(TtsProfileIds.EDGE)
            ?: error("没有可用的配音平台")
        val pool = repository.getTtsVoicePool(profile.id)
        val targetRole = when {
            character == null || speaker == null -> TtsVoiceRole.NARRATOR
            character.gender == "MALE" -> TtsVoiceRole.MALE
            character.gender == "FEMALE" -> TtsVoiceRole.FEMALE
            else -> TtsVoiceRole.UNKNOWN
        }
        val candidates = pool.filter { it.role == targetRole.name }.ifEmpty {
            pool.filter { it.role == TtsVoiceRole.UNKNOWN.name }
        }
        val selected = candidates.maxWithOrNull(
            compareBy<com.storybrain.app.data.TtsProfileVoicePoolEntity> { score(character, it.tagsJson, it.gender) }
                .thenBy { stableTie(character?.id ?: bookId, it.voiceId) }
        )
        if (selected == null) {
            val label = when (TtsProviderKind.valueOf(profile.kind)) {
                TtsProviderKind.FISH_AUDIO -> "Fish Audio"
                TtsProviderKind.OPENAI_COMPATIBLE -> "兼容 TTS"
                TtsProviderKind.EDGE -> "Edge TTS"
            }
            error("$label 尚未配置${if (character == null) "旁白" else "角色"}音色池")
        }
        return ResolvedTtsVoice(profile, selected.voiceId, selected.voiceName, false)
    }

    private fun score(character: StoryCharacterEntity?, tagsJson: String, gender: String): Int {
        if (character == null) return 0
        val tags = runCatching { JSONArray(tagsJson) }.getOrNull() ?: JSONArray()
        var score = if (gender == character.gender) 10 else 0
        val personality = character.personality.lowercase()
        for (index in 0 until tags.length()) {
            if (tags.optString(index).isNotBlank() && personality.contains(tags.optString(index).lowercase())) score += 3
        }
        return score
    }

    private fun stableTie(characterId: String, voiceId: String): Int = (characterId + voiceId).hashCode()
}
