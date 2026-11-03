package com.flowai.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * On-device Flow brain. Model weights are downloaded only after the user
 * chooses the download action; they are not bundled in the APK.
 */
object LocalFlowBrain {
    private const val MODEL_FILE = "Qwen3-0.6B.litertlm"
    private const val MODEL_URL =
        "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm"
    private const val EXPECTED_BYTES = 586_000_000L

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    fun modelFile(context: Context) = File(context.filesDir, MODEL_FILE)
    fun isModelDownloaded(context: Context) = modelFile(context).let { it.exists() && it.length() > 100_000_000L }

    fun downloadModel(
        context: Context,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        val target = modelFile(context)
        worker.execute {
            try {
                if (isModelDownloaded(context)) {
                    main.post { onComplete(true, "Flow's AI model is already downloaded.") }
                    return@execute
                }
                val partial = File(context.filesDir, "$MODEL_FILE.part")
                val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 60000
                    instanceFollowRedirects = true
                }
                try {
                    connection.connect()
                    if (connection.responseCode !in 200..299) {
                        throw IllegalStateException("Model download failed (HTTP ${connection.responseCode}).")
                    }
                    val total = connection.contentLengthLong.takeIf { it > 0 } ?: EXPECTED_BYTES
                    connection.inputStream.use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var count: Long = 0
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                count += n
                                val percent = ((count * 100) / total).toInt().coerceIn(0, 99)
                                main.post { onProgress(percent) }
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                if (partial.length() < 500_000_000L) {
                    partial.delete()
                    throw IllegalStateException("The downloaded model is incomplete. Please retry on a stable Wi-Fi connection.")
                }
                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) throw IllegalStateException("Could not save the model file.")
                main.post { onProgress(100); onComplete(true, "AI model downloaded. Flow can now think offline.") }
            } catch (e: Exception) {
                main.post { onComplete(false, e.message ?: "Model download failed.") }
            }
        }
    }

    fun ask(context: Context, prompt: String, onChunk: (String) -> Unit, onDone: (String?) -> Unit) {
        worker.execute {
            try {
                if (conversation == null) {
                    val file = modelFile(context)
                    if (!isModelDownloaded(context)) throw IllegalStateException("Download Flow's AI model first from the Flow AI home screen.")
                    val candidate = Engine(
                        EngineConfig(
                            modelPath = file.absolutePath,
                            backend = Backend.CPU(),
                            cacheDir = context.cacheDir.absolutePath
                        )
                    )
                    candidate.initialize()
                    engine = candidate
                    conversation = candidate.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(
                                "You are Flow, a calm, capable, friendly tablet assistant. " +
                                "Give useful, clear answers. Never claim you can see the user's screen " +
                                "or operate apps unless that capability is actually provided."
                            )
                        )
                    )
                }
                runBlocking {
                    checkNotNull(conversation).sendMessageAsync(prompt).collect { chunk ->
                        val piece = chunk.toString()
                        main.post { onChunk(piece) }
                    }
                }
                main.post { onDone(null) }
            } catch (e: Exception) {
                main.post { onDone(e.message ?: "Flow could not complete that request.") }
            }
        }
    }
}
