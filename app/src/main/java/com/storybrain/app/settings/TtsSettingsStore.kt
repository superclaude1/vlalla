package com.storybrain.app.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.storybrain.app.data.TtsProfileIds
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ttsDataStore by preferencesDataStore(name = "tts_settings_v4")

data class TtsGlobalConfig(
    val globalProfileId: String = TtsProfileIds.EDGE
)

class TtsSettingsStore(private val context: Context) {
    private val secure = SecureTtsCredentialStore(context)

    val config: Flow<TtsGlobalConfig> = context.ttsDataStore.data.map { preferences ->
        val stored = preferences[GLOBAL_PROFILE]
        val legacy = preferences[LEGACY_SERVICE]
        TtsGlobalConfig(
            globalProfileId = stored ?: when (legacy) {
                "FISH_AUDIO" -> TtsProfileIds.FISH
                "OPENAI_COMPATIBLE" -> TtsProfileIds.OPENAI
                else -> TtsProfileIds.EDGE
            }
        )
    }

    suspend fun saveGlobalProfile(profileId: String) {
        context.ttsDataStore.edit { preferences -> preferences[GLOBAL_PROFILE] = profileId }
    }

    suspend fun saveInsecureHttpAllowed(profileId: String, allowed: Boolean) {
        context.ttsDataStore.edit { preferences ->
            preferences[booleanPreferencesKey("allow_insecure_${safeProfileId(profileId)}")] = allowed
        }
    }

    suspend fun isInsecureHttpAllowed(profileId: String, baseUrl: String): Boolean {
        val key = booleanPreferencesKey("allow_insecure_${safeProfileId(profileId)}")
        val preferences = configPreferences()
        return preferences[key] ?: EndpointPolicy.isInsecure(baseUrl)
    }

    fun writeApiKey(profileId: String, value: String) = secure.write(profileId, value.trim())
    fun readApiKey(profileId: String) = secure.read(profileId).orEmpty()
    fun hasApiKey(profileId: String) = secure.read(profileId).isNullOrBlank().not()
    fun clearApiKey(profileId: String) = secure.write(profileId, "")

    private suspend fun configPreferences() = context.ttsDataStore.data.first()
    private fun safeProfileId(profileId: String) = profileId.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private companion object {
        val GLOBAL_PROFILE = stringPreferencesKey("global_profile_id")
        val LEGACY_SERVICE = stringPreferencesKey("service")
    }
}

private class SecureTtsCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences("secure_tts_credentials_v2", Context.MODE_PRIVATE)

    fun write(profileId: String, value: String) {
        val prefix = profileId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        if (value.isBlank()) {
            preferences.edit().remove("${prefix}_cipher").remove("${prefix}_iv").apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        preferences.edit()
            .putString("${prefix}_cipher", Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP))
            .putString("${prefix}_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun read(profileId: String): String? = runCatching {
        val prefix = profileId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val encrypted = preferences.getString("${prefix}_cipher", null) ?: return null
        val iv = preferences.getString("${prefix}_iv", null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)))
    }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val ALIAS = "zhangjing_tts_provider_keys_v2"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
