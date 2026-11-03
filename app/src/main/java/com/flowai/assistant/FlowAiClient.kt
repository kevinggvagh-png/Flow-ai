package com.flowai.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the user's personal Gemini key encrypted with an Android Keystore key.
 * A key is never committed to the repository. For a public production app,
 * use a server-side proxy instead of distributing a shared API key.
 */
object FlowAiClient {
    private const val PREFS = "flow_ai_secure"
    private const val VALUE = "gemini_key"
    private const val ALIAS = "flow_ai_gemini_key"
    private const val MODEL = "gemini-3.5-flash-lite"

    fun saveApiKey(context: Context, apiKey: String) {
        require(apiKey.isNotBlank()) { "Enter an API key first." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(VALUE, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    fun hasApiKey(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(VALUE, null).isNullOrBlank()

    private fun readApiKey(context: Context): String {
        val packedText = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(VALUE, null) ?: throw IllegalStateException("Add your Gemini API key in Flow AI settings first.")
        val packed = Base64.decode(packedText, Base64.NO_WRAP)
        require(packed.size > 12) { "Saved AI key is invalid. Please add it again." }
        val iv = packed.copyOfRange(0, 12)
        val encrypted = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun ask(context: Context, prompt: String): String {
        val key = readApiKey(context)
        val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", key)
        }
        try {
            val body = JSONObject()
                .put("contents", org.json.JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "parts", org.json.JSONArray().put(JSONObject().put("text", prompt))
                    )
                ))
                .put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 1200))
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            if (status !in 200..299) {
                val detail = try { JSONObject(response).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "Gemini request failed (HTTP $status). Check your key and free-tier access.")
            }
            val json = JSONObject(response)
            return json.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getString("text")
        } finally {
            connection.disconnect()
        }
    }
}
