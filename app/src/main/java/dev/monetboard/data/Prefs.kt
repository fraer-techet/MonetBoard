package dev.monetboard.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.monetboard.ai.AiConfig
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

const val DEFAULT_SYSTEM_PROMPT =
    "You are an AI writing assistant inside a phone keyboard. Output ONLY the final text the user will insert: " +
        "no preface, no explanations, no surrounding quotes, no markdown unless asked. " +
        "Answer in the language of the user's request unless told otherwise. Be concise."

/**
 * SharedPreferences wrapper. Every getter touches a snapshot counter, so any composable
 * (settings screen or the live keyboard) recomposes the moment a value changes.
 */
class Prefs private constructor(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("monetboard", Context.MODE_PRIVATE)
    private var version by mutableIntStateOf(0)
    private fun touch() { check(version >= 0) }

    private fun bool(key: String, def: Boolean): ReadWriteProperty<Any?, Boolean> =
        object : ReadWriteProperty<Any?, Boolean> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean { touch(); return sp.getBoolean(key, def) }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { sp.edit().putBoolean(key, value).apply(); version++ }
        }

    private fun int(key: String, def: Int): ReadWriteProperty<Any?, Int> =
        object : ReadWriteProperty<Any?, Int> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Int { touch(); return sp.getInt(key, def) }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { sp.edit().putInt(key, value).apply(); version++ }
        }

    private fun float(key: String, def: Float): ReadWriteProperty<Any?, Float> =
        object : ReadWriteProperty<Any?, Float> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Float { touch(); return sp.getFloat(key, def) }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) { sp.edit().putFloat(key, value).apply(); version++ }
        }

    private fun str(key: String, def: String): ReadWriteProperty<Any?, String> =
        object : ReadWriteProperty<Any?, String> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): String { touch(); return sp.getString(key, def) ?: def }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) { sp.edit().putString(key, value).apply(); version++ }
        }

    // --- appearance
    var themeMode by int("themeMode", 0)          // 0 system, 1 light, 2 dark
    var colorSource by int("colorSource", 0)      // 0 Monet (wallpaper), 1 custom accent
    var accent by int("accent", 0)
    var amoled by bool("amoled", false)

    // --- keyboard
    var keyHeight by int("keyHeight", 52)
    var keyGap by int("keyGap", 4)
    var keyRadius by int("keyRadius", 16)
    var numberRow by bool("numberRow", false)
    var haptics by bool("haptics", true)
    var sound by bool("sound", false)
    var longPressMs by int("longPressMs", 350)
    var swipeCursor by bool("swipeCursor", true)
    var lastLang by int("lastLang", 0)

    // --- text input
    var suggestions by bool("suggestions", true)
    var autocorrect by bool("autocorrect", false)  // off by default: bundled word lists are starter-size
    var autocorrectLevel by int("autocorrectLevel", 0) // 0 careful, 1 active
    var punctSpace by bool("punctSpace", true)
    var autoCap by bool("autoCap", true)
    var doubleSpacePeriod by bool("doubleSpacePeriod", true)

    // --- ai
    var aiEnabled by bool("aiEnabled", true)
    var aiBaseUrl by str("aiBaseUrl", "https://api.openai.com/v1")
    var aiApiKey by str("aiApiKey", "")
    var aiModel by str("aiModel", "gpt-4o-mini")
    var aiTemperature by float("aiTemperature", 0.6f)
    var aiTarget by int("aiTarget", 0)             // translate target: 0 auto, 1 ru, 2 en
    var aiSystemPrompt by str("aiSystemPrompt", DEFAULT_SYSTEM_PROMPT)

    fun aiConfig() = AiConfig(aiBaseUrl, aiApiKey, aiModel, aiTemperature, aiSystemPrompt)

    // --- learned words (autocorrect undo / manual)
    fun learnedSet(): Set<String> = sp.getStringSet("learned", emptySet()) ?: emptySet()
    fun learn(word: String) {
        val n = HashSet(learnedSet())
        if (n.add(word)) sp.edit().putStringSet("learned", n).apply()
    }
    fun clearLearned() { sp.edit().remove("learned").apply(); version++ }
    fun learnedCount(): Int { touch(); return learnedSet().size }

    companion object {
        @Volatile private var inst: Prefs? = null
        fun get(ctx: Context): Prefs =
            inst ?: synchronized(this) { inst ?: Prefs(ctx).also { inst = it } }
    }
}
