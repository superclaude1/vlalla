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
            // 音色池为空时不再报错：按性别落到引擎内置默认音色（至少男女各一）。
            return builtInFallback(profile, targetRole, character, speaker)
        }
        return ResolvedTtsVoice(profile, selected.voiceId, selected.voiceName, false)
    }

    /**
     * 音色池兜底：本引擎无可用音色时，先试 Edge 内置默认音色（晓晓/云希），
     * 最后落到 Android 系统 TTS（离线可用），保证男女角色始终有声音。
     */
    private suspend fun builtInFallback(
        profile: TtsProviderProfileEntity,
        targetRole: TtsVoiceRole,
        character: StoryCharacterEntity?,
        speaker: String?
    ): ResolvedTtsVoice {
        val kind = TtsProviderKind.valueOf(profile.kind)
        when (kind) {
            TtsProviderKind.EDGE -> {
                val voice = if (targetRole == TtsVoiceRole.FEMALE || character?.gender == "FEMALE")
                    EDGE_FEMALE else EDGE_MALE
                return ResolvedTtsVoice(profile, voice.first, voice.second, false)
            }

            TtsProviderKind.ANDROID_SYSTEM -> return ResolvedTtsVoice(
                profile, ANDROID_SYSTEM_VOICE_ID, "系统默认", false
            )

            TtsProviderKind.FISH_AUDIO, TtsProviderKind.OPENAI_COMPATIBLE -> {
                // 第三方引擎无通用默认音色：降级到 Edge 内置默认（edge-default 总是预置）。
                repository.getTtsProfile(TtsProfileIds.EDGE)?.let { edge ->
                    val voice = if (targetRole == TtsVoiceRole.FEMALE || character?.gender == "FEMALE")
                        EDGE_FEMALE else EDGE_MALE
                    return ResolvedTtsVoice(edge, voice.first, voice.second, false)
                }
                val label = when (kind) {
                    TtsProviderKind.FISH_AUDIO -> "Fish Audio"
                    TtsProviderKind.OPENAI_COMPATIBLE -> "兼容 TTS"
                    else -> "配音"
                }
                error("$label 尚未配置${if (character == null) "旁白" else "角色"}音色，请先在设置中添加音色或为角色绑定音色")
            }
        }
    }

    companion object {
        /** Edge TTS 内置默认音色（男女）。 */
        private val EDGE_FEMALE = "zh-CN-XiaoxiaoNeural" to "晓晓"
        private val EDGE_MALE = "zh-CN-YunxiNeural" to "云希"
        const val ANDROID_SYSTEM_VOICE_ID = "system-default"
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
