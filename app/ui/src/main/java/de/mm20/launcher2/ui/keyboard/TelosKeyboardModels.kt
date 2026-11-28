package de.mm20.launcher2.ui.keyboard

import android.net.Uri
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    TextEditing,
    Numpad,
    Translate,
}

enum class OneHandedSide {
    Off,
    Left,
    Right,
}

data class ClipboardItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String? = null,
    val imageUri: Uri? = null,
    val mimeType: String = "image/png",
    val timestamp: Long = System.currentTimeMillis()
)

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

    var clipboardItems by mutableStateOf<List<ClipboardItem>>(kotlin.collections.emptyList())
    var lastClipboardItem by mutableStateOf<ClipboardItem?>(null)

    var imeAction by mutableIntStateOf(EditorInfo.IME_ACTION_UNSPECIFIED)
    var activeKeyPreview by mutableStateOf<String?>(null)

    var oneHandedSide by mutableStateOf(OneHandedSide.Off)
    var emojiSearchQuery by mutableStateOf("")
    var translateSourceText by mutableStateOf("")
    var translateTargetText by mutableStateOf("")
    var isListeningVoice by mutableStateOf(false)
}
