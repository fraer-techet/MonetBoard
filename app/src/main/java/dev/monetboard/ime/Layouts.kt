package dev.monetboard.ime

enum class KType { CHAR, SHIFT, BACKSPACE, ENTER, SPACE, SYM1, SYM2, LETTERS, LANG, EMOJI, SPACER }

data class KeyDef(
    val label: String,
    val type: KType = KType.CHAR,
    val weight: Float = 1f,
    val alts: String = "",
)

class Lang(val code: String, val name: String, val rows: List<String>, val alts: Map<Char, String>)

/** Add a language: append a Lang here + drop assets/dict/<code>.txt (+ a subtype in res/xml/method.xml). */
object Layouts {
    val langs = listOf(
        Lang(
            "EN", "English", listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
            mapOf(
                'a' to "àáâäæãåā", 'e' to "èéêëēėę", 'i' to "îïíīįì", 'o' to "ôöòóœøōõ",
                'u' to "ûüùúū", 'c' to "çćč", 'n' to "ñń", 's' to "ßśš", 'y' to "ÿý", 'z' to "žźż",
            ),
        ),
        Lang(
            "RU", "Русский", listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю"),
            mapOf('е' to "ё"),
        ),
    )

    private fun chars(s: String, alts: Map<Char, String> = emptyMap(), digitAlts: Boolean = false): List<KeyDef> =
        s.mapIndexed { i, ch ->
            val digit = if (digitAlts && i < 10) ((i + 1) % 10).toString() else ""
            KeyDef(ch.toString(), KType.CHAR, 1f, digit + (alts[ch] ?: ""))
        }

    private fun spacer(w: Float) = KeyDef("", KType.SPACER, w)

    private fun bottom(first: KeyDef, lang: Lang) = listOf(
        first,
        KeyDef(",", KType.CHAR, 0.9f),
        KeyDef("😊", KType.EMOJI, 0.9f),
        KeyDef(" ", KType.SPACE, 3.3f),
        KeyDef(".", KType.CHAR, 0.9f),
        KeyDef(lang.code, KType.LANG, 0.9f),
        KeyDef("↵", KType.ENTER, 1.3f),
    )

    fun rows(page: Page, lang: Lang, numberRow: Boolean): List<List<KeyDef>> = when (page) {
        Page.LETTERS -> letters(lang, numberRow)
        Page.SYM1 -> symbols1(lang)
        else -> symbols2(lang)
    }

    private fun letters(lang: Lang, numberRow: Boolean): List<List<KeyDef>> {
        val max = lang.rows[0].length.toFloat()
        val r1 = chars(lang.rows[0], lang.alts, digitAlts = !numberRow)
        val s2 = (max - lang.rows[1].length) / 2f
        val r2 = listOf(spacer(s2)) + chars(lang.rows[1], lang.alts) + spacer(s2)
        val s3 = (max - lang.rows[2].length) / 2f
        val r3 = listOf(KeyDef("⇧", KType.SHIFT, s3)) + chars(lang.rows[2], lang.alts) + KeyDef("⌫", KType.BACKSPACE, s3)
        val out = ArrayList<List<KeyDef>>()
        if (numberRow) out += chars("1234567890").map { it.copy(weight = max / 10f) }
        out += listOf(r1, r2, r3, bottom(KeyDef("?123", KType.SYM1, 1.3f), lang))
        return out
    }

    private fun symbols1(lang: Lang) = listOf(
        chars("1234567890"),
        chars("@#\$_&-+()/"),
        listOf(KeyDef("=\\<", KType.SYM2, 1.5f)) + chars("*\"':;!?") + KeyDef("⌫", KType.BACKSPACE, 1.5f),
        bottom(KeyDef("ABC", KType.LETTERS, 1.3f), lang),
    )

    private fun symbols2(lang: Lang) = listOf(
        chars("~`|•√π÷×¶∆"),
        chars("£¢€¥^°={}\\"),
        listOf(KeyDef("?123", KType.SYM1, 1.5f)) + chars("%©®™✓[]") + KeyDef("⌫", KType.BACKSPACE, 1.5f),
        bottom(KeyDef("ABC", KType.LETTERS, 1.3f), lang),
    )
}

object Emojis {
    private fun l(s: String) = s.trim().split(' ').filter { it.isNotEmpty() }
    val cats: List<Pair<String, List<String>>> = listOf(
        "😀" to l("😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😍 🥰 😘 😋 😛 😜 🤪 🤨 🧐 🤓 😎 🥳 😏 😒 😞 😔 😕 🙁 😣 😢 😭 😤 😠 😡 🤯 😳 🥵 🥶 😱 😰 🤗 🤔 🤭 🤫 😶 😐 🙄 😬 😴 🤤 😪 🤢 🤮 🤧 😷"),
        "👍" to l("👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 🤏 👏 🙌 👐 🤲 🙏 🤝 💪 👀 👅 👄 🧠 👶 🧑 👨 👩 🧔 💀 👻 👽 🤖 💩"),
        "❤️" to l("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 ✨ ⭐ 🌟 💫 🔥 💥 💯 ✅ ❌ ❓ ❗ ⚡ 🎉 🎊 🎁 🏆"),
        "🐱" to l("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🦆 🦅 🦉 🐺 🐴 🦄 🐝 🦋 🐌 🐞 🐢 🐍 🐙 🐬 🐳 🦈 🌸 🌹 🌻 🌲 🍀"),
        "🍕" to l("🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🥦 🥕 🌽 🍞 🧀 🍳 🥓 🍔 🍟 🌭 🍕 🌮 🌯 🍣 🍜 🍝 🍰 🎂 🍩 🍪 🍫 🍬 ☕ 🍵 🍺 🍷"),
        "⚽" to l("⚽ 🏀 🏈 ⚾ 🎾 🏐 🎱 🏓 🥊 🎯 🎮 🎲 🎸 🎹 🥁 🎤 🎧 🎬 🎨 📚 🏋️ 🚴 🏊 🧘 ⛷️"),
        "🚗" to l("🚗 🚕 🚌 🚎 🏎️ 🚓 🚑 🚒 🚲 🛵 🏍️ 🚂 ✈️ 🚀 🚁 ⛵ 🚢 🏠 🏢 🏰 🗼 🌍 🌙 ☀️ ⛅ 🌧️ ❄️ 🌈"),
        "💡" to l("📱 💻 ⌨️ 🖥️ 📷 🔋 🔌 💡 🔦 📞 ⏰ 💰 💳 🔑 🔒 🔨 🔧 ⚙️ 🧲 📦 ✉️ 📝 📌 📎 🔗 🚫 ⚠️ ♻️"),
    )
}
