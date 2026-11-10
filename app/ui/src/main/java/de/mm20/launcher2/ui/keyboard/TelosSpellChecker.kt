package de.mm20.launcher2.ui.keyboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.mm20.launcher2.search.GreekFold
import java.util.Locale

object TelosKeyboardDictionary {
    private val greekWords = listOf(
        "καλημέρα", "καλησπέρα", "καληνύχτα", "γεια", "ευχαριστώ", "παρακαλώ", "ναι", "όχι",
        "σήμερα", "αύριο", "χθες", "σπίτι", "εργασία", "δουλειά", "τηλέφωνο", "μήνυμα", "κλήση",
        "σύστημα", "εφαρμογή", "ελληνικά", "τηλεόραση", "ραδιόφωνο", "μουσική", "βίντεο", "αρχείο",
        "σημείωση", "ημερολόγιο", "λήψεις", "δίκτυο", "καιρός", "ρολόι", "φακός", "υπολογιστής",
        "τι κάνεις", "όλα καλά", "εντάξει", "συγγνώμη", "παρακαλώ πολύ", "πολύ καλά", "καλή συνέχεια"
    )

    private val englishWords = listOf(
        "hello", "thanks", "thank you", "please", "yes", "no", "today", "tomorrow", "yesterday",
        "home", "work", "phone", "message", "call", "system", "app", "application", "keyboard",
        "television", "radio", "music", "video", "file", "note", "calendar", "downloads", "network",
        "weather", "clock", "flashlight", "calculator", "how are you", "sounds good", "okay", "sorry"
    )

    private val learnedWords = mutableSetOf<String>()

    fun suggestions(input: String, lang: KeyboardLanguage): List<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()
        val foldedInput = GreekFold.fold(trimmed.lowercase(Locale.ROOT))

        val pool = when (lang) {
            KeyboardLanguage.Greek -> greekWords
            KeyboardLanguage.English -> englishWords
        } + learnedWords.toList()

        val exactPrefixes = pool.filter {
            GreekFold.fold(it.lowercase(Locale.ROOT)).startsWith(foldedInput)
        }

        val fuzzyMatches = if (exactPrefixes.size < 3) {
            pool.filter {
                GreekFold.fold(it.lowercase(Locale.ROOT)).contains(foldedInput) && it !in exactPrefixes
            }
        } else emptyList()

        return (exactPrefixes + fuzzyMatches).take(4)
    }

    fun learnWord(word: String, isIncognito: Boolean) {
        if (isIncognito || word.isBlank() || word.length < 3) return
        learnedWords.add(word.trim())
    }
}

object TelosKeyboardBuffer {
    var currentWordBuffer by mutableStateOf("")
    val activeSuggestions = mutableStateListOf<String>()

    fun appendChar(char: String, lang: KeyboardLanguage) {
        currentWordBuffer += char
        updateSuggestions(lang)
    }

    fun backspace(lang: KeyboardLanguage) {
        if (currentWordBuffer.isNotEmpty()) {
            currentWordBuffer = currentWordBuffer.dropLast(1)
            updateSuggestions(lang)
        }
    }

    fun clear() {
        currentWordBuffer = ""
        activeSuggestions.clear()
    }

    fun updateSuggestions(lang: KeyboardLanguage) {
        activeSuggestions.clear()
        if (currentWordBuffer.isNotBlank()) {
            activeSuggestions.addAll(TelosKeyboardDictionary.suggestions(currentWordBuffer, lang))
        }
    }
}
