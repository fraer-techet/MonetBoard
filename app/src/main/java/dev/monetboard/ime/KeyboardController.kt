package dev.monetboard.ime

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.monetboard.data.Prefs
import dev.monetboard.ui.MainActivity
import dev.monetboard.ui.PermissionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class Source(val text: String, val selection: Boolean, val before: Int, val after: Int)

/** All typing logic. UI only calls into this; state lives in [state] / [ai] / [translator] (Compose snapshot state). */
class KeyboardController(val service: InputMethodService, val prefs: Prefs) {
    val state = KeyboardState(prefs)
    val ai = AiAgent(this)
    val translator = TranslatorAgent(this)
    val clipboard = ClipboardStore(service.applicationContext, prefs)
    val voice = VoiceInput(service, this)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dict = Dictionaries(service.applicationContext, prefs).also { it.preload() }

    var hostView: View? = null

    private var info: EditorInfo? = null
    private var noSuggest = false
    private var autoShift = false
    private var lastShiftAt = 0L
    private var lastSpaceAt = 0L
    private var lastCorrection: Correction? = null

    private class Correction(val orig: String, val corrected: String, val suffix: String)

    private val ic: InputConnection? get() = service.currentInputConnection
    private val audio by lazy { service.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private val passwordVariations = setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
    )
    private val plainVariations = passwordVariations + setOf(
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_URI,
    )

    /** Keys feed an in-keyboard text line (AI prompt / translator input) instead of the host field. */
    private val bufferActive: Boolean get() = ai.open || state.panel == Panel.TRANSLATE
    private var buffer: String
        get() = if (ai.open) ai.prompt else translator.input
        set(v) { if (ai.open) ai.prompt = v else translator.input = v }

    init { clipboard.start() }

    // ---------------------------------------------------------------- lifecycle

