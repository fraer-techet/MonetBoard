package dev.monetboard.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.monetboard.data.Prefs

enum class Page { LETTERS, SYM1, SYM2, EMOJI }
enum class Shift { OFF, ON, CAPS }
enum class EnterKind { RETURN, SEND, SEARCH, GO, NEXT, DONE }

class KeyboardState(prefs: Prefs) {
    var langIndex by mutableIntStateOf(prefs.lastLang.coerceIn(0, Layouts.langs.lastIndex))
    var page by mutableStateOf(Page.LETTERS)
    var shift by mutableStateOf(Shift.OFF)
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var altKeys by mutableStateOf<List<String>>(emptyList())
    var enter by mutableStateOf(EnterKind.RETURN)
    var emojiCat by mutableIntStateOf(0)
    val lang: Lang get() = Layouts.langs[langIndex]
}
