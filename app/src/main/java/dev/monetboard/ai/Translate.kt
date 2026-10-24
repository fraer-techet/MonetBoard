package dev.monetboard.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TrLang(val code: String, val name: String)

val TrLangs = listOf(
    TrLang("auto", "Авто"), TrLang("ru", "Русский"), TrLang("en", "English"), TrLang("uk", "Українська"),
    TrLang("de", "Deutsch"), TrLang("fr", "Français"), TrLang("es", "Español"), TrLang("it", "Italiano"),
    TrLang("tr", "Türkçe"), TrLang("pl", "Polski"), TrLang("zh", "中文"),
)

internal object Http {
    fun request(url: URL, method: String, body: String?, headers: Map<String, String>): String {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) MonetBoard")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) doOutput = true
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException(errorOf(text) ?: "HTTP $code")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun errorOf(body: String): String? = runCatching {
        val o = JSONObject(body)
        (o.optString("message").ifBlank { o.optJSONObject("error")?.optString("message").orEmpty() }).takeIf { it.isNotBlank() }
    }.getOrNull() ?: body.take(120).takeIf { it.isNotBlank() }
}

object Translate {
    fun autoTarget(text: String): String {
        val cyr = text.count { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
        val lat = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        return if (cyr >= lat) "en" else "ru"
    }

    /** Keyless endpoint used by Google's own clients. Unofficial: may rate-limit. Newlines survive inside the response segments. */
    fun google(text: String, target: String): String {
        val tl = if (target == "zh") "zh-CN" else target
        val out = StringBuilder()
        for (chunk in chunks(text, 1200)) {
            val url = URL(
                "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$tl&dt=t&q=" +
                    URLEncoder.encode(chunk, "UTF-8"),
            )
            val seg = JSONArray(Http.request(url, "GET", null, emptyMap())).getJSONArray(0)
            for (i in 0 until seg.length()) out.append(seg.optJSONArray(i)?.optString(0).orEmpty())
        }
        return out.toString().trim()
    }

    /** Official Yandex Cloud Translate v2 (needs Api-Key + Folder ID). */
    fun yandex(text: String, target: String, key: String, folder: String): String {
        if (key.isBlank() || folder.isBlank()) throw IOException("Укажи API-ключ и Folder ID Яндекса: Настройки → Инструменты")
        val body = JSONObject()
            .put("folderId", folder.trim())
            .put("texts", JSONArray().put(text.take(9000)))
            .put("targetLanguageCode", target)
            .toString()
        val resp = Http.request(
            URL("https://translate.api.cloud.yandex.net/translate/v2/translate"), "POST", body,
            mapOf("Content-Type" to "application/json", "Authorization" to "Api-Key ${key.trim()}"),
        )
        return JSONObject(resp).getJSONArray("translations").getJSONObject(0).getString("text").trim()
    }

    private fun chunks(text: String, max: Int): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            var end = minOf(i + max, text.length)
            if (end < text.length) {
                val cut = text.lastIndexOfAny(charArrayOf('\n', ' ', '.', '!', '?'), end - 1)
                if (cut > i + max / 2) end = cut + 1
            }
            out += text.substring(i, end)
            i = end
        }
        return out
    }
}
