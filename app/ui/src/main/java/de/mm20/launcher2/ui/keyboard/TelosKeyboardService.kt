package de.mm20.launcher2.ui.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import de.mm20.launcher2.ui.theme.LauncherTheme

class TelosKeyboardService :
    InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val mLifecycleRegistry = LifecycleRegistry(this)
    private val mSavedStateRegistryController = SavedStateRegistryController.create(this)
    private val mViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = mLifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = mSavedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = mViewModelStore

    private var composeView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        // Compose + SavedStateRegistry require attach before restore; skipping this
        // crashes the IME the first time the system binds Telos Keyboard.
        mSavedStateRegistryController.performAttach()
        mSavedStateRegistryController.performRestore(null)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        TelosKeyboardAppearance.load(applicationContext)
        TelosSystemSuggestions.extra = { input, _ ->
            TelosSystemSuggestions.fromUserDictionary(applicationContext, input)
        }
    }

    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        val nav =
            androidx.core.view.ViewCompat.getRootWindowInsets(window?.window?.decorView ?: return)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                ?.bottom ?: 0
        outInsets.contentTopInsets = outInsets.visibleTopInsets
        outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_CONTENT
        if (nav > 0 && outInsets.contentTopInsets > nav) {
            // Keep the reported IME height above the nav bar; keys already pad with navigationBarsPadding.
        }
    }

    override fun onCreateInputView(): View {
        // onCreateInputView can be called again after a config change; LifecycleRegistry
        // throws if START/RESUME are re-entered from an already-resumed state.
        advanceLifecycleToResumed()
        // The window recomposer looks up owners from the IME decor, not only its input view.
        window?.window?.decorView?.let(::installViewOwners)
        composeView?.let { old ->
            old.disposeComposition()
            (old.parent as? ViewGroup)?.removeView(old)
        }
        window?.window?.let { win ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
        }
        val view =
            ComposeView(this).also { host ->
                installViewOwners(host)
                host.setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
                val keyboardContent: @Composable () -> Unit = {
                    TelosKeyboardView(
                        onKeyTap = ::handleKeyTap,
                        onBackspace = ::handleBackspace,
                        onEnter = ::handleEnter,
                        onSpace = ::handleSpace,
                        onToggleIncognito = ::toggleIncognito,
                        onSwitchLanguage = ::switchLanguage,
                        onPasteText = ::handlePasteText,
                        onPasteClipboardItem = ::handlePasteClipboardItem,
                        onStartVoiceTyping = ::startVoiceTyping,
                        onTextEditingAction = ::handleTextEditingAction,
                        onVibrate = ::vibrateKey,
                    )
                }
                host.setContent {
                    LauncherTheme(
                        wallpaperGlass = false,
                        loadingContent = {
                            MaterialTheme(
                                colorScheme =
                                    if (androidx.compose.foundation.isSystemInDarkTheme())
                                        darkColorScheme()
                                    else lightColorScheme()
                            ) {
                                keyboardContent()
                            }
                        },
                    ) {
                        keyboardContent()
                    }
                }
                composeView = host
            }
        return view
    }

    private fun installViewOwners(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        advanceLifecycleToResumed()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (mLifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (mLifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    private fun advanceLifecycleToResumed() {
        if (!mLifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        if (!mLifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        TelosKeyboardBuffer.clear()
        TelosKeyboardState.lastClipboardText = ""
        TelosKeyboardState.privateEditor = false
        val info = attribute ?: return
        TelosKeyboardState.imeAction = info.imeOptions and EditorInfo.IME_MASK_ACTION
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

        val sensitivePackages = java.util.Arrays.asList(
            "com.x8bit.bitwarden", "org.keepassdroid", "de.tutao.tutanota",
            "com.revolut.revolut", "de.number26.android", "ch.protonmail.android",
            "io.metamask", "exodusmovement.exodus"
        )
        val isSensitiveApp = info.packageName in sensitivePackages

        TelosKeyboardState.privateEditor = isPassword || numericPassword || noLearning || isSensitiveApp
        if (TelosKeyboardState.privateMode) {
            TelosKeyboardBuffer.clear()
            TelosKeyboardState.lastClipboardText = ""
        } else {
            updateClipboardSnippet()
        }
    }

    override fun onFinishInput() {
        TelosKeyboardBuffer.clear()
        TelosKeyboardState.lastClipboardText = ""
        TelosKeyboardState.privateEditor = false
        super.onFinishInput()
    }

    private fun handleKeyTap(char: String) {
        val ic = currentInputConnection ?: return
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

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        val selectedText = ic.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        TelosKeyboardBuffer.backspace(TelosKeyboardState.currentLanguage)
    }

    private fun handleEnter() {
        val ic = currentInputConnection ?: return
        TelosKeyboardBuffer.clear()
        val info = currentInputEditorInfo
        val action = keyboardEditorAction(info?.actionId ?: 0, info?.imeOptions ?: 0)
        if (action != 0) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun handleSpace() {
        currentInputConnection?.commitText(" ", 1)
        if (TelosKeyboardBuffer.currentWordBuffer.isNotBlank()) {
            TelosKeyboardDictionary.learnWord(
                TelosKeyboardBuffer.currentWordBuffer,
                TelosKeyboardState.privateMode,
            )
        }
        TelosKeyboardBuffer.clear()
    }

    private fun handlePasteText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun toggleIncognito() {
        TelosKeyboardState.isIncognitoMode = !TelosKeyboardState.isIncognitoMode
        if (TelosKeyboardState.privateMode) {
            TelosKeyboardBuffer.clear()
            TelosKeyboardState.lastClipboardText = ""
        }
    }

    private fun switchLanguage() {
        val newLang = if (TelosKeyboardState.currentLanguage == KeyboardLanguage.Greek)
            KeyboardLanguage.English
        else KeyboardLanguage.Greek
        TelosKeyboardState.currentLanguage = newLang
        lifecycleScope.launch {
            TelosDictionaryManager.ensureDictionary(applicationContext, newLang)
        }
    }

    private fun startVoiceTyping() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            android.widget.Toast.makeText(this, "Speech recognition unavailable", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        TelosKeyboardState.isListeningVoice = true
        runCatching {
            speechRecognizer?.destroy()
            val sr = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer = sr
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { TelosKeyboardState.isListeningVoice = false }
                override fun onError(error: Int) { TelosKeyboardState.isListeningVoice = false }
                override fun onResults(results: Bundle?) {
                    TelosKeyboardState.isListeningVoice = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null && matches.size > 0) {
                        currentInputConnection?.commitText(matches[0] + " ", 1)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (TelosKeyboardState.currentLanguage == KeyboardLanguage.Greek) "el-GR" else "en-US")
            sr.startListening(intent)
        }.onFailure {
            TelosKeyboardState.isListeningVoice = false
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null

    private fun handleTextEditingAction(action: String) {
        val ic = currentInputConnection ?: return
        when (action) {
            "left" -> ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
            "right" -> ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
            "up" -> ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
            "down" -> ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
            "select_all" -> ic.performContextMenuAction(android.R.id.selectAll)
            "copy" -> ic.performContextMenuAction(android.R.id.copy)
            "cut" -> ic.performContextMenuAction(android.R.id.cut)
            "paste" -> ic.performContextMenuAction(android.R.id.paste)
            "home" -> ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_HOME))
            "end" -> ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_END))
        }
    }

    private fun handlePasteClipboardItem(item: ClipboardItem) {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return

        if (item.imageUri != null) {
            runCatching {
                val inputContentInfo = InputContentInfoCompat(
                    item.imageUri,
                    ClipDescription("Image", arrayOf(item.mimeType)),
                    null
                )
                val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                InputConnectionCompat.commitContent(ic, info, inputContentInfo, flags, null)
            }.onFailure {
                if (!item.text.isNullOrBlank()) {
                    ic.commitText(item.text, 1)
                }
            }
        } else if (!item.text.isNullOrBlank()) {
            ic.commitText(item.text, 1)
        }
    }

    private fun updateClipboardSnippet() {
        runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = cm?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val items = java.util.ArrayList<ClipboardItem>()
                val count = clip.itemCount
                var i = 0
                while (i < count) {
                    val item = clip.getItemAt(i)
                    if (item.uri != null) {
                        val mime = cm.primaryClipDescription?.getMimeType(0) ?: "image/png"
                        items.add(ClipboardItem(text = item.text?.toString(), imageUri = item.uri, mimeType = mime))
                    } else if (item.text != null && item.text.length > 0) {
                        items.add(ClipboardItem(text = item.text.toString()))
                    }
                    i++
                }
                val firstItem = if (items.size > 0) items[0] else null
                TelosKeyboardState.clipboardItems = items
                TelosKeyboardState.lastClipboardItem = firstItem
                TelosKeyboardState.lastClipboardText = firstItem?.text ?: ""
            }
        }
    }

    private fun vibrateKey() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION") v?.vibrate(15)
            }
        }
    }

    override fun onDestroy() {
        composeView?.disposeComposition()
        composeView = null
        TelosKeyboardBuffer.clear()
        TelosKeyboardState.lastClipboardText = ""
        if (mLifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (mLifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        if (
            mLifecycleRegistry.currentState != Lifecycle.State.INITIALIZED &&
                mLifecycleRegistry.currentState != Lifecycle.State.DESTROYED
        ) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        mViewModelStore.clear()
        super.onDestroy()
    }
}
