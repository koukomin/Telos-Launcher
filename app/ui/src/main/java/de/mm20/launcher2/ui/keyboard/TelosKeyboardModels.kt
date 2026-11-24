package de.mm20.launcher2.ui.keyboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class KeyboardLanguage {
    Greek,
    English,
}

enum class KeyboardMode {
    Letters,
    Numbers,
    Symbols,
    Emojis,
    Clipboard,
}

object TelosKeyboardState {
    var isIncognitoMode by mutableStateOf(false)
    internal var privateEditor by mutableStateOf(false)
    val privateMode: Boolean
        get() = isIncognitoMode || privateEditor

    var currentLanguage by mutableStateOf(KeyboardLanguage.Greek)
    var keyboardMode by mutableStateOf(KeyboardMode.Letters)
    var isShifted by mutableStateOf(false)
    var isCapsLock by mutableStateOf(false)
    var lastClipboardText by mutableStateOf("")
}

