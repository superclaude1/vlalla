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
import com.storybrain.app.data.LlmApiProfileEntity
import com.storybrain.app.tts.EdgeTtsClient
import com.storybrain.app.tts.FishAudioClient
import com.storybrain.app.tts.OpenAiTtsClient
import com.storybrain.app.tts.TtsVoice
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class SettingsUiState(
    val llmProfiles: List<LlmApiProfileEntity> = emptyList(),
    val llmModelGroups: List<LlmModelGroup> = emptyList(),
    val selectedApiProfileId: String = DEFAULT_LLM_PROFILE_ID,
    val apiDisplayName: String = "默认 API",
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKeyDraft: String = "",
    val hasStoredKey: Boolean = false,
    val selectedModel: String = "",
    val detectedModels: List<String> = emptyList(),
    val detecting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val profiles: List<TtsProviderProfileEntity> = emptyList(),
    val globalProfileId: String = TtsProfileIds.EDGE,
    val selectedProfileId: String = TtsProfileIds.EDGE,
    val loadedProfileId: String? = null,
    val profileLoading: Boolean = false,
    val profileBaseUrl: String = "",
    val profileModel: String = "edge-online",
    val profileSupportsInstructions: Boolean = false,
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

data class TtsRequestIdentity(
    val profileId: String,
    val baseUrl: String,
    val apiKeyDraft: String,
    val model: String,
    val voiceQuery: String,
    val requestId: Long
)

data class LlmDraftIdentity(
    val profileId: String,
    val displayName: String,
    val baseUrl: String,
    val apiKeyDraft: String,
    val selectedModel: String,
    val detectedModels: List<String>,
    val requestId: Long
)

fun llmDraftIdentity(state: SettingsUiState, requestId: Long) = LlmDraftIdentity(
    state.selectedApiProfileId, state.apiDisplayName, state.baseUrl, state.apiKeyDraft,
    state.selectedModel, state.detectedModels.toList(), requestId
)

fun authorizesLlmDraft(identity: LlmDraftIdentity, state: SettingsUiState, currentRequestId: Long): Boolean =
    identity.requestId == currentRequestId && identity == llmDraftIdentity(state, currentRequestId)

fun selectLlmModelDraft(state: SettingsUiState, identity: LlmModelIdentity): SettingsUiState {
    val profile = state.llmProfiles.firstOrNull { it.id == identity.apiProfileId } ?: return state
    val models = state.llmModelGroups.firstOrNull { it.profile.id == profile.id }
        ?.models.orEmpty().map { it.identity.modelId }
    return state.copy(
        selectedApiProfileId = profile.id,
        apiDisplayName = profile.displayName,
        baseUrl = profile.baseUrl,
        selectedModel = identity.modelId,
        detectedModels = models,
        apiKeyDraft = "",
        message = null
    )
}

fun selectionAfterProfileDeletion(selectedProfileId: String, deletedProfileId: String): String =
    selectedProfileId.takeUnless { it == deletedProfileId }.orEmpty()

fun normalizeTtsEndpointDraft(kind: String, baseUrl: String): String = when (TtsProviderKind.valueOf(kind)) {
    TtsProviderKind.FISH_AUDIO, TtsProviderKind.OPENAI_COMPATIBLE -> ApiEndpointPolicy.normalize(baseUrl)
    TtsProviderKind.EDGE, TtsProviderKind.ANDROID_SYSTEM -> baseUrl.trim()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as StoryBrainApplication).repository
    private val llmStore = LlmSettingsStore(application, repository)
    private val ttsStore = TtsSettingsStore(application)
    private val llmClient = OpenAiCompatibleClient()
    private val edgeClient = EdgeTtsClient()
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()
    private var llmRequestSequence = 0L
    private var ttsRequestSequence = 0L
    private var llmSaveSequence = 0L
    private val llmPersistenceMutex = Mutex()
    private var ttsSaveSequence = 0L

    init {
        viewModelScope.launch {
            repository.ensureDefaultTtsProfiles()
            llmStore.ensureLegacyMigration()
            val llm = llmStore.config.first()
            val llmProfiles = repository.getLlmApiProfiles()
            val llmModels = repository.getLlmModels()
            val llmProfile = llmProfiles.firstOrNull { it.id == llm.apiProfileId }
            val global = ttsStore.config.first()
            val profiles = repository.getTtsProfiles()
            val selected = profiles.firstOrNull { it.id == global.globalProfileId } ?: profiles.first()
            _state.value = _state.value.copy(
                baseUrl = llmProfile?.baseUrl.orEmpty(),
                selectedModel = llmProfile?.selectedModel.orEmpty(),
                llmProfiles = llmProfiles,
                llmModelGroups = groupLlmModels(llmProfiles, llmModels),
                selectedApiProfileId = llmProfile?.id.orEmpty(),
                apiDisplayName = llmProfile?.displayName.orEmpty(),
                hasStoredKey = llmProfile?.let { llmStore.hasApiKey(it.id) } == true,
                detectedModels = llmModels.filter { it.apiProfileId == llmProfile?.id }.map { it.modelId },
                profiles = profiles,
                globalProfileId = global.globalProfileId,
                selectedProfileId = selected.id,
                loadedProfileId = selected.id,
                profileLoading = false,
                profileBaseUrl = selected.baseUrl,
                profileModel = selected.model,
                profileSupportsInstructions = selected.supportsInstructions,
                ttsHasStoredKey = ttsStore.hasApiKey(selected.id),
                ttsModels = modelsFor(selected),
                voicePool = repository.getTtsVoicePool(selected.id)
            )
        }
    }

    fun updateBaseUrl(value: String) { invalidateLlmDraft(); _state.value = _state.value.copy(baseUrl = value, detecting = false, message = null) }
    fun updateApiDisplayName(value: String) { invalidateLlmDraft(); _state.value = _state.value.copy(apiDisplayName = value, message = null) }
    fun updateApiKey(value: String) { invalidateLlmDraft(); _state.value = _state.value.copy(apiKeyDraft = value, detecting = false, message = null) }
    fun selectModel(identity: LlmModelIdentity) {
        invalidateLlmDraft()
        val selected = selectLlmModelDraft(_state.value, identity)
        _state.value = selected.copy(hasStoredKey = llmStore.hasApiKey(selected.selectedApiProfileId))
    }

    fun selectLlmProfile(profileId: String) {
        val profile = _state.value.llmProfiles.firstOrNull { it.id == profileId } ?: return
        invalidateLlmDraft()
        _state.value = _state.value.copy(
            selectedApiProfileId = profile.id,
            apiDisplayName = profile.displayName,
            baseUrl = profile.baseUrl,
            selectedModel = profile.selectedModel.takeIf(String::isNotBlank)
                ?: _state.value.llmModelGroups.firstOrNull { it.profile.id == profile.id }
                    ?.models?.firstOrNull()?.identity?.modelId.orEmpty(),
            apiKeyDraft = "",
            hasStoredKey = llmStore.hasApiKey(profile.id),
            detectedModels = _state.value.llmModelGroups.firstOrNull { it.profile.id == profile.id }
                ?.models.orEmpty().map { it.identity.modelId },
            message = null
        )
    }

    fun addLlmProfile() {
        val snapshot = _state.value
        val requestId = ++llmSaveSequence
        val identity = llmDraftIdentity(snapshot, requestId)
        viewModelScope.launch {
            runCatching {
                require(snapshot.apiDisplayName.isNotBlank()) { "请输入 API 名称" }
                val normalizedBaseUrl = ApiEndpointPolicy.normalize(snapshot.baseUrl)
                val profile = LlmApiProfileEntity(
                    id = UUID.randomUUID().toString(),
                    displayName = snapshot.apiDisplayName,
                    baseUrl = normalizedBaseUrl,
                    selectedModel = snapshot.selectedModel.takeIf { it in snapshot.detectedModels }.orEmpty()
                )
                llmPersistenceMutex.withLock {
                    repository.saveLlmProfileDraft(profile, snapshot.detectedModels) {
                        requireCurrentLlmDraft(identity, llmSaveSequence)
                    }
                    requireCurrentLlmDraft(identity, llmSaveSequence)
                    snapshot.apiKeyDraft.takeIf(String::isNotBlank)?.let { llmStore.writeApiKey(profile.id, it) }
                    requireCurrentLlmDraft(identity, llmSaveSequence)
                    llmStore.saveSelection(LlmModelIdentity(profile.id, profile.selectedModel))
                }
                profile.id
            }.onSuccess { profileId ->
                if (!authorizesLlmDraft(identity, _state.value, llmSaveSequence)) return@onSuccess
                refreshLlmState(profileId, "已新增 LLM API", identity)
            }.onFailure {
                if (!authorizesLlmDraft(identity, _state.value, llmSaveSequence)) return@onFailure
                _state.value = _state.value.copy(message = it.message ?: "新增失败", isError = true)
            }
        }
    }
    fun updateProfileBaseUrl(value: String) { if (profileReady()) { ttsRequestSequence++; _state.value = _state.value.copy(profileBaseUrl = value, ttsDetecting = false, ttsMessage = null) } }
    fun updateProfileModel(value: String) { if (profileReady()) { ttsRequestSequence++; _state.value = _state.value.copy(profileModel = value, ttsDetecting = false, ttsMessage = null) } }
    fun updateTtsApiKey(value: String) { if (profileReady()) { ttsRequestSequence++; _state.value = _state.value.copy(ttsApiKeyDraft = value, ttsDetecting = false, ttsMessage = null) } }
    fun updateVoiceQuery(value: String) { if (profileReady()) { ttsRequestSequence++; _state.value = _state.value.copy(voiceQuery = value, ttsDetecting = false) } }
    fun setSupportsInstructions(value: Boolean) { if (profileReady()) _state.value = _state.value.copy(profileSupportsInstructions = value) }

    fun selectGlobalProfile(profileId: String) {
        viewModelScope.launch {
            ttsStore.saveGlobalProfile(profileId)
            _state.value = _state.value.copy(globalProfileId = profileId, ttsMessage = "全局主力引擎已更新", ttsIsError = false)
        }
    }

    fun selectProfile(profileId: String) {
        ttsRequestSequence++
        if (_state.value.loadedProfileId == profileId && !_state.value.profileLoading) return
        val request = _state.value.copy(
            selectedProfileId = profileId,
            loadedProfileId = null,
            profileLoading = true,
            ttsApiKeyDraft = "",
            voices = emptyList(),
            voicePool = emptyList(),
            ttsMessage = null,
            ttsIsError = false
        )
        _state.value = request
        viewModelScope.launch {
            val profile = repository.getTtsProfile(profileId)
            if (profile == null) {
                if (_state.value.selectedProfileId == profileId) {
                    _state.value = _state.value.copy(profileLoading = false, ttsMessage = "找不到目标配音服务", ttsIsError = true)
                }
                return@launch
            }
            if (_state.value.selectedProfileId != profileId) return@launch
            _state.value = _state.value.copy(
                selectedProfileId = profileId,
                loadedProfileId = profileId,
                profileLoading = false,
                profileBaseUrl = profile.baseUrl,
                profileModel = profile.model,
                profileSupportsInstructions = profile.supportsInstructions,
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
        llmSaveSequence++
        val requestId = ++llmRequestSequence
        val identity = llmDraftIdentity(snapshot, requestId)
        viewModelScope.launch {
            _state.value = snapshot.copy(detecting = true, message = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    llmClient.listModels(snapshot.baseUrl, snapshot.apiKeyDraft.ifBlank { llmStore.readApiKey(snapshot.selectedApiProfileId) })
                        .also { require(it.isNotEmpty()) { "连接成功，但没有返回可用模型" } }
                }
            }.onSuccess { models ->
                if (!authorizesLlmDetect(identity, _state.value, llmRequestSequence)) return@onSuccess
                llmPersistenceMutex.withLock {
                    if (!authorizesLlmDetect(identity, _state.value, llmRequestSequence)) return@withLock
                    repository.replaceLlmModels(snapshot.selectedApiProfileId, models)
                }
                if (!authorizesLlmDetect(identity, _state.value, llmRequestSequence)) return@onSuccess
                val profiles = repository.getLlmApiProfiles()
                val allModels = repository.getLlmModels()
                if (!authorizesLlmDetect(identity, _state.value, llmRequestSequence)) return@onSuccess
                _state.value = _state.value.copy(
                    detecting = false,
                    detectedModels = models,
                    llmModelGroups = groupLlmModels(profiles, allModels),
                    selectedModel = _state.value.selectedModel.takeIf(models::contains) ?: models.first(),
                    message = "连接成功，检测到 ${models.size} 个模型",
                    isError = false
                )
            }.onFailure { error ->
                if (!authorizesLlmDetect(identity, _state.value, llmRequestSequence)) return@onFailure
                _state.value = _state.value.copy(detecting = false, message = error.message ?: "模型检测失败", isError = true)
            }
        }
    }

    fun save() {
        val snapshot = _state.value
        val requestId = ++llmSaveSequence
        val identity = llmDraftIdentity(snapshot, requestId)
        viewModelScope.launch {
            runCatching {
                val current = snapshot.llmProfiles.first { it.id == snapshot.selectedApiProfileId }
                val modelsForProfile = snapshot.llmModelGroups.firstOrNull { it.profile.id == snapshot.selectedApiProfileId }
                    ?.models.orEmpty().map { it.identity.modelId }
                val models = snapshot.detectedModels.takeIf { it == modelsForProfile }
                    ?: modelsForProfile
                val selectedModels = (models + snapshot.selectedModel).filter(String::isNotBlank).distinct()
                llmPersistenceMutex.withLock {
                    val normalizedBaseUrl = ApiEndpointPolicy.normalize(snapshot.baseUrl)
                    val updatedProfile = current.copy(
                        displayName = snapshot.apiDisplayName,
                        baseUrl = normalizedBaseUrl,
                        selectedModel = snapshot.selectedModel
                    )
                    repository.saveLlmProfileDraft(updatedProfile, selectedModels) {
                        requireCurrentLlmDraft(identity, llmSaveSequence)
                    }
                    requireCurrentLlmDraft(identity, llmSaveSequence)
                    llmStore.saveSelection(LlmModelIdentity(snapshot.selectedApiProfileId, snapshot.selectedModel))
                    requireCurrentLlmDraft(identity, llmSaveSequence)
                    snapshot.apiKeyDraft.takeIf(String::isNotBlank)?.let { llmStore.writeApiKey(snapshot.selectedApiProfileId, it) }
                }
            }
                .onSuccess {
                    if (!authorizesLlmDraft(identity, _state.value, llmSaveSequence)) return@onSuccess
                    refreshLlmState(snapshot.selectedApiProfileId, "LLM 设置已安全保存", identity)
                }
                .onFailure {
                    if (!authorizesLlmDraft(identity, _state.value, llmSaveSequence)) return@onFailure
                    _state.value = _state.value.copy(message = it.message ?: "保存失败", isError = true)
                }
        }
    }

    fun detectTtsService() {
        val snapshot = _state.value
        if (!profileReady(snapshot)) return
        val profile = snapshot.profiles.firstOrNull { it.id == snapshot.selectedProfileId } ?: return
        val identity = ttsIdentity(snapshot, ++ttsRequestSequence)
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
                            val count = FishAudioClient(snapshot.profileBaseUrl).test(key)
                            FishAudioClient(snapshot.profileBaseUrl).listModels() to "Fish Audio 连接成功，账号音色 $count 个"
                        }
                        TtsProviderKind.OPENAI_COMPATIBLE -> {
                            val key = snapshot.ttsApiKeyDraft.ifBlank { ttsStore.readApiKey(profile.id) }
                            val models = OpenAiTtsClient(snapshot.profileBaseUrl).listModels(key)
                            models to "兼容 TTS 连接成功"
                        }
                        TtsProviderKind.ANDROID_SYSTEM -> {
                            val tts = android.speech.tts.TextToSpeech(getApplication<Application>()) {}
                            try { require(tts.voices.orEmpty().any { com.storybrain.app.tts.AndroidSystemTtsVoiceSupport.isChinese(it.locale) }) { "系统未安装中文 TTS 音色" } }
                            finally { tts.shutdown() }
                            emptyList<String>() to "Android 系统 TTS 可用"
                        }
                    }
                }
            }.onSuccess { (models, message) ->
                if (!sameTtsRequest(identity)) return@onSuccess
                _state.value = _state.value.copy(
                    ttsDetecting = false,
                    ttsModels = models,
                    profileModel = _state.value.profileModel.takeIf { it in models || models.isEmpty() } ?: models.first(),
                    ttsMessage = message,
                    ttsIsError = false
                )
            }.onFailure { error ->
                if (!sameTtsRequest(identity)) return@onFailure
                _state.value = _state.value.copy(ttsDetecting = false, ttsMessage = error.message ?: "配音服务检测失败", ttsIsError = true)
            }
        }
    }

    fun searchFishVoices(own: Boolean) {
        val snapshot = _state.value
        if (!profileReady(snapshot)) return
        val profile = snapshot.profiles.firstOrNull { it.id == snapshot.selectedProfileId } ?: return
        if (profile.kind != TtsProviderKind.FISH_AUDIO.name) return
        val identity = ttsIdentity(snapshot, ++ttsRequestSequence)
        viewModelScope.launch {
            _state.value = snapshot.copy(ttsDetecting = true, searchingOwnVoices = own, ttsMessage = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    val key = snapshot.ttsApiKeyDraft.ifBlank { ttsStore.readApiKey(profile.id) }
                    require(key.isNotBlank()) { "请输入 Fish Audio API Key" }
                    FishAudioClient(snapshot.profileBaseUrl).listVoices(key, self = own, query = snapshot.voiceQuery)
                }
            }.onSuccess { page ->
                if (!sameTtsRequest(identity)) return@onSuccess
                _state.value = _state.value.copy(ttsDetecting = false, voices = page.voices, voiceTotal = page.total, ttsMessage = "找到 ${page.total} 个音色", ttsIsError = false)
            }.onFailure { error ->
                if (!sameTtsRequest(identity)) return@onFailure
                _state.value = _state.value.copy(ttsDetecting = false, ttsMessage = error.message ?: "音色查询失败", ttsIsError = true)
            }
        }
    }

    fun addVoiceToPool(voice: TtsVoice, role: TtsVoiceRole) {
        if (!profileReady()) return
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
            if (!sameProfileRequest(profileId)) return@launch
            _state.value = _state.value.copy(voicePool = repository.getTtsVoicePool(profileId), ttsMessage = "已将 ${voice.name} 加入${roleLabel(role)}音色池", ttsIsError = false)
        }
    }

    fun addManualVoice(voiceId: String, name: String, role: TtsVoiceRole) {
        if (voiceId.isBlank()) return
        addVoiceToPool(TtsVoice(voiceId.trim(), name.trim().ifBlank { voiceId.trim() }, source = "MANUAL"), role)
    }

    fun saveTts() {
        val snapshot = _state.value
        if (!profileReady(snapshot)) return
        val profile = snapshot.profiles.firstOrNull { it.id == snapshot.selectedProfileId } ?: return
        val requestId = ++ttsSaveSequence
        viewModelScope.launch {
            runCatching {
                val normalizedBaseUrl = normalizeTtsEndpointDraft(profile.kind, snapshot.profileBaseUrl)
                repository.saveTtsProfile(
                    profile.copy(
                        baseUrl = normalizedBaseUrl,
                        model = snapshot.profileModel,
                        supportsInstructions = snapshot.profileSupportsInstructions
                    )
                )
                if (requestId == ttsSaveSequence && sameTtsDraft(snapshot)) {
                    snapshot.ttsApiKeyDraft.takeIf(String::isNotBlank)?.let { ttsStore.writeApiKey(profile.id, it) }
                }
            }.onSuccess {
                val profiles = repository.getTtsProfiles()
                if (requestId != ttsSaveSequence || !sameTtsDraft(snapshot)) return@onSuccess
                _state.value = _state.value.copy(
                    profiles = profiles,
                    ttsApiKeyDraft = "",
                    ttsHasStoredKey = snapshot.ttsApiKeyDraft.isNotBlank() || snapshot.ttsHasStoredKey,
                    ttsMessage = "配音提供商设置已保存",
                    ttsIsError = false
                )
            }.onFailure {
                if (requestId != ttsSaveSequence || !sameTtsDraft(snapshot)) return@onFailure
                _state.value = _state.value.copy(ttsMessage = it.message ?: "保存失败", ttsIsError = true)
            }
        }
    }

    fun clearApiKey() { llmSaveSequence++; llmStore.clearApiKey(_state.value.selectedApiProfileId); _state.value = _state.value.copy(apiKeyDraft = "", hasStoredKey = false, message = "已清除当前 LLM API Key") }

    fun deleteLlmProfile(profileId: String) {
        val selectedAtRequest = _state.value.selectedApiProfileId
        llmRequestSequence++
        val deleteRequestId = ++llmSaveSequence
        viewModelScope.launch {
            runCatching {
                llmPersistenceMutex.withLock {
                    repository.deleteLlmProfile(profileId)
                    llmStore.clearApiKey(profileId)
                    if (selectedAtRequest == profileId) llmStore.clearSelection()
                }
            }.onSuccess {
                val profiles = repository.getLlmApiProfiles()
                val models = repository.getLlmModels()
                if (deleteRequestId != llmSaveSequence || _state.value.selectedApiProfileId != selectedAtRequest) return@onSuccess
                val selected = selectionAfterProfileDeletion(selectedAtRequest, profileId)
                val selectedProfile = profiles.firstOrNull { it.id == selected }
                _state.value = _state.value.copy(
                    llmProfiles = profiles,
                    llmModelGroups = groupLlmModels(profiles, models),
                    selectedApiProfileId = selected,
                    apiDisplayName = selectedProfile?.displayName.orEmpty(),
                    baseUrl = selectedProfile?.baseUrl.orEmpty(),
                    selectedModel = selectedProfile?.selectedModel.orEmpty(),
                    detectedModels = models.filter { it.apiProfileId == selected }.map { it.modelId },
                    apiKeyDraft = "",
                    hasStoredKey = selected.isNotBlank() && llmStore.hasApiKey(selected),
                    message = if (selected.isBlank()) "已删除当前 API，请选择其他 API" else "已删除 API",
                    isError = false
                )
            }.onFailure {
                _state.value = _state.value.copy(message = it.message ?: "删除失败", isError = true)
            }
        }
    }

    fun clearTtsApiKey() {
        ttsSaveSequence++
        ttsStore.clearApiKey(_state.value.selectedProfileId)
        _state.value = _state.value.copy(ttsApiKeyDraft = "", ttsHasStoredKey = false, ttsMessage = "已清除当前提供商 API Key")
    }

    private fun profileReady(state: SettingsUiState = _state.value) =
        !state.profileLoading && state.loadedProfileId == state.selectedProfileId

    private fun sameProfileRequest(profileId: String): Boolean =
        _state.value.selectedProfileId == profileId && _state.value.loadedProfileId == profileId

    private fun ttsIdentity(state: SettingsUiState, requestId: Long) = TtsRequestIdentity(
        state.selectedProfileId, state.profileBaseUrl, state.ttsApiKeyDraft,
        state.profileModel, state.voiceQuery, requestId
    )

    private fun sameTtsRequest(identity: TtsRequestIdentity): Boolean {
        val current = _state.value
        return identity.requestId == ttsRequestSequence && sameProfileRequest(identity.profileId) &&
            current.profileBaseUrl == identity.baseUrl && current.ttsApiKeyDraft == identity.apiKeyDraft &&
            current.profileModel == identity.model && current.voiceQuery == identity.voiceQuery
    }

    private fun sameTtsDraft(snapshot: SettingsUiState): Boolean {
        val current = _state.value
        return sameProfileRequest(snapshot.selectedProfileId) && current.profileBaseUrl == snapshot.profileBaseUrl &&
            current.profileModel == snapshot.profileModel && current.profileSupportsInstructions == snapshot.profileSupportsInstructions &&
            current.ttsApiKeyDraft == snapshot.ttsApiKeyDraft
    }

    private fun invalidateLlmDraft() {
        llmRequestSequence++
        llmSaveSequence++
    }

    private fun requireCurrentLlmDraft(identity: LlmDraftIdentity, currentRequestId: Long) {
        check(authorizesLlmDraft(identity, _state.value, currentRequestId)) { "LLM 草稿已变更" }
    }

    private fun authorizesLlmDetect(identity: LlmDraftIdentity, state: SettingsUiState, currentRequestId: Long): Boolean {
        val currentDraft = state.copy(detecting = false, message = null)
        return authorizesLlmDraft(identity, currentDraft, currentRequestId)
    }

    private suspend fun refreshLlmState(profileId: String, message: String, identity: LlmDraftIdentity) {
        val profiles = repository.getLlmApiProfiles()
        val models = repository.getLlmModels()
        if (!authorizesLlmDraft(identity, _state.value, llmSaveSequence)) return
        val profile = profiles.first { it.id == profileId }
        _state.value = _state.value.copy(
            llmProfiles = profiles,
            llmModelGroups = groupLlmModels(profiles, models),
            selectedApiProfileId = profileId,
            apiDisplayName = profile.displayName,
            baseUrl = profile.baseUrl,
            selectedModel = profile.selectedModel,
            detectedModels = models.filter { it.apiProfileId == profileId }.map { it.modelId },
            apiKeyDraft = "",
            hasStoredKey = llmStore.hasApiKey(profileId),
            message = message,
            isError = false
        )
    }

    private fun modelsFor(profile: TtsProviderProfileEntity) = when (TtsProviderKind.valueOf(profile.kind)) {
        TtsProviderKind.FISH_AUDIO -> FishAudioClient(profile.baseUrl).listModels()
        else -> listOf(profile.model).filter(String::isNotBlank)
    }

    private fun roleLabel(role: TtsVoiceRole) = when (role) {
        TtsVoiceRole.NARRATOR -> "旁白"
        TtsVoiceRole.MALE -> "男性"
        TtsVoiceRole.FEMALE -> "女性"
        TtsVoiceRole.UNKNOWN -> "通用"
    }
}
