package de.mm20.launcher2.ui.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelosKeyboardSwipeTest {
    @Test
    fun glidePathCollectsUniqueLettersInOrder() {
        val keys = listOf(
            KeyboardHitKey("h", Rect(0f, 0f, 10f, 10f)),
            KeyboardHitKey("e", Rect(20f, 0f, 30f, 10f)),
            KeyboardHitKey("l", Rect(40f, 0f, 50f, 10f)),
            KeyboardHitKey("o", Rect(60f, 0f, 70f, 10f)),
        )
        val path = listOf(
            Offset(5f, 5f),
            Offset(25f, 5f),
            Offset(45f, 5f),
            Offset(45f, 6f),
            Offset(65f, 5f),
        )
        assertEquals("helo", TelosKeyboardSwipe.lettersAlongPath(path, keys))
        val word = TelosKeyboardSwipe.recognize(path, keys, KeyboardLanguage.English)
        assertTrue(word == "hello" || word == "helo")
    }
}
