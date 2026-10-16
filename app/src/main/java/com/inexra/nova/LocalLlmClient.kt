package com.inexra.nova

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Qwen is bundled as an APK asset; it is copied to app-private storage on first use. */
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

    private fun hasBundledModel(context: Context): Boolean =
        runCatching { context.assets.open(MODEL_NAME).close(); true }.getOrDefault(false)

    fun isModelDownloaded(context: Context): Boolean {
        val file = modelFile(context)
        return (file.isFile && file.length() > 100_000_000L) || hasBundledModel(context)
    }

    private suspend fun ensureModelFile(context: Context): File = withContext(Dispatchers.IO) {
        val target = modelFile(context)
        if (target.isFile && target.length() > 100_000_000L) return@withContext target
        if (!hasBundledModel(context)) {
            throw IllegalStateException("The offline AI model is not available. Reinstall Nova or download the model in Settings.")
        }
        val partial = File(target.parentFile, target.name + ".part")
        try {
            context.assets.open(MODEL_NAME).buffered().use { input ->
                partial.outputStream().buffered().use { output -> input.copyTo(output, 256 * 1024) }
            }
            if (partial.length() < 100_000_000L) throw IllegalStateException("The bundled model asset is incomplete.")
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            target
        } catch (error: Exception) {
            partial.delete()
            throw error
        }
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
        val file = ensureModelFile(context)
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
