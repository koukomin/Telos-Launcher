package de.mm20.launcher2.search

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Local Voice Assistant & Speech Action parser for Telos Launcher.
 * Parses natural language voice commands off the main thread into executable system actions
 * (e.g. "turn on flashlight", "freeze instagram", "search wikipedia for x").
 */
object TelosVoiceAssistant {

    private const val TAG = "TelosVoiceAssistant"

    sealed class VoiceAction {
        data class SearchWikipedia(val query: String) : VoiceAction()
        data class FreezeApp(val packageNameOrLabel: String) : VoiceAction()
        data class ToggleFlashlight(val enable: Boolean) : VoiceAction()
        data class OpenApp(val appLabel: String) : VoiceAction()
        data class Unknown(val rawText: String) : VoiceAction()
    }

    /**
     * Parses raw speech-to-text input into an actionable [VoiceAction].
     */
    suspend fun parseCommand(rawSpeechInput: String): VoiceAction = withContext(Dispatchers.Default) {
        val clean = rawSpeechInput.trim().lowercase(Locale.ROOT)
        if (clean.isBlank()) return@withContext VoiceAction.Unknown(rawSpeechInput)

        Log.d(TAG, "Parsing voice command: '$clean'")

        return@withContext when {
            clean.contains("flashlight") || clean.contains("φακός") -> {
                val enable = !clean.contains("off") && !clean.contains("κλείσε")
                VoiceAction.ToggleFlashlight(enable)
            }
            clean.startsWith("freeze ") || clean.startsWith("πάγωσε ") -> {
                val target = clean.substringAfter(" ").trim()
                VoiceAction.FreezeApp(target)
            }
            clean.startsWith("search wikipedia for ") || clean.startsWith("βικιπαίδεια ") -> {
                val query = clean.substringAfter("for ").substringAfter("βικιπαίδεια ").trim()
                VoiceAction.SearchWikipedia(query)
            }
            clean.startsWith("open ") || clean.startsWith("άνοιξε ") -> {
                val target = clean.substringAfter(" ").trim()
                VoiceAction.OpenApp(target)
            }
            else -> VoiceAction.Unknown(rawSpeechInput)
        }
    }
}
