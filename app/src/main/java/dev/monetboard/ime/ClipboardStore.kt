package dev.monetboard.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.monetboard.data.Prefs
import org.json.JSONArray
import org.json.JSONObject

class Clip(val text: String, pinned: Boolean) {
    var pinned by mutableStateOf(pinned)
}

/** Clipboard history (newest first, pinned survive trimming). Persisted in prefs as JSON. */
class ClipboardStore(private val ctx: Context, private val prefs: Prefs) {
    val items = mutableStateListOf<Clip>()
    private val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { capture() }
    private var lastSaved = ""

    init { load() }

    fun start() { runCatching { cm.addPrimaryClipChangedListener(listener) }; capture() }
    fun stop() { runCatching { cm.removePrimaryClipChangedListener(listener) } }

    /** Pick up external changes (e.g. history cleared from settings) and the current clip. */
    fun refresh() {
        if (prefs.clipsJson != lastSaved) load()
        capture()
    }

    fun capture() {
        if (!prefs.clipEnabled) return
        val clip = runCatching { cm.primaryClip }.getOrNull() ?: return
        if (clip.itemCount == 0) return
        val desc = clip.description
        if (Build.VERSION.SDK_INT >= 33 && desc.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return
        if (!desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) && !desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) return
        val t = clip.getItemAt(0).coerceToText(ctx)?.toString()?.trim().orEmpty()
        if (t.isNotEmpty()) add(t.take(2000))
    }

    private fun add(t: String) {
        val ex = items.indexOfFirst { it.text == t }
        if (ex == 0) return
        val pinned = if (ex > 0) items.removeAt(ex).pinned else false
        items.add(0, Clip(t, pinned))
        trim()
        save()
    }

    private fun trim() {
        var n = items.count { !it.pinned }
        while (n > prefs.clipMax.coerceAtLeast(5)) {
            val idx = items.indexOfLast { !it.pinned }
            if (idx < 0) break
            items.removeAt(idx); n--
        }
    }

    fun remove(c: Clip) { items.remove(c); save() }

    fun togglePin(c: Clip) {
        c.pinned = !c.pinned
        if (c.pinned) { items.remove(c); items.add(0, c) }
        save()
    }

    fun clear() {
        items.removeAll { !it.pinned }
        save()
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("t", it.text).put("p", it.pinned)) }
        lastSaved = arr.toString()
        prefs.clipsJson = lastSaved
    }

    private fun load() {
        items.clear()
        runCatching {
            val arr = JSONArray(prefs.clipsJson)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(Clip(o.getString("t"), o.optBoolean("p")))
            }
        }
        lastSaved = prefs.clipsJson
    }
}
