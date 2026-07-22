package com.storybrain.app.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.llmDataStore by preferencesDataStore(name = "llm_settings")

data class LlmConfig(
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "",
    val allowInsecureHttp: Boolean = false
)

class LlmSettingsStore(private val context: Context) {
    private val secureKeyStore = SecureApiKeyStore(context)

    val config: Flow<LlmConfig> = context.llmDataStore.data.map { preferences ->
        val baseUrl = preferences[BASE_URL] ?: "https://api.openai.com/v1"
        LlmConfig(
            baseUrl = baseUrl,
            model = preferences[MODEL] ?: "",
            // Existing HTTP configurations stay compatible until the user saves them again.
            allowInsecureHttp = preferences[ALLOW_INSECURE_HTTP] ?: EndpointPolicy.isInsecure(baseUrl)
        )
    }

    suspend fun save(baseUrl: String, model: String, apiKey: String?, allowInsecureHttp: Boolean = false) {
        val normalized = EndpointPolicy.requireAllowed(baseUrl, allowInsecureHttp)
        context.llmDataStore.edit { preferences ->
            preferences[BASE_URL] = normalized
            preferences[MODEL] = model.trim()
            preferences[ALLOW_INSECURE_HTTP] = allowInsecureHttp
        }
        if (apiKey != null) secureKeyStore.write(apiKey.trim())
    }

    fun readApiKey(): String = secureKeyStore.read().orEmpty()
    fun hasApiKey(): Boolean = secureKeyStore.read().isNullOrBlank().not()
    fun clearApiKey() = secureKeyStore.write("")

    companion object {
        private val BASE_URL = stringPreferencesKey("base_url")
        private val MODEL = stringPreferencesKey("model")
        private val ALLOW_INSECURE_HTTP = booleanPreferencesKey("allow_insecure_http")
    }
}

private class SecureApiKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences("secure_llm_credentials", Context.MODE_PRIVATE)

    fun write(value: String) {
        if (value.isBlank()) {
            preferences.edit().remove(CIPHERTEXT).remove(IV).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        preferences.edit()
            .putString(CIPHERTEXT, Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP))
            .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun read(): String? = runCatching {
        val encrypted = preferences.getString(CIPHERTEXT, null) ?: return null
        val iv = preferences.getString(IV, null) ?: return null
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
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "story_brain_llm_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val CIPHERTEXT = "ciphertext"
        const val IV = "iv"
    }
}
