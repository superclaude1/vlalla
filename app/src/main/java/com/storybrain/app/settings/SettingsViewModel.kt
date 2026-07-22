package com.storybrain.app.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.storybrain.app.StoryBrainApplication
import com.storybrain.app.data.TtsProfileIds
import com.storybrain.app.data.TtsProfileVoicePoolEntity
import com.storybrain.app.data.TtsProviderKind
import com.storybrain.app.data.TtsProviderProfileEntity
import com.storybrain.app.data.TtsVoiceRole
import com.storybrain.app.tts.EdgeTtsClient
import com.storybrain.app.tts.FishAudioClient
import com.storybrain.app.tts.OpenAiTtsClient
import com.storybrain.app.tts.TtsVoice
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class SettingsUiState(
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKeyDraft: String = "",
    val hasStoredKey: Boolean = false,
    val selectedModel: String = "",
    val allowInsecureHttp: Boolean = false,
    val detectedModels: List<String> = emptyList(),
    val detecting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val profiles: List<TtsProviderProfileEntity> = emptyList(),
    val globalProfileId: String = TtsProfileIds.EDGE,
    val selectedProfileId: String = TtsProfileIds.EDGE,
    val profileBaseUrl: String = "",
    val profileModel: String = "edge-online",
    val profileSupportsInstructions: Boolean = false,
    val profileAllowInsecureHttp: Boolean = false,
    val ttsApiKeyDraft: String = "",
    val ttsHasStoredKey: Boolean = false,
    val ttsModels: List<String> = emptyList(),
    val voices: List<TtsVoice> = emptyList(),
    val voicePool: List<TtsProfileVoicePoolEntity> = emptyList(),
    val voiceQuery: String = "",
    val voiceTotal: Int = 0,
    val searchingOwnVoices: Boolean = true,
    val ttsDetecting: Boolean = false,
    val ttsMessage: String? = null,
    val ttsIsError: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as StoryBrainApplication).repository
    private val llmStore = LlmSettingsStore(application)
    private val ttsStore = TtsSettingsStore(application)
    private val llmClient = OpenAiCompatibleClient()
    private val edgeClient = EdgeTtsClient()
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultTtsProfiles()
            val llm = llmStore.config.first()
            val global = ttsStore.config.first()
            val profiles = repository.getTtsProfiles()
            val selected = profiles.firstOrNull { it.id == global.globalProfileId } ?: profiles.first()
            _state.value = _state.value.copy(
                baseUrl = llm.baseUrl,
                selectedModel = llm.model,
                allowInsecureHttp = llm.allowInsecureHttp,
                hasStoredKey = llmStore.hasApiKey(),
                profiles = profiles,
                globalProfileId = global.globalProfileId,
                selectedProfileId = selected.id,
                profileBaseUrl = selected.baseUrl,
                profileModel = selected.model,
                profileSupportsInstructions = selected.supportsInstructions,
                profileAllowInsecureHttp = ttsStore.isInsecureHttpAllowed(selected.id, selected.baseUrl),
                ttsHasStoredKey = ttsStore.hasApiKey(selected.id),
                ttsModels = modelsFor(selected),
                voicePool = repository.getTtsVoicePool(selected.id)
            )
        }
    }

    fun updateBaseUrl(value: String) { _state.value = _state.value.copy(baseUrl = value, message = null) }
    fun updateApiKey(value: String) { _state.value = _state.value.copy(apiKeyDraft = value, message = null) }
    fun selectModel(value: String) { _state.value = _state.value.copy(selectedModel = value, message = null) }
    fun setAllowInsecureHttp(value: Boolean) { _state.value = _state.value.copy(allowInsecureHttp = value, message = null) }
    fun updateProfileBaseUrl(value: String) { _state.value = _state.value.copy(profileBaseUrl = value, ttsMessage = null) }
    fun updateProfileModel(value: String) { _state.value = _state.value.copy(profileModel = value, ttsMessage = null) }
    fun updateTtsApiKey(value: String) { _state.value = _state.value.copy(ttsApiKeyDraft = value, ttsMessage = null) }
    fun updateVoiceQuery(value: String) { _state.value = _state.value.copy(voiceQuery = value) }
    fun setSupportsInstructions(value: Boolean) { _state.value = _state.value.copy(profileSupportsInstructions = value) }
    fun setProfileAllowInsecureHttp(value: Boolean) { _state.value = _state.value.copy(profileAllowInsecureHttp = value, ttsMessage = null) }

    fun selectGlobalProfile(profileId: String) {
        viewModelScope.launch {
            ttsStore.saveGlobalProfile(profileId)
            _state.value = _state.value.copy(globalProfileId = profileId, ttsMessage = "全局主力引擎已更新", ttsIsError = false)
        }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            val profile = repository.getTtsProfile(profileId) ?: return@launch
            _state.value = _state.value.copy(
                selectedProfileId = profileId,
                profileBaseUrl = profile.baseUrl,
                profileModel = profile.model,
                profileSupportsInstructions = profile.supportsInstructions,
                profileAllowInsecureHttp = ttsStore.isInsecureHttpAllowed(profile.id, profile.baseUrl),
                ttsApiKeyDraft = "",
                ttsHasStoredKey = ttsStore.hasApiKey(profileId),
                ttsModels = modelsFor(profile),
                voices = emptyList(),
                voicePool = repository.getTtsVoicePool(profileId),
                ttsMessage = null,
                ttsIsError = false
            )
        }
    }

    fun detectModels() {
        val snapshot = _state.value
        viewModelScope.launch {
            _state.value = snapshot.copy(detecting = true, message = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    llmClient.listModels(
                        snapshot.baseUrl,
                        snapshot.apiKeyDraft.ifBlank { llmStore.readApiKey() },
                        snapshot.allowInsecureHttp
                    )
                        .also { require(it.isNotEmpty()) { "连接成功，但没有返回可用模型" } }
                }
            }.onSuccess { models ->
                _state.value = _state.value.copy(
                    detecting = false,
                    detectedModels = models,
                    selectedModel = _state.value.selectedModel.takeIf(models::contains) ?: models.first(),
                    message = "连接成功，检测到 ${models.size} 个模型",
                    isError = false
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(detecting = false, message = error.message ?: "模型检测失败", isError = true)
            }
        }
    }

    fun save() {
        val snapshot = _state.value
        viewModelScope.launch {
            runCatching {
                llmStore.save(
                    snapshot.baseUrl,
                    snapshot.selectedModel,
                    snapshot.apiKeyDraft.takeIf(String::isNotBlank),
                    snapshot.allowInsecureHttp
                )
            }
                .onSuccess { _state.value = _state.value.copy(apiKeyDraft = "", hasStoredKey = snapshot.apiKeyDraft.isNotBlank() || snapshot.hasStoredKey, message = "LLM 设置已安全保存", isError = false) }
                .onFailure { _state.value = _state.value.copy(message = it.message ?: "保存失败", isError = true) }
        }
    }

    fun detectTtsService() {
        val snapshot = _state.value
        val profile = snapshot.profiles.firstOrNull { it.id == snapshot.selectedProfileId } ?: return
        viewModelScope.launch {
            _state.value = snapshot.copy(ttsDetecting = true, ttsMessage = null, ttsIsError = false)
            runCatching {
                withContext(Dispatchers.IO) {
                    when (TtsProviderKind.valueOf(profile.kind)) {
                        TtsProviderKind.EDGE -> {
                            val output = File(getApplication<Application>().cacheDir, "edge-test.mp3")
                            try { edgeClient.synthesize("章境配音连接测试", "zh-CN-XiaoxiaoNeural", output) }
                            finally { output.delete() }
                            emptyList<String>() to "Edge TTS 直连成功"
                        }
                        TtsProviderKind.FISH_AUDIO -> {
                            val key = snapshot.ttsApiKeyDraft.ifBlank { ttsStore.readApiKey(profile.id) }
                            require(key.isNotBlank()) { "请输入 Fish Audio API Key" }
                            val client = FishAudioClient(
                                snapshot.profileBaseUrl,
                                allowInsecureHttp = snapshot.profileAllowInsecureHttp
                            )
                            val count = client.test(key)
                            client.listModels() to "Fish Audio 连接成功，账号音色 $count 个"
                        }
                        TtsProviderKind.OPENAI_COMPATIBLE -> {
                            val key = snapshot.ttsApiKeyDraft.ifBlank { ttsStore.readApiKey(profile.id) }
                            val models = OpenAiTtsClient(
                                snapshot.profileBaseUrl,
                                allowInsecureHttp = snapshot.profileAllowInsecureHttp
                            ).listModels(key)
                            models to "兼容 TTS 连接成功，检测到 ${models.size} 个模型"
                        }
                    }
                }
            }.onSuccess { (models, message) ->
                _state.value = _state.value.copy(
                    ttsDetecting = false,
                    ttsModels = models,
                    profileModel = _state.value.profileModel.takeIf { it in models || models.isEmpty() } ?: models.first(),
                    ttsMessage = message,
                    ttsIsError = false
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(ttsDetecting = false, ttsMessage = error.message ?: "配音服务检测失败", ttsIsError = true)
            }
        }
    }

    fun searchFishVoices(own: Boolean) {
        val snapshot = _state.value
        val profile = snapshot.profiles.firstOrNull { it.id == snapshot.selectedProfileId } ?: return
        if (profile.kind != TtsProviderKind.FISH_AUDIO.name) return
        viewModelScope.launch {
            _state.value = snapshot.copy(ttsDetecting = true, searchingOwnVoices = own, ttsMessage = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    val key = snapshot.ttsApiKeyDraft.ifBlank { ttsStore.readApiKey(profile.id) }
                    require(key.isNotBlank()) { "请输入 Fish Audio API Key" }
                    FishAudioClient(
                        snapshot.profileBaseUrl,
                        allowInsecureHttp = snapshot.profileAllowInsecureHttp
                    ).listVoices(key, self = own, query = snapshot.voiceQuery)
                }
            }.onSuccess { page ->
                _state.value = _state.value.copy(ttsDetecting = false, voices = page.voices, voiceTotal = page.total, ttsMessage = "找到 ${page.total} 个音色", ttsIsError = false)
            }.onFailure { error ->
                _state.value = _state.value.copy(ttsDetecting = false, ttsMessage = error.message ?: "音色查询失败", ttsIsError = true)
            }
        }
    }

    fun addVoiceToPool(voice: TtsVoice, role: TtsVoiceRole) {
        val profileId = _state.value.selectedProfileId
        viewModelScope.launch {
            repository.addVoicePoolItem(
                TtsProfileVoicePoolEntity(
                    profileId = profileId,
                    voiceId = voice.id,
                    role = role.name,
                    voiceName = voice.name,
                    gender = voice.gender,
                    ageGroup = voice.ageGroup,
                    language = voice.language,
                    tagsJson = JSONArray(voice.tags).toString(),
                    source = voice.source,
                    favorite = true
                )
            )
            _state.value = _state.value.copy(voicePool = repository.getTtsVoicePool(profileId), ttsMessage = "已将 ${voice.name} 加入${roleLabel(role)}音色池", ttsIsError = false)
        }
    }

    fun addManualVoice(voiceId: String, name: String, role: TtsVoiceRole) {
        if (voiceId.isBlank()) return
        addVoiceToPool(TtsVoice(voiceId.trim(), name.trim().ifBlank { voiceId.trim() }, source = "MANUAL"), role)
    }

    fun saveTts() {
        val snapshot = _state.value
        val profile = snapshot.profiles.firstOrNull { it.id == snapshot.selectedProfileId } ?: return
        viewModelScope.launch {
            runCatching {
                val normalizedBaseUrl = if (profile.kind == TtsProviderKind.EDGE.name) "" else {
                    EndpointPolicy.requireAllowed(snapshot.profileBaseUrl, snapshot.profileAllowInsecureHttp)
                }
                repository.saveTtsProfile(
                    profile.copy(
                        baseUrl = normalizedBaseUrl,
                        model = snapshot.profileModel,
                        supportsInstructions = snapshot.profileSupportsInstructions
                    )
                )
                ttsStore.saveInsecureHttpAllowed(profile.id, snapshot.profileAllowInsecureHttp)
                snapshot.ttsApiKeyDraft.takeIf(String::isNotBlank)?.let { ttsStore.writeApiKey(profile.id, it) }
            }.onSuccess {
                val profiles = repository.getTtsProfiles()
                _state.value = _state.value.copy(
                    profiles = profiles,
                    ttsApiKeyDraft = "",
                    ttsHasStoredKey = snapshot.ttsApiKeyDraft.isNotBlank() || snapshot.ttsHasStoredKey,
                    ttsMessage = "配音提供商设置已保存",
                    ttsIsError = false
                )
            }.onFailure { _state.value = _state.value.copy(ttsMessage = it.message ?: "保存失败", ttsIsError = true) }
        }
    }

    fun clearApiKey() { llmStore.clearApiKey(); _state.value = _state.value.copy(apiKeyDraft = "", hasStoredKey = false, message = "已清除 LLM API Key") }
    fun clearTtsApiKey() {
        ttsStore.clearApiKey(_state.value.selectedProfileId)
        _state.value = _state.value.copy(ttsApiKeyDraft = "", ttsHasStoredKey = false, ttsMessage = "已清除当前提供商 API Key")
    }

    private fun modelsFor(profile: TtsProviderProfileEntity) = when (TtsProviderKind.valueOf(profile.kind)) {
        TtsProviderKind.FISH_AUDIO -> listOf("s2.1-pro-free", "s2.1-pro", "s2-pro", "s1")
        else -> listOf(profile.model).filter(String::isNotBlank)
    }

    private fun roleLabel(role: TtsVoiceRole) = when (role) {
        TtsVoiceRole.NARRATOR -> "旁白"
        TtsVoiceRole.MALE -> "男性"
        TtsVoiceRole.FEMALE -> "女性"
        TtsVoiceRole.UNKNOWN -> "通用"
    }
}