    fun onStartInput(ei: EditorInfo, restarting: Boolean) {
        info = ei
        val cls = ei.inputType and InputType.TYPE_MASK_CLASS
        val variation = ei.inputType and InputType.TYPE_MASK_VARIATION
        val isText = cls == InputType.TYPE_CLASS_TEXT
        noSuggest = !isText || variation in plainVariations ||
            (ei.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        ai.allowed = ei.inputType != 0 &&
            !(isText && variation in passwordVariations) &&
            !(cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)

        state.enter = if ((ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) EnterKind.RETURN
        else when (ei.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEND -> EnterKind.SEND
            EditorInfo.IME_ACTION_SEARCH -> EnterKind.SEARCH
            EditorInfo.IME_ACTION_GO -> EnterKind.GO
            EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_PREVIOUS -> EnterKind.NEXT
            EditorInfo.IME_ACTION_DONE -> EnterKind.DONE
            else -> EnterKind.RETURN
        }

        if (!restarting) {
            state.page = when (cls) {
                InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> Page.SYM1
                else -> Page.LETTERS
            }
            state.shift = Shift.OFF
            autoShift = false
            state.altKeys = emptyList()
            lastCorrection = null
            resetPanels()
        }
        afterEdit()
    }

    private fun resetPanels() {
        voice.stop()
        translator.close()
        ai.close()
        state.panel = Panel.KEYS
    }

    fun onFinish() { resetPanels() }

    fun destroy() {
        voice.stop()
        clipboard.stop()
        scope.cancel()
    }

    fun onSelectionChanged() { afterEdit() }

    // ---------------------------------------------------------------- panels

    fun openPanel(p: Panel) {
        if (state.panel == Panel.VOICE && p != Panel.VOICE) voice.stop()
        if (ai.open) ai.close()
        if (p != Panel.TRANSLATE) translator.close()
        state.altKeys = emptyList()
        state.panel = p
        when (p) {
            Panel.VOICE -> voice.start()
            Panel.CLIP -> clipboard.refresh()
            Panel.KEYS -> afterEdit()
            else -> {}
        }
    }

    /** The round "X" on the left: closes AI first, otherwise returns to the keys. */
    fun closeAny() { if (ai.open) ai.close() else openPanel(Panel.KEYS) }

    fun openAi() {
        openPanel(Panel.KEYS)
        if (ai.allowed) ai.open = true
    }

    /** One tap: AI-fix the text that's already in the field (or the selection), result shown as a card. */
    fun openAiFix() {
        openPanel(Panel.KEYS)
        if (!ai.allowed) return
        ai.open = true
        ai.runAction(AiAction.FIX)
    }

    fun toggleMic() { openPanel(if (state.panel == Panel.VOICE) Panel.KEYS else Panel.VOICE) }

    // ---------------------------------------------------------------- typing

    fun onChar(raw: String) {
        val s = if (state.page == Page.LETTERS && state.shift != Shift.OFF) raw.uppercase() else raw
        state.altKeys = emptyList()
        if (bufferActive) { buffer += s; consumeShift(); return }
        val ic = ic ?: return
        lastSpaceAt = 0
        lastCorrection = null
        if (s.length == 1 && s[0] in ",.!?;:") punct(ic, s) else ic.commitText(s, 1)
        consumeShift()
        afterEdit()
    }

    /** Raw insert (emoji, clipboard item): no autocorrect, no shift handling. */
    fun typeRaw(s: String) {
        state.altKeys = emptyList()
        if (bufferActive) { buffer += s; return }
        lastCorrection = null
        ic?.commitText(s, 1)
        afterEdit()
    }

    fun insertClip(text: String) {
        typeRaw(text)
        openPanel(Panel.KEYS)
    }

    fun commitVoice(t: String) {
        if (bufferActive) {
            val b = buffer
            buffer = b + (if (b.isNotEmpty() && !b.endsWith(" ")) " " else "") + t
            return
        }
        val ic = ic ?: return
        val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        val lead = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        lastCorrection = null
        ic.commitText("$lead$t ", 1)
    }

    private fun punct(ic: InputConnection, p: String) {
        var trailing = p
        if (prefs.punctSpace) {
            // "word ," -> "word, "  (space moves behind the mark)
            val b = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (b.length == 2 && b[1] == ' ' && b[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0)
                trailing = "$p "
            }
        }
        commitBoundary(ic, trailing)
    }

    /** Commits [trailing] after the current word, autocorrecting the word first when enabled. */
    private fun commitBoundary(ic: InputConnection, trailing: String) {
        val before = ic.getTextBeforeCursor(48, 0) ?: ""
        val word = currentWord(before)
        val corr = if (word.isNotEmpty() && prefs.autocorrect && !noSuggest)
            dict.correct(word, state.lang.code, prefs.autocorrectLevel) else null
        if (corr != null) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(word.length, 0)
            ic.commitText(corr + trailing, 1)
            ic.endBatchEdit()
            lastCorrection = Correction(word, corr, trailing)
        } else {
            ic.commitText(trailing, 1)
        }
    }

    fun onSpace() {
        state.altKeys = emptyList()
        if (bufferActive) { buffer += " "; return }
        val ic = ic ?: return
        lastCorrection = null
        val now = SystemClock.uptimeMillis()
        if (prefs.doubleSpacePeriod && !noSuggest && now - lastSpaceAt < 450) {
            val b = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (b.length == 2 && b[1] == ' ' && b[0].isLetterOrDigit()) {
                ic.beginBatchEdit(); ic.deleteSurroundingText(1, 0); ic.commitText(". ", 1); ic.endBatchEdit()
                lastSpaceAt = 0
                afterEdit()
                return
            }
        }
        commitBoundary(ic, " ")
        lastSpaceAt = now
        afterEdit()
    }

    fun onBackspace(repeat: Boolean = false) {
        state.altKeys = emptyList()
        if (bufferActive) {
            val p = buffer
            if (p.isNotEmpty()) buffer = p.dropLast(if (p.length >= 2 && Character.isLowSurrogate(p.last())) 2 else 1)
            return
        }
        val ic = ic ?: return
        lastSpaceAt = 0
        val lc = lastCorrection
        lastCorrection = null
        if (lc != null && !repeat) {
            // backspace right after an autocorrect = undo it and remember the original word
            val tail = lc.corrected + lc.suffix
            if (ic.getTextBeforeCursor(tail.length, 0)?.toString() == tail) {
                ic.beginBatchEdit(); ic.deleteSurroundingText(tail.length, 0); ic.commitText(lc.orig, 1); ic.endBatchEdit()
                prefs.learn(lc.orig.lowercase())
                afterEdit()
                return
            }
        }
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) ic.commitText("", 1) else service.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        afterEdit()
    }

    fun onEnter() {
        state.altKeys = emptyList()
        if (ai.open) { ai.send(); return }
        if (state.panel == Panel.TRANSLATE) { translator.run(); return }
        val ic = ic ?: return
        val ei = info ?: return
        lastCorrection = null
        val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
        val noAction = (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED
        if (noAction) service.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) else ic.performEditorAction(action)
        afterEdit()
    }

