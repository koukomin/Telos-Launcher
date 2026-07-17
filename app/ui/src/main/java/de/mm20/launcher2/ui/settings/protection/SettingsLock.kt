package de.mm20.launcher2.ui.settings.protection

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.SettingsLockMethod
import de.mm20.launcher2.preferences.protection.ProtectionSettings
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SettingsLockVM : ViewModel(), KoinComponent {
    private val protectionSettings: ProtectionSettings by inject()

    val lockSensitiveSettings = protectionSettings.lockSensitiveSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val lockMethod = protectionSettings.lockMethod
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setLockSensitiveSettings(locked: Boolean) =
        protectionSettings.setLockSensitiveSettings(locked)

    fun setLockMethod(method: SettingsLockMethod) = protectionSettings.setLockMethod(method)
}

/**
 * Runs the system authentication prompt for the given method.
 *
 * If the device has no way to authenticate with this method at all (no biometric hardware, or
 * nothing enrolled), this *fails open* and reports success: a lock that nobody can open isn't
 * protection, it's data loss - and the device itself offers no barrier in that state anyway.
 */
fun authenticateSettings(
    activity: FragmentActivity,
    method: SettingsLockMethod,
    title: String,
    onResult: (Boolean) -> Unit,
) {
    val authenticators = when (method) {
        SettingsLockMethod.DeviceCredential ->
            Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL

        SettingsLockMethod.BiometricsOnly -> Authenticators.BIOMETRIC_STRONG
    }

    when (BiometricManager.from(activity).canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
            onResult(true)
            return
        }
    }

    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(false)
            }
        },
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setAllowedAuthenticators(authenticators)
        .apply {
            // A negative button is required when device credential is not an option.
            if (method == SettingsLockMethod.BiometricsOnly) {
                setNegativeButtonText(activity.getString(android.R.string.cancel))
            }
        }
        .build()

    prompt.authenticate(promptInfo)
}

/**
 * Gates [content] behind the settings lock. If the lock is disabled, content shows directly.
 * If enabled, the system prompt opens automatically on entry, and again on every new entry to
 * the screen (the unlocked state lives only in this composition).
 */
@Composable
fun ProtectedSettingsScreen(
    content: @Composable () -> Unit,
) {
    val viewModel: SettingsLockVM = viewModel()
    val locked by viewModel.lockSensitiveSettings.collectAsStateWithLifecycle()
    val method by viewModel.lockMethod.collectAsStateWithLifecycle()

    var unlocked by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? FragmentActivity
    val promptTitle = stringResource(R.string.settings_lock_prompt_title)

    when {
        locked == null || method == null -> {}

        locked == false || unlocked || activity == null -> content()

        else -> {
            val currentMethod = method!!
            LaunchedEffect(Unit) {
                authenticateSettings(activity, currentMethod, promptTitle) {
                    unlocked = it
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painterResource(R.drawable.lock_48px),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.settings_locked_message),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                Button(onClick = {
                    authenticateSettings(activity, currentMethod, promptTitle) {
                        unlocked = it
                    }
                }) {
                    Text(stringResource(R.string.settings_locked_unlock))
                }
            }
        }
    }
}
