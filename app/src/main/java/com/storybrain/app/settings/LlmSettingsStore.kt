package com.storybrain.app.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.storybrain.app.data.LlmApiProfileEntity
import com.storybrain.app.data.LlmModelEntity
import com.storybrain.app.data.StoryRepository
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.llmDataStore by preferencesDataStore(name = "llm_settings")

const val DEFAULT_LLM_PROFILE_ID = "llm-default"

data class LlmModelIdentity(val apiProfileId: String, val modelId: String)

data class LlmConfig(
    val apiProfileId: String = DEFAULT_LLM_PROFILE_ID,
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = ""
) {
    val identity: LlmModelIdentity get() = LlmModelIdentity(apiProfileId, model)
}

data class LlmProfileSnapshot(
    val apiProfileId: String,
    val baseUrl: String,
    val apiKey: String,
    val modelId: String
)

data class LlmModelOption(val identity: LlmModelIdentity)
data class LlmModelGroup(val profile: LlmApiProfileEntity, val models: List<LlmModelOption>)

fun groupLlmModels(
    profiles: List<LlmApiProfileEntity>,
    models: List<LlmModelEntity>
): List<LlmModelGroup> = profiles.sortedWith(compareBy(LlmApiProfileEntity::createdAt, LlmApiProfileEntity::id)).map { profile ->
    LlmModelGroup(
        profile,
        models.filter { it.apiProfileId == profile.id }
            .sortedBy { it.modelId.lowercase() }
            .map { LlmModelOption(LlmModelIdentity(it.apiProfileId, it.modelId)) }
    )
}

