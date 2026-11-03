package com.flowai.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.Executors

/** Uses a locally configured OpenAI-compatible provider when available; otherwise uses the project backend. */
object OnlineFlowBrain {
    private const val BACKEND_URL = BuildConfig.FLOW_API_URL
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun isConfigured(context: Context): Boolean =
        FlowApiKeyStore.isConfigured(context) || isBackendConfigured()

    private fun isBackendConfigured(): Boolean = runCatching {
        val url = URL(BACKEND_URL)
        url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null &&
            url.query == null && url.ref == null && (url.path.isEmpty() || url.path == "/")
    }.getOrDefault(false)

    fun ask(context: Context, message: String, onResult: (String?, String?) -> Unit) {
        val appContext = context.applicationContext
        worker.execute {
            var connection: HttpURLConnection? = null
            try {
                require(message.isNotBlank()) { "Please enter a message." }
                require(message.length <= 4000) { "Messages must be 4000 characters or fewer." }

                val local = FlowApiKeyStore.load(appContext)
                val target: String
                val payload: JSONObject
                if (local != null) {
                    val base = URL(local.baseUrl)
                    require(base.protocol == "https" && base.host.isNotBlank() && base.userInfo == null &&
                        base.query == null && base.ref == null) { "The API endpoint must be a valid HTTPS URL." }
                    target = local.baseUrl.trimEnd('/') + "/chat/completions"
                    payload = JSONObject()
                        .put("model", local.model)
                        .put("messages", org.json.JSONArray().put(
                            JSONObject().put("role", "user").put("content", message)
                        ))
                } else {
                    if (!isBackendConfigured()) {
                        throw IllegalStateException("Add your API key in Flow's AI settings, or configure the project backend.")
                    }
                    target = BACKEND_URL.trimEnd('/') + "/v1/flow/chat"
                    payload = JSONObject().put("message", message)
                }

                connection = (URL(target).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    if (local != null) setRequestProperty("Authorization", "Bearer ${local.apiKey}")
                }
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.let { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
                }.orEmpty()
                if (code !in 200..299) {
                    val detail = runCatching { JSONObject(body).optString("error").take(240) }
                        .getOrNull()?.takeIf { it.isNotBlank() }
                    throw IllegalStateException(detail ?: "AI provider returned HTTP $code. Check your endpoint, key, model and provider quota.")
                }

                val response = JSONObject(body)
                val reply = if (local != null) {
                    response.optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.optString("content").orEmpty().trim()
                } else response.optString("reply").trim()
                if (reply.isEmpty()) throw IllegalStateException("The AI provider returned an empty reply.")
                main.post { onResult(reply, null) }
            } catch (error: Exception) {
                main.post {
                    val messageText = when (error) {
                        is SocketTimeoutException -> "Flow took too long to reply. Please try again."
                        is UnknownHostException -> "Cannot find the AI server. Check the endpoint and your internet connection."
                        else -> error.message ?: "Flow couldn't reach the online AI provider."
                    }
                    onResult(null, messageText)
                }
            } finally {
                connection?.disconnect()
            }
        }
    }
}
