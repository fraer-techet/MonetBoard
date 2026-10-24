package dev.monetboard.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.monetboard.ai.Translate
import dev.monetboard.ai.TrLangs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** In-keyboard translator. Source = text typed into its own line, else the field text / selection. */
class TranslatorAgent(private val c: KeyboardController) {
    var input by mutableStateOf("")
    var busy by mutableStateOf(false)
    var result by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var applyLabel by mutableStateOf("Вставить")

    val showCard: Boolean get() = busy || result != null || error != null

    private var src: Source? = null
    private var job: Job? = null

    fun run() {
        if (busy) return
        val typed = input.trim()
        val source = if (typed.isEmpty()) c.readSource().takeIf { it.text.isNotBlank() } else null
        val text = if (typed.isNotEmpty()) typed else source?.text.orEmpty()
        if (text.isBlank()) {
            result = null
            error = "Нечего переводить: напиши текст здесь или в поле"
            return
        }
        src = source
        val p = c.prefs
        val sel = TrLangs[p.trTarget.coerceIn(0, TrLangs.lastIndex)]
        val target = if (sel.code == "auto") Translate.autoTarget(text) else sel.code
        val provider = p.trProvider
        val key = p.yandexKey
        val folder = p.yandexFolder
        job?.cancel()
        busy = true; error = null; result = null
        job = c.scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    if (provider == 1) Translate.yandex(text, target, key, folder) else Translate.google(text, target)
                }
            }
            busy = false
            r.onSuccess { out ->
                if (out.isBlank()) error = "Пустой ответ" else {
                    result = out
                    applyLabel = if (src != null) "Заменить" else "Вставить"
                }
            }.onFailure { error = it.message ?: it.javaClass.simpleName }
        }
    }

    fun apply() {
        val r = result ?: return
        val s = src
        if (s != null) c.replaceSource(s, r) else c.insertText(r)
        c.openPanel(Panel.KEYS)
    }

    fun copy() {
        val r = result ?: return
        val cm = c.service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("translation", r))
    }

    fun dismissCard() {
        job?.cancel()
        busy = false; result = null; error = null
    }

    fun close() {
        dismissCard()
        input = ""
    }
}
