package de.mm20.launcher2.ui.tools

import com.anyascii.AnyAscii
import java.text.Normalizer
import java.util.Locale

/** Greek radio alphabet, otherwise offline romanization into international Morse. */
object MorseCodec {
    private val latin = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toList().zip(
        listOf(".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..", ".---", "-.-", ".-..", "--", "-.", "---", ".--.", "--.-", ".-.", "...", "-", "..-", "...-", ".--", "-..-", "-.--", "--..", "-----", ".----", "..---", "...--", "....-", ".....", "-....", "--...", "---..", "----.")
    ).toMap() + mapOf('.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '/' to "-..-.", '-' to "-....-", '@' to ".--.-.", ':' to "---...", '=' to "-...-", '+' to ".-.-.", '\'' to ".----.", '"' to ".-..-.", '(' to "-.--.", ')' to "-.--.-", '!' to "-.-.--")
    private val greek = "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩ".toList().zip(
        listOf(".-", "-...", "--.", "-..", ".", "--..", "....", "-.-.", "..", "-.-", ".-..", "--", "-.", "-..-", "---", ".--.", ".-.", "...", "-", "-.--", "..-.", "----", "--.-", ".--")
    ).toMap()
    private val silentLetters = setOf("ъ", "ь")
    data class Encoded(val code: String, val readable: String, val romanized: Boolean, val unsupported: Boolean)
    data class Pulse(val on: Boolean, val units: Int)

    fun encode(input: String): Encoded {
        val tokens = mutableListOf<String>()
        val readable = StringBuilder()
        var romanized = false
        var unsupported = false
        val normalized = input.take(input.offsetByCodePoints(0, input.codePointCount(0, input.length).coerceAtMost(2000)))
        var offset = 0
        while (offset < normalized.length) {
            val cp = normalized.codePointAt(offset)
            offset += Character.charCount(cp)
            val source = String(Character.toChars(cp))
            val upper = Normalizer.normalize(source.uppercase(Locale.ROOT), Normalizer.Form.NFD).take(1)
            val direct = upper.singleOrNull()?.let { greek[it] }
            if (direct != null) {
                tokens.add(direct); readable.append(source); continue
            }
            if (Character.getType(cp) == Character.FORMAT.toInt()) continue
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                if (tokens.isNotEmpty() && tokens.last() != "/") tokens.add("/")
                readable.append(' '); continue
            }
            val combining = Character.getType(cp) == Character.NON_SPACING_MARK.toInt() || Character.getType(cp) == Character.COMBINING_SPACING_MARK.toInt()
            if (!Character.isLetterOrDigit(cp) && !combining && cp > 127) { unsupported = true; continue }
            // AnyAscii treats unvocalized alef as silent; keep it explicit for a user-entered signal.
            val ascii = (if (cp == 0x0627 || cp == 0x0623 || cp == 0x0625 || cp == 0x0622 || cp == 0x0671) "A"
                else AnyAscii.transliterate(source)).replace('`', '\'').uppercase(Locale.ROOT)
            if (cp > 127) romanized = true
            if (ascii.isEmpty() && !combining && source.lowercase(Locale.ROOT) !in silentLetters) unsupported = true
            readable.append(ascii)
            for (ch in ascii) {
                if (ch.isWhitespace()) {
                    if (tokens.isNotEmpty() && tokens.last() != "/") tokens.add("/")
                } else {
                    val code = latin[ch]
                    if (code == null) unsupported = true else tokens.add(code)
                }
            }
        }
        while (tokens.lastOrNull() == "/") tokens.removeAt(tokens.lastIndex)
        return Encoded(tokens.joinToString(" "), readable.toString(), romanized, unsupported)
    }

    /** ITU 1/3-unit marks, 1/3/7-unit gaps, without accumulating extra gaps. */
    fun pulses(code: String): List<Pulse> = buildList {
        val words = code.split(" / ").filter { it.isNotBlank() }
        words.forEachIndexed { wi, word ->
            if (wi > 0) add(Pulse(false, 7))
            word.split(' ').filter { it.isNotBlank() }.forEachIndexed { li, letter ->
                if (li > 0) add(Pulse(false, 3))
                letter.forEachIndexed { si, symbol ->
                    if (si > 0) add(Pulse(false, 1))
                    add(Pulse(true, if (symbol == '-') 3 else 1))
                }
            }
        }
    }
}
