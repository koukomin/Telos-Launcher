package de.mm20.launcher2.ui.trash
import de.mm20.launcher2.i18n.R as I18nR

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.AuthManager

import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

class TrashUnlockState : ViewModel() {
    var unlocked by mutableStateOf(false)
    var started = false
}

@Serializable
data object TrashUnlockRoute : NavKey

@Composable
fun TrashUnlockScreen() {
    val activity = LocalActivity.current as? FragmentActivity
    val backStack = LocalBackStack.current
    val title = stringResource(I18nR.string.telos_trash_unlock)
    val state: TrashUnlockState = viewModel()
    val unlocked = state.unlocked
    // Recreating BiometricPrompt reconnects its callback to AndroidX's retained prompt.
    // Only the first composition starts authentication; rotation must not open a second prompt.
    val prompt = remember(activity, state) {
        activity?.let { host ->
            androidx.biometric.BiometricPrompt(host, androidx.core.content.ContextCompat.getMainExecutor(host),
                object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                        state.unlocked = de.mm20.launcher2.comms.BiometricCryptoAuth.confirmed(result)
                    }
                    override fun onAuthenticationError(code: Int, message: CharSequence) {
                        if (!host.isChangingConfigurations) { state.started = false; backStack.removeLastOrNull() }
                    }
                })
        }
    }
    LaunchedEffect(prompt) {
        if (state.unlocked || state.started) return@LaunchedEffect
        if (prompt == null) { backStack.removeLastOrNull(); return@LaunchedEffect }
        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder().setTitle(title).apply {
            if (android.os.Build.VERSION.SDK_INT >= 30) setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            else {
                setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                setNegativeButtonText(activity.getString(android.R.string.cancel))
            }
        }.build()
        val cipher = de.mm20.launcher2.comms.BiometricCryptoAuth.encryptCipher()
        if (cipher == null) {
            state.started = false
            backStack.removeLastOrNull()
            return@LaunchedEffect
        }
        state.started = true
        runCatching { prompt.authenticate(info, androidx.biometric.BiometricPrompt.CryptoObject(cipher)) }
            .onFailure { state.started = false; backStack.removeLastOrNull() }
    }
    if (!unlocked) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        TrashScreen()
    }
}
