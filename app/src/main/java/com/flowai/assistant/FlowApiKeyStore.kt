package com.flowai.assistant

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class FlowProviderConfig(
    val apiKey: String,
    val baseUrl: String,
    val model: String
)

/** Encrypts provider settings with an AES key held in Android Keystore. */
object FlowApiKeyStore {
    private const val PREFS = "flow_private_ai_settings"
    private const val ENTRY = "encrypted_config"
    private const val KEY_ALIAS = "flow_ai_local_api_settings_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun save(context: android.content.Context, config: FlowProviderConfig) {
        require(config.apiKey.isNotBlank()) { "Enter an API key." }
        require(config.baseUrl.startsWith("https://", ignoreCase = true)) { "Use an HTTPS API endpoint." }
        require(config.model.isNotBlank()) { "Enter a model name." }
        val plain = JSONObject()
            .put("apiKey", config.apiKey.trim())
            .put("baseUrl", config.baseUrl.trim().trimEnd('/'))
            .put("model", config.model.trim())
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain)
        val packed = JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(ENTRY, packed).apply()
    }

    fun load(context: android.content.Context): FlowProviderConfig? = runCatching {
        val packed = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(ENTRY, null) ?: return null
        val envelope = JSONObject(packed)
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val encrypted = Base64.decode(envelope.getString("data"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        val json = JSONObject(String(cipher.doFinal(encrypted), StandardCharsets.UTF_8))
        FlowProviderConfig(
            json.getString("apiKey"),
            json.getString("baseUrl"),
            json.getString("model")
        )
    }.getOrNull()

    fun clear(context: android.content.Context) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().remove(ENTRY).apply()
    }

    fun isConfigured(context: android.content.Context): Boolean = load(context) != null
}
