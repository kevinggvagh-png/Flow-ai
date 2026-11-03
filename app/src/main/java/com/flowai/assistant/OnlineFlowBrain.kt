package com.flowai.assistant

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
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

    fun isConfigured(): Boolean = BACKEND_URL.startsWith("https://")

    fun ask(message: String, onResult: (String?, String?) -> Unit) {
        worker.execute {
            var connection: HttpURLConnection? = null
            try {
                if (!isConfigured()) {
                    throw IllegalStateException(
                        "Flow's online AI server is not connected yet. The app needs a trusted HTTPS backend; no API key should be placed inside the APK."
                    )
                }
                connection = (URL(BACKEND_URL.trimEnd('/') + "/v1/flow/chat")
                    .openConnection() as HttpURLConnection).apply {
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
                    throw IllegalStateException("Flow's AI server returned HTTP $code.")
                }
                val reply = JSONObject(body).optString("reply").trim()
                if (reply.isEmpty()) throw IllegalStateException("The AI server returned an empty reply.")
                main.post { onResult(reply, null) }
            } catch (error: Exception) {
                main.post {
                    onResult(null, error.message ?: "Flow couldn't reach its online AI server.")
                }
            } finally {
                connection?.disconnect()
            }
        }
    }
}
