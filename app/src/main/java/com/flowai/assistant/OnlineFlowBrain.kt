package com.flowai.assistant

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

/**
 * Online Flow client.
 *
 * The app talks only to a trusted HTTPS backend owned/configured by the project
 * maintainer. Provider credentials must stay on that server, never in this APK.
 * Backend contract: POST /v1/flow/chat with {"message":"..."} and return
 * {"reply":"..."}.
 */
object OnlineFlowBrain {
    private const val BACKEND_URL = BuildConfig.FLOW_API_URL
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun isConfigured(): Boolean = runCatching {
        val url = URL(BACKEND_URL)
        url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null &&
            url.query == null && url.ref == null && (url.path.isEmpty() || url.path == "/")
    }.getOrDefault(false)

    fun ask(message: String, onResult: (String?, String?) -> Unit) {
        worker.execute {
            var connection: HttpURLConnection? = null
            try {
                if (!isConfigured()) {
                    throw IllegalStateException(
                        "Flow's online AI server is not connected yet. The app needs a trusted HTTPS backend; no API key should be placed inside the APK."
                    )
                }
                require(message.isNotBlank()) { "Please enter a message." }
                require(message.length <= 4000) { "Messages must be 4000 characters or fewer." }
                connection = (URL(BACKEND_URL.trimEnd('/') + "/v1/flow/chat")
                    .openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 45000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                val payload = JSONObject().put("message", message).toString()
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload) }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.let { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
                }.orEmpty()
                if (code !in 200..299) {
                    val detail = when (code) {
                        400, 413, 415, 429, 503 -> runCatching {
                            JSONObject(body).optString("error").take(240)
                        }.getOrNull()?.takeIf { it.isNotBlank() }
                        else -> null
                    }
                    throw IllegalStateException(detail ?: "Flow's AI server returned HTTP $code. Please try again.")
                }
                val reply = JSONObject(body).optString("reply").trim()
                if (reply.isEmpty()) throw IllegalStateException("The AI server returned an empty reply.")
                main.post { onResult(reply, null) }
            } catch (error: Exception) {
                main.post {
                    val message = when (error) {
                        is SocketTimeoutException -> "Flow took too long to reply. Please try again."
                        is UnknownHostException -> "Cannot find Flow's server. Check your internet connection."
                        else -> error.message ?: "Flow couldn't reach its online AI server."
                    }
                    onResult(null, message)
                }
            } finally {
                connection?.disconnect()
            }
        }
    }
}
