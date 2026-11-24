package de.mm20.launcher2.ui.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo

class TelosKeyboardEditor(private val service: InputMethodService) {

    fun onStartInput(attribute: EditorInfo?) {
        TelosKeyboardBuffer.clear()
        TelosKeyboardState.lastClipboardText = ""
        TelosKeyboardState.privateEditor = false
        val info = attribute ?: return
        TelosKeyboardState.keyboardMode =
            when (info.inputType and EditorInfo.TYPE_MASK_CLASS) {
                EditorInfo.TYPE_CLASS_NUMBER,
                EditorInfo.TYPE_CLASS_PHONE,
                EditorInfo.TYPE_CLASS_DATETIME -> KeyboardMode.Numbers
                else -> KeyboardMode.Letters
            }
        val isPassword =
            (info.inputType and EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT &&
                ((info.inputType and EditorInfo.TYPE_MASK_VARIATION) ==
                    EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                    (info.inputType and EditorInfo.TYPE_MASK_VARIATION) ==
                        EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    (info.inputType and EditorInfo.TYPE_MASK_VARIATION) ==
                        EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD)

        val noLearning = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0

        val numericPassword =
            (info.inputType and EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_NUMBER &&
                (info.inputType and EditorInfo.TYPE_MASK_VARIATION) ==
                    EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
        TelosKeyboardState.privateEditor = isPassword || numericPassword || noLearning
        if (!TelosKeyboardState.privateMode) updateClipboardSnippet()
    }

    fun onFinishInput() {
        TelosKeyboardBuffer.clear()
        TelosKeyboardState.lastClipboardText = ""
        TelosKeyboardState.privateEditor = false
    }

    fun handleKeyTap(char: String) {
        val ic = service.currentInputConnection ?: return
        val textToInsert =
            when {
                TelosKeyboardState.isShifted || TelosKeyboardState.isCapsLock -> char.uppercase()
                else -> char.lowercase()
            }
        ic.commitText(textToInsert, 1)
        if (TelosKeyboardState.privateMode) TelosKeyboardBuffer.clear()
        else TelosKeyboardBuffer.appendChar(textToInsert, TelosKeyboardState.currentLanguage)

        if (TelosKeyboardState.isShifted && !TelosKeyboardState.isCapsLock) {
            TelosKeyboardState.isShifted = false
        }
    }

    fun handleBackspace() {
        val ic = service.currentInputConnection ?: return
        val selectedText = ic.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        TelosKeyboardBuffer.backspace(TelosKeyboardState.currentLanguage)
    }

    fun handleEnter() {
        val ic = service.currentInputConnection ?: return
        TelosKeyboardBuffer.clear()
        val info = service.currentInputEditorInfo
        val action = keyboardEditorAction(info?.actionId ?: 0, info?.imeOptions ?: 0)
        if (action != 0) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    fun handleSpace() {
        service.currentInputConnection?.commitText(" ", 1)
        if (TelosKeyboardBuffer.currentWordBuffer.isNotBlank()) {
            TelosKeyboardDictionary.learnWord(
                TelosKeyboardBuffer.currentWordBuffer,
                TelosKeyboardState.privateMode,
            )
        }
        TelosKeyboardBuffer.clear()
    }

    fun handlePasteText(text: String) {
        service.currentInputConnection?.commitText(text, 1)
    }

    fun toggleIncognito() {
        TelosKeyboardState.isIncognitoMode = !TelosKeyboardState.isIncognitoMode
        if (TelosKeyboardState.privateMode) {
            TelosKeyboardBuffer.clear()
            TelosKeyboardState.lastClipboardText = ""
        }
    }

    fun switchLanguage() {
        TelosKeyboardState.currentLanguage =
            if (TelosKeyboardState.currentLanguage == KeyboardLanguage.Greek)
                KeyboardLanguage.English
            else KeyboardLanguage.Greek
    }

    private fun updateClipboardSnippet() {
        runCatching {
            val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = cm?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0)?.text?.toString()
                if (!text.isNullOrBlank()) {
                    TelosKeyboardState.lastClipboardText = text.take(120)
                }
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun vibrateKey() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = service.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = service.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION") v?.vibrate(15)
            }
        }
    }
}
