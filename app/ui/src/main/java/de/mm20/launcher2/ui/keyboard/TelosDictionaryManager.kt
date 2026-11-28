package de.mm20.launcher2.ui.keyboard

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

object TelosDictionaryManager {
    private const val BASE_URL = "https://cdn.jsdelivr.net/gh/wooorm/dictionaries@main/dictionaries"

    private fun getLangCode(lang: KeyboardLanguage): String {
        return when (lang) {
            KeyboardLanguage.Greek -> "el"
            KeyboardLanguage.English -> "en"
        }
    }

    fun getDictionaryFile(context: Context, lang: KeyboardLanguage): File {
        val code = getLangCode(lang)
        val dir = File(context.filesDir, "dictionaries")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$code.txt")
    }

    suspend fun ensureDictionary(context: Context, lang: KeyboardLanguage) = withContext(Dispatchers.IO) {
        val file = getDictionaryFile(context, lang)
        if (file.exists() && file.length() > 0) {
            TelosKeyboardDictionary.loadDownloadedDictionary(lang, file)
            return@withContext
        }
        val code = getLangCode(lang)
        val dicUrl = "$BASE_URL/$code/index.dic"
        runCatching {
            val content = URL(dicUrl).readText(Charsets.UTF_8)
            val lines = content.lines()
            val words = java.util.ArrayList<String>()
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                val word = line.split('/')[0].trim()
                if (word.length in 2..28 && !word.startsWith("#") && !words.contains(word)) {
                    words.add(word)
                }
                i++
            }
            val sb = StringBuilder()
            var j = 0
            while (j < words.size) {
                sb.append(words[j]).append("\n")
                j++
            }
            file.writeText(sb.toString(), Charsets.UTF_8)
            TelosKeyboardDictionary.loadDownloadedDictionary(lang, file)
        }
    }

    suspend fun removeDictionary(context: Context, lang: KeyboardLanguage) = withContext(Dispatchers.IO) {
        val file = getDictionaryFile(context, lang)
        if (file.exists()) {
            file.delete()
        }
        TelosKeyboardDictionary.unloadDownloadedDictionary(lang)
    }
}