    fun onSuggestion(s: String) {
        if (bufferActive) return
        val ic = ic ?: return
        val w = currentWord(ic.getTextBeforeCursor(48, 0) ?: "")
        ic.beginBatchEdit()
        if (w.isNotEmpty()) ic.deleteSurroundingText(w.length, 0)
        ic.commitText(matchCase(w, s) + " ", 1)
        ic.endBatchEdit()
        lastCorrection = null
        lastSpaceAt = SystemClock.uptimeMillis()
        afterEdit()
    }

    // long-press alternates: the base char was already typed on press, so swap it
    fun onAltLong(alts: String, upper: Boolean) {
        val list = alts.map { if (upper) it.toString().uppercase() else it.toString() }
        if (list.size == 1) replaceLast(list[0]) else state.altKeys = list
    }

    fun onAltPick(s: String) {
        replaceLast(s)
        state.altKeys = emptyList()
    }

    private fun replaceLast(s: String) {
        if (bufferActive) { buffer = buffer.dropLast(1) + s; return }
        val ic = ic ?: return
        ic.beginBatchEdit(); ic.deleteSurroundingText(1, 0); ic.commitText(s, 1); ic.endBatchEdit()
        lastCorrection = null
        afterEdit()
    }

    fun moveCursor(dir: Int) {
        if (bufferActive) return
        service.sendDownUpKeyEvents(if (dir < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
        afterEdit()
    }

    // ---------------------------------------------------------------- shift / pages / lang

    fun onShift() {
        val now = SystemClock.uptimeMillis()
        autoShift = false
        state.shift = when {
            state.shift == Shift.CAPS -> Shift.OFF
            state.shift == Shift.ON && now - lastShiftAt < 350 -> Shift.CAPS
            state.shift == Shift.ON -> Shift.OFF
            else -> Shift.ON
        }
        lastShiftAt = now
    }

    private fun consumeShift() {
        if (state.shift == Shift.ON) state.shift = Shift.OFF
        autoShift = false
    }

    fun setPage(p: Page) {
        state.page = p
        state.altKeys = emptyList()
    }

    fun setLang(i: Int) {
        val n = i.coerceIn(0, Layouts.langs.lastIndex)
        if (n == state.langIndex) return
        state.langIndex = n
        prefs.lastLang = n
        afterEdit()
    }

    // ---------------------------------------------------------------- state refresh

    fun afterEdit() {
        if (bufferActive) return
        val ic = ic ?: return
        val ei = info ?: return
        if (prefs.autoCap && state.page == Page.LETTERS && state.shift != Shift.CAPS) {
            val want = ic.getCursorCapsMode(ei.inputType) != 0
            if (want && state.shift == Shift.OFF) { state.shift = Shift.ON; autoShift = true }
            else if (!want && autoShift && state.shift == Shift.ON) { state.shift = Shift.OFF; autoShift = false }
        }
        val w = currentWord(ic.getTextBeforeCursor(48, 0) ?: "")
        state.suggestions = if (!prefs.suggestions || noSuggest || w.isEmpty()) emptyList() else {
            val c = dict.complete(w, state.lang.code, 3).map { matchCase(w, it) }
            if (c.isEmpty()) listOf(w) else c
        }
    }

    private fun currentWord(before: CharSequence): String {
        var i = before.length
        while (i > 0) {
            val ch = before[i - 1]
            if (ch.isLetter() || ch == '\'' || ch == '’') i-- else break
        }
        return before.subSequence(i, before.length).toString().trimStart('\'', '’')
    }

    // ---------------------------------------------------------------- field IO (AI / translator)

    fun readSource(): Source {
        val ic = ic ?: return Source("", false, 0, 0)
        val sel = ic.getSelectedText(0)?.toString()
        if (!sel.isNullOrEmpty()) return Source(sel, true, 0, 0)
        val b = ic.getTextBeforeCursor(6000, 0)?.toString().orEmpty()
        val a = ic.getTextAfterCursor(6000, 0)?.toString().orEmpty()
        return Source(b + a, false, b.length, a.length)
    }

    fun replaceSource(src: Source, text: String) {
        val ic = ic ?: return
        ic.beginBatchEdit()
        if (!src.selection) ic.deleteSurroundingText(src.before, src.after)
        ic.commitText(text, 1)
        ic.endBatchEdit()
    }

    fun insertText(text: String) { ic?.commitText(text, 1) }

    // ---------------------------------------------------------------- misc

    @Suppress("DEPRECATION")
    fun feedback() {
        if (prefs.haptics) {
            hostView?.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
        }
        if (prefs.sound) audio.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.5f)
    }

    fun openSettings() {
        service.startActivity(Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openPermission() {
        service.startActivity(Intent(service, PermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
