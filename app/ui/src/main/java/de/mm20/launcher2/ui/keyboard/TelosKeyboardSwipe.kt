package de.mm20.launcher2.ui.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

data class KeyboardHitKey(val letter: String, val bounds: Rect)

object TelosKeyboardHits {
    private val keys = java.util.concurrent.ConcurrentHashMap<String, Rect>()

    fun remember(letter: String, bounds: Rect) {
        keys[letter.lowercase()] = bounds
    }

    fun snapshot(): List<KeyboardHitKey> = keys.map { KeyboardHitKey(it.key, it.value) }
}

/** Maps a glide path onto letter keys, then scores against the dictionary. */
object TelosKeyboardSwipe {
    fun lettersAlongPath(path: List<Offset>, keys: List<KeyboardHitKey>): String {
        if (path.size < 4 || keys.isEmpty()) return ""
        val visited = ArrayList<String>(8)
        for (point in path) {
            val hit = keys.firstOrNull { it.bounds.contains(point) } ?: continue
            val letter = hit.letter.lowercase()
            if (letter.length != 1 || !letter[0].isLetter()) continue
            if (visited.isEmpty() || visited.last() != letter) visited += letter
        }
        return visited.joinToString("")
    }

    fun recognize(path: List<Offset>, keys: List<KeyboardHitKey>, lang: KeyboardLanguage): String? {
        val raw = lettersAlongPath(path, keys)
        if (raw.length < 2) return null
        val suggestions = TelosKeyboardDictionary.suggestions(raw, lang)
        return suggestions.firstOrNull { it.length >= 2 } ?: raw.takeIf { it.length >= 3 }
    }
}
