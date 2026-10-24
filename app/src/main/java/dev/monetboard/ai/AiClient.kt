package dev.monetboard.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Float,
    val systemPrompt: String,
)

/** Minimal OpenAI-compatible /chat/completions client (OpenAI, OpenRouter, DeepSeek, Groq, Ollama...). Blocking: call off the main thread. */
object AiClient {
    data class Msg(val role: String, val content: String)

    private val think = Regex("(?s)<think>.*?</think>")

    fun chat(cfg: AiConfig, messages: List<Msg>): String {
        if (cfg.baseUrl.isBlank()) throw IOException("Не указан Base URL")
        if (cfg.model.isBlank()) throw IOException("Не указана модель")
        val url = URL(cfg.baseUrl.trim().trimEnd('/') + "/chat/completions")
        val body = JSONObject().apply {
            put("model", cfg.model.trim())
            put("temperature", cfg.temperature.toDouble())
            put("messages", JSONArray().apply {
                messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
            })
        }.toString()

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (cfg.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${cfg.apiKey.trim()}")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException((extractError(text) ?: "HTTP $code").take(240))
            val content = JSONObject(text).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content")
            return think.replace(content, "").trim()
        } finally {
            conn.disconnect()
        }
    }

    private fun extractError(body: String): String? = runCatching {
        val o = JSONObject(body)
        val msg = when (val e = o.opt("error")) {
            is JSONObject -> e.optString("message")
            is String -> e
            else -> o.optString("message")
        }
        msg.takeIf { it.isNotBlank() }
    }.getOrNull()
}
