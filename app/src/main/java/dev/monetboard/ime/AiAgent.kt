package dev.monetboard.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.monetboard.ai.AiClient
import dev.monetboard.ai.AiClient.Msg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AiAction(val label: String, val applyLabel: String, val replace: Boolean, private val instr: String) {
    FIX("Исправить", "Заменить", true, "Fix spelling, grammar and punctuation. Keep meaning, tone, language and formatting. Output only the corrected text."),
    REWRITE("Переписать", "Заменить", true, "Rewrite the text to sound clearer and more natural. Keep the meaning and the language. Output only the rewritten text."),
    SHORTER("Короче", "Заменить", true, "Make the text shorter and tighter without losing key information. Keep the language. Output only the result."),
    LONGER("Подробнее", "Заменить", true, "Expand the text with a bit more detail and polish. Keep the language and intent. Output only the result."),
    FORMAL("Формально", "Заменить", true, "Rewrite the text in a polite, formal, professional tone. Keep the language. Output only the result."),
    FRIENDLY("Дружелюбно", "Заменить", true, "Rewrite the text in a warm, casual, friendly tone. Keep the language. Output only the result."),
    TRANSLATE("Перевести", "Заменить", true, "Translate the text into %s. Output only the translation."),
    CONTINUE("Продолжить", "Вставить", false, "Continue the text naturally, in the same voice and language. Output only the continuation, do not repeat the original."),
    ANSWER("Ответить", "Вставить", false, "The text is a message or a question. Write the best short reply or answer, in the same language. Output only the reply.");

    fun prompt(target: String) = if (this == TRANSLATE) instr.format(target) else instr
}

private fun targetLang(mode: Int, text: String): String = when (mode) {
    1 -> "Russian"
    2 -> "English"
    else -> {
        val cyr = text.count { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
        val lat = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        if (cyr >= lat) "English" else "Russian"
    }
}

/** In-keyboard AI: quick actions on the field text/selection + free chat with short history. */
class AiAgent(private val c: KeyboardController) {
    var open by mutableStateOf(false)
    var prompt by mutableStateOf("")
    var busy by mutableStateOf(false)
    var result by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var applyLabel by mutableStateOf("Вставить")
    var useContext by mutableStateOf(true)
    var allowed = true

    val showCard: Boolean get() = busy || result != null || error != null

    private var applyFn: ((String) -> Unit)? = null
    private var lastRun: (() -> Unit)? = null
    private val history = ArrayList<Msg>()
    private var job: Job? = null

    fun toggle() {
        if (open) close() else if (allowed) open = true
    }

    fun close() {
        job?.cancel()
        open = false; busy = false; result = null; error = null; prompt = ""
        c.afterEdit()
    }

    fun dismissCard() {
        job?.cancel()
        busy = false; result = null; error = null
    }

    fun resetChat() { history.clear(); dismissCard() }

    fun retry() { lastRun?.invoke() }

    fun apply() {
        val r = result ?: return
        applyFn?.invoke(r)
        close()
    }

    fun runAction(a: AiAction) {
        val src = c.readSource()
        if (src.text.isBlank()) {
            result = null; error = "Поле пустое: напиши текст или выдели его"; return
        }
        lastRun = { runAction(a) }
        val instr = a.prompt(targetLang(c.prefs.aiTarget, src.text))
        val msgs = listOf(Msg("system", c.prefs.aiSystemPrompt), Msg("user", "$instr\n\nTEXT:\n${src.text}"))
        request(msgs, a.applyLabel, onDone = null) { out ->
            if (a.replace) c.replaceSource(src, out) else c.insertText(out)
        }
    }

    fun send() {
        val q = prompt.trim()
        if (q.isEmpty() || busy) return
        val field = if (useContext) c.readSource().text.take(4000) else ""
        val user = if (field.isBlank()) q else "$q\n\n---\nText in the current field:\n$field"
        val msgs = ArrayList<Msg>()
        msgs += Msg("system", c.prefs.aiSystemPrompt)
        msgs += history.takeLast(8)
        msgs += Msg("user", user)
        lastRun = { prompt = q; send() }
        prompt = ""
        request(msgs, "Вставить", onDone = { out ->
            history += Msg("user", q); history += Msg("assistant", out)
        }) { out -> c.insertText(out) }
    }

    private fun request(msgs: List<Msg>, label: String, onDone: ((String) -> Unit)?, apply: (String) -> Unit) {
        val p = c.prefs
        if (!p.aiEnabled) { error = "ИИ выключен в настройках"; return }
        val local = listOf("127.0.0.1", "localhost", "192.168.", "10.0.").any { p.aiBaseUrl.contains(it) }
        if (p.aiApiKey.isBlank() && !local) { result = null; error = "Добавь API-ключ: Настройки → ИИ-агент"; return }
        job?.cancel()
        busy = true; error = null; result = null
        val cfg = p.aiConfig()
        job = c.scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { AiClient.chat(cfg, msgs) } }
            busy = false
            r.onSuccess { text ->
                if (text.isBlank()) error = "Пустой ответ" else {
                    result = text; applyLabel = label; applyFn = apply; onDone?.invoke(text)
                }
            }.onFailure { error = it.message ?: it.javaClass.simpleName }
        }
    }
}
