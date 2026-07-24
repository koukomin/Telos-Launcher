package de.mm20.launcher2.ui.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

/**
 * Applying [KeyboardType.Password] (without a visual transformation, so typed text still shows
 * normally) is how EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING ends up set on the IME - Compose's
 * KeyboardOptions/ImeOptions don't expose that flag directly. This also disables autocorrect and
 * suggestion learning as a side effect, which is the point: text typed here shouldn't be recorded
 * by the keyboard's personal dictionary.
 */
fun KeyboardOptions.withPrivateKeyboard(enabled: Boolean): KeyboardOptions =
    if (enabled) copy(keyboardType = KeyboardType.Password) else this