class LlmSettingsStore(
    private val context: Context,
    private val repository: StoryRepository
) {
    private val secureKeyStore = SecureApiKeyStore(context)
    private val migrationMutex = Mutex()

    val config: Flow<LlmConfig> = context.llmDataStore.data.map { preferences ->
        LlmConfig(
            apiProfileId = preferences[SELECTED_PROFILE] ?: DEFAULT_LLM_PROFILE_ID,
            baseUrl = preferences[BASE_URL] ?: "https://api.openai.com/v1",
            model = preferences[SELECTED_MODEL] ?: preferences[MODEL] ?: ""
        )
    }

    suspend fun ensureLegacyMigration() = migrationMutex.withLock {
        val preferences = context.llmDataStore.data.first()
        val selectedProfileId = preferences[SELECTED_PROFILE]
        if (selectedProfileId != null && selectedProfileId.isBlank()) return@withLock
        val existingProfile = selectedProfileId?.let { repository.getLlmApiProfile(it) }
        if (existingProfile != null) {
            val legacyModel = preferences[SELECTED_MODEL] ?: preferences[MODEL].orEmpty()
            if (existingProfile.selectedModel.isBlank() && legacyModel.isNotBlank()) {
                repository.selectLlmModel(existingProfile.id, legacyModel)
            }
            return@withLock
        }

        val profileId = selectedProfileId ?: DEFAULT_LLM_PROFILE_ID
        val baseUrl = OpenAiCompatibleClient.normalizeBaseUrl(
            preferences[BASE_URL] ?: "https://api.openai.com/v1"
        )
        val legacyModel = preferences[SELECTED_MODEL] ?: preferences[MODEL].orEmpty()
        if (repository.getLlmApiProfile(profileId) == null) {
            repository.saveLlmApiProfile(
                LlmApiProfileEntity(profileId, "默认 API", baseUrl, createdAt = 0L, updatedAt = 0L)
            )
        }
        if (legacyModel.isNotBlank() && repository.getLlmModels().none {
                it.apiProfileId == profileId && it.modelId == legacyModel
            }) {
            repository.replaceLlmModels(profileId, listOf(legacyModel))
        }
        repository.selectLlmModel(profileId, legacyModel)
        secureKeyStore.migrateLegacy(profileId)
        context.llmDataStore.edit {
            it[SELECTED_PROFILE] = profileId
            it[SELECTED_MODEL] = legacyModel
        }
    }

    suspend fun snapshot(): LlmProfileSnapshot {
        ensureLegacyMigration()
        val selected = config.first()
        require(selected.apiProfileId.isNotBlank()) { "请先选择 LLM API" }
        val profile = repository.getLlmApiProfile(selected.apiProfileId)
            ?: error("找不到所选 LLM API")
        return LlmProfileSnapshot(
            apiProfileId = profile.id,
            baseUrl = profile.baseUrl,
            apiKey = readApiKey(profile.id),
            modelId = profile.selectedModel
        )
    }

    suspend fun saveSelection(identity: LlmModelIdentity) {
        require(repository.getLlmApiProfile(identity.apiProfileId) != null) { "找不到所选 LLM API" }
        repository.selectLlmModel(identity.apiProfileId, identity.modelId)
        context.llmDataStore.edit {
            it[SELECTED_PROFILE] = identity.apiProfileId
            it[SELECTED_MODEL] = identity.modelId.trim()
        }
    }

    suspend fun clearSelection() {
        context.llmDataStore.edit {
            it[SELECTED_PROFILE] = ""
            it[SELECTED_MODEL] = ""
        }
    }

    suspend fun save(baseUrl: String, model: String, apiKey: String?) {
        ensureLegacyMigration()
        val current = config.first()
        repository.saveLlmApiProfile(
            repository.getLlmApiProfile(current.apiProfileId)?.copy(baseUrl = baseUrl)
                ?: LlmApiProfileEntity(current.apiProfileId, "默认 API", baseUrl)
        )
        repository.replaceLlmModels(current.apiProfileId, listOf(model))
        saveSelection(LlmModelIdentity(current.apiProfileId, model))
        if (apiKey != null) writeApiKey(current.apiProfileId, apiKey)
    }

    suspend fun saveConfig(baseUrl: String, model: String) = save(baseUrl, model, null)

    fun writeApiKey(profileId: String, value: String) = secureKeyStore.write(profileId, value.trim())
    fun readApiKey(profileId: String): String = secureKeyStore.read(profileId).orEmpty()
    fun hasApiKey(profileId: String): Boolean = secureKeyStore.read(profileId).isNullOrBlank().not()
    fun clearApiKey(profileId: String) = secureKeyStore.write(profileId, "")

    companion object {
        private val BASE_URL = stringPreferencesKey("base_url")
        private val MODEL = stringPreferencesKey("model")
        private val SELECTED_PROFILE = stringPreferencesKey("selected_api_profile_id")
        private val SELECTED_MODEL = stringPreferencesKey("selected_model_id")
    }
}

private class SecureApiKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences("secure_llm_credentials", Context.MODE_PRIVATE)

    fun write(profileId: String, value: String) {
        val prefix = safe(profileId)
        if (value.isBlank()) {
            preferences.edit().remove("${prefix}_ciphertext").remove("${prefix}_iv").apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        preferences.edit()
            .putString("${prefix}_ciphertext", Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP))
            .putString("${prefix}_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun read(profileId: String): String? = decrypt("${safe(profileId)}_ciphertext", "${safe(profileId)}_iv")

    fun migrateLegacy(profileId: String) {
        if (read(profileId).isNullOrBlank()) decrypt(LEGACY_CIPHERTEXT, LEGACY_IV)?.let { write(profileId, it) }
    }

    private fun decrypt(ciphertextKey: String, ivKey: String): String? = runCatching {
        val encrypted = preferences.getString(ciphertextKey, null) ?: return null
        val iv = preferences.getString(ivKey, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)))
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private fun safe(profileId: String) = profileId.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private companion object {
        const val KEY_ALIAS = "story_brain_llm_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val LEGACY_CIPHERTEXT = "ciphertext"
        const val LEGACY_IV = "iv"
    }
}
