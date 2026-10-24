package dev.monetboard.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.monetboard.data.Prefs

enum class Page { LETTERS, SYM1, SYM2 }
enum class Panel { KEYS, TOOLS, CLIP, TRANSLATE, VOICE, EMOJI }
enum class Shift { OFF, ON, CAPS }
enum class EnterKind { RETURN, SEND, SEARCH, GO, NEXT, DONE }

class KeyboardState(prefs: Prefs) {
    var langIndex by mutableIntStateOf(prefs.lastLang.coerceIn(0, Layouts.langs.lastIndex))
    var page by mutableStateOf(Page.LETTERS)
    var panel by mutableStateOf(Panel.KEYS)
    var shift by mutableStateOf(Shift.OFF)
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var altKeys by mutableStateOf<List<String>>(emptyList())
    var enter by mutableStateOf(EnterKind.RETURN)
    var emojiCat by mutableIntStateOf(0)

    var voiceListening by mutableStateOf(false)
    var voiceText by mutableStateOf("")
    var voiceLevel by mutableFloatStateOf(0f)
    var voiceError by mutableStateOf<String?>(null)
    var voiceNeedsPermission by mutableStateOf(false)

    val lang: Lang get() = Layouts.langs[langIndex]
}
