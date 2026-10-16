package com.inexra.nova

import android.os.Handler
import android.os.Looper
import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

object NovaAiClient {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    data class SearchResult(val title: String, val snippet: String, val url: String)

    fun searchAndAnswer(query: String, apiKey: String, callback: (String) -> Unit) {
        executor.execute {
            val results = runCatching { searchWeb(query) }.getOrDefault(emptyList())
            val response = if (apiKey.isNotBlank()) {
                runCatching { askGemini(query, apiKey, results) }.getOrElse { error ->
                    if (results.isNotEmpty()) formatResults(results, "AI answer unavailable (\${error.message ?: "network error"}). Search results:")
                    else "I couldn't reach Gemini or the web. Check your internet connection and API key, then try again."
                }
            } else {
                if (results.isNotEmpty()) formatResults(results, "Here are useful web results. Add your Gemini API key in Nova Settings for a synthesized answer:")
                else "I couldn't retrieve web results. Check your internet connection. You can add a Gemini API key in Nova Settings for AI answers."
            }
            main.post { callback(response) }
        }
    }

    private fun searchWeb(query: String): List<SearchResult> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val connection = URL("https://html.duckduckgo.com/html/?q=$encoded").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 12000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile) AppleWebKit/537.36 Chrome/124 Safari/537.36")
        try {
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val output = mutableListOf<SearchResult>()
            val blocks = Regex("""(?s)<div class="result[^"]*".*?</div>\s*</div>""").findAll(html)
            for (match in blocks) {
                if (output.size >= 5) break
                val block = match.value
                val link = Regex("""<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).find(block)
                val snippetMatch = Regex("""class="result__snippet"[^>]*>(.*?)</""", RegexOption.DOT_MATCHES_ALL).find(block)
                if (link != null) {
                    val title = cleanHtml(link.groupValues[2])
                    val url = cleanHtml(link.groupValues[1])
                    val snippet = snippetMatch?.groupValues?.get(1)?.let(::cleanHtml).orEmpty()
                    if (title.isNotBlank()) output.add(SearchResult(title, snippet, url))
                }
            }
            return output
        } finally {
            connection.disconnect()
        }
    }

    private fun askGemini(query: String, key: String, results: List<SearchResult>): String {
        val context = if (results.isEmpty()) {
            "No web results were retrieved. Answer from your general knowledge and clearly say if uncertain."
        } else {
            results.joinToString("\n\n") { "Title: \${it.title}\nSnippet: \${it.snippet}\nURL: \${it.url}" }
        }
        val prompt = "You are Nova, a concise phone assistant. Answer the user's question clearly. Use the web search snippets below for current facts, distinguish facts from uncertainty, and do not invent citations. If useful, include source URLs.\n\nUser question: $query\n\nWeb results:\n$context"
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" +
            URLEncoder.encode(key, StandardCharsets.UTF_8.name())
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 12000
        connection.readTimeout = 25000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        try {
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                .put("generationConfig", JSONObject().put("temperature", 0.3).put("maxOutputTokens", 700))
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            if (responseCode !in 200..299) {
                val message = json.optJSONObject("error")?.optString("message").orEmpty()
                throw IllegalStateException(if (message.isBlank()) "Gemini request failed (HTTP $responseCode)" else message)
            }
            val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val answer = parts?.optJSONObject(0)?.optString("text").orEmpty().trim()
            if (answer.isBlank()) throw IllegalStateException("Gemini returned an empty answer")
            return answer
        } finally {
            connection.disconnect()
        }
    }

    private fun formatResults(results: List<SearchResult>, intro: String): String =
        intro + "\n\n" + results.take(5).joinToString("\n\n") {
            "\${it.title}\n\${it.snippet}\n\${it.url}"
        }

    @Suppress("DEPRECATION")
    private fun cleanHtml(value: String): String =
        Html.fromHtml(value.replace(Regex("<[^>]+>"), " ").replace("&amp;", "&")).toString()
            .replace(Regex("\\s+"), " ").trim()
}
