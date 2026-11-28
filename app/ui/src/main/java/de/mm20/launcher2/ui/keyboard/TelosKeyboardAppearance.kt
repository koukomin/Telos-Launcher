package de.mm20.launcher2.ui.keyboard

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** User-tunable IME look. SharedPreferences so the IME and settings stay in sync. */
object TelosKeyboardAppearance {
    const val PREFS = "telos_keyboard"
    const val DEFAULT_ACCENT = 0xFF00F0FFL
    val accentSwatches = longArrayOf(
        0xFF00F0FFL, 0xFF00E676L, 0xFFFF007FL, 0xFFFF9100L,
        0xFFFFFFFFL, 0xFF82B1FFL, 0xFFFF5252L, 0xFFD500F9L,
    )

    var accentArgb by mutableLongStateOf(DEFAULT_ACCENT)
    var keyboardScale by mutableFloatStateOf(1f)
    var keyHeightDp by mutableFloatStateOf(52f)
    var keyFontSp by mutableFloatStateOf(18f)
    var showKeyBorders by mutableStateOf(false)
    var showNumberRow by mutableStateOf(false)
    var showSymbolHints by mutableStateOf(true)

    val accent: Color get() = Color(accentArgb.toInt())

    fun scaledKeyHeightDp(): Float = (keyHeightDp * keyboardScale).coerceIn(38f, 84f)
    fun scaledFontSp(): Float = (keyFontSp * keyboardScale).coerceIn(12f, 28f)

    fun load(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        accentArgb = p.getLong("accent", DEFAULT_ACCENT)
        keyboardScale = p.getFloat("scale", 1f).coerceIn(0.75f, 1.4f)
        keyHeightDp = p.getFloat("key_height", 52f).coerceIn(38f, 76f)
        keyFontSp = p.getFloat("key_font", 18f).coerceIn(12f, 28f)
        showKeyBorders = p.getBoolean("key_borders", false)
        showNumberRow = p.getBoolean("number_row", false)
        showSymbolHints = p.getBoolean("symbol_hints", true)
    }

    fun save(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("accent", accentArgb)
            .putFloat("scale", keyboardScale)
            .putFloat("key_height", keyHeightDp)
            .putFloat("key_font", keyFontSp)
            .putBoolean("key_borders", showKeyBorders)
            .putBoolean("number_row", showNumberRow)
            .putBoolean("symbol_hints", showSymbolHints)
            .apply()
    }
}
