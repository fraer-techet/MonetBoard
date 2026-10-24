package dev.monetboard.ime

import android.content.Context
import dev.monetboard.data.Prefs
import kotlin.math.abs

fun matchCase(src: String, cand: String): String = when {
    src.length > 1 && src.any { it.isLetter() } && src.all { !it.isLetter() || it.isUpperCase() } -> cand.uppercase()
    src.firstOrNull()?.isUpperCase() == true -> cand.replaceFirstChar { it.uppercase() }
    else -> cand
}

/**
 * Word lists live in assets/dict/<code>.txt: one lowercase word per line, most frequent first.
 * Swap in bigger lists (30-100k words) and completions + autocorrect get much better; no code change.
 */
class Dictionaries(private val ctx: Context, private val prefs: Prefs) {
    private class WordList(val words: List<String>) { val set = HashSet(words) }

    private val empty = WordList(emptyList())
    private val lists = mapOf("EN" to lazy { load("en") }, "RU" to lazy { load("ru") })

    private fun load(name: String): WordList = runCatching {
        ctx.assets.open("dict/$name.txt").bufferedReader().useLines { seq ->
            WordList(seq.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toList())
        }
    }.getOrDefault(empty)

    fun preload() { Thread { lists.values.forEach { it.value } }.start() }

    private fun list(lang: String) = lists[lang]?.value ?: empty

    fun known(word: String, lang: String): Boolean {
        val w = word.lowercase()
        return w in list(lang).set || w in prefs.learnedSet()
    }

    fun complete(prefix: String, lang: String, n: Int): List<String> {
        val p = prefix.lowercase()
        val out = LinkedHashSet<String>()
        for (w in prefs.learnedSet()) {
            if (w.length > p.length && w.startsWith(p)) { out += w; if (out.size >= n) return out.toList() }
        }
        for (w in list(lang).words) {
            if (w.length > p.length && w.startsWith(p)) { out += w; if (out.size >= n) break }
        }
        return out.toList()
    }

    /** Returns a replacement for a misspelled [word], or null. level 0 = careful, 1 = active. */
    fun correct(word: String, lang: String, level: Int): String? {
        if (word.any { !it.isLetter() }) return null
        val lw = word.lowercase()
        if (lw.length < (if (level == 0) 4 else 3)) return null
        if (known(lw, lang)) return null
        val maxD = if (level == 0) 1 else if (lw.length >= 5) 2 else 1
        var best: String? = null
        var bestD = Int.MAX_VALUE
        for (c in list(lang).words) {
            if (abs(c.length - lw.length) > maxD) continue
            if (level == 0 && c[0] != lw[0]) continue
            val d = osa(lw, c, maxD)
            if (d < bestD) {
                bestD = d; best = c
                if (d == 1) break // list is frequency-ordered, first d==1 hit is the best one
            }
        }
        return if (best != null && bestD <= maxD) matchCase(word, best) else null
    }

    /** Optimal-string-alignment distance (handles transposition), bails out above [max]. */
    private fun osa(a: String, b: String, max: Int): Int {
        val n = a.length
        val m = b.length
        var prev2 = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[m]
    }
}
