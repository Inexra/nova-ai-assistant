package com.inexra.nova

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional local language model. The first download needs internet; inference after that
 * runs on the phone CPU and does not send prompts to a server.
 */
object LocalLlmClient {
    private const val MODEL_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
    private const val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
    private val main = Handler(Looper.getMainLooper())
    private val modelMutex = Mutex()
    @Volatile private var loadedModel: LlamaModel? = null

    fun modelFile(context: Context): File {
        val directory = File(context.getExternalFilesDir("models") ?: File(context.filesDir, "models"), "nova")
        if (!directory.exists()) directory.mkdirs()
        return File(directory, MODEL_NAME)
    }

    fun isModelDownloaded(context: Context): Boolean {
        val file = modelFile(context)
        return file.isFile && file.length() > 100_000_000L
    }

    fun downloadModel(context: Context, onProgress: (Long, Long) -> Unit, onComplete: (Result<File>) -> Unit) {
        Thread {
            val target = modelFile(context)
            val partial = File(target.parentFile, target.name + ".part")
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Nova-Android-Assistant")
                }
                connection.connect()
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("Model download failed (HTTP $code)")
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var downloaded = 0L
                        var lastReport = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (downloaded - lastReport >= 1_000_000L || (total > 0 && downloaded == total)) {
                                lastReport = downloaded
                                main.post { onProgress(downloaded, total) }
                            }
                        }
                    }
                }
                if (partial.length() < 100_000_000L) throw IllegalStateException("Downloaded model is too small; the download may have been interrupted.")
                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                main.post { onComplete(Result.success(target)) }
            } catch (error: Exception) {
                partial.delete()
                main.post { onComplete(Result.failure(error)) }
            } finally {
                connection?.disconnect()
            }
        }.apply { name = "Nova-Model-Download"; isDaemon = true }.start()
    }

    suspend fun answer(context: Context, prompt: String): String = modelMutex.withLock {
        val file = modelFile(context)
        if (!isModelDownloaded(context)) {
            throw IllegalStateException("Download the optional offline AI model in Nova Settings first.")
        }
        var model = loadedModel
        if (model == null || !model.isLoaded) {
            model = Llama.loadModel(
                modelPath = file.absolutePath,
                config = LlamaConfig(contextSize = 2048, threads = 4, gpuLayers = 0, temperature = 0.65f)
            )
            loadedModel = model
        }
        val result = Llama.complete(
            model = model,
            prompt = prompt,
            systemPrompt = "You are Nova, a helpful Android phone assistant. You are running locally without internet. Be concise, honest about uncertainty, and never claim to have performed a phone action unless the app confirms it.",
            maxTokens = 220
        )
        result.text.trim().ifBlank { "I couldn't generate a response. Please try a shorter question." }
    }

    suspend fun release() {
        modelMutex.withLock {
            loadedModel?.let { Llama.releaseModel(it) }
            loadedModel = null
        }
    }
}
