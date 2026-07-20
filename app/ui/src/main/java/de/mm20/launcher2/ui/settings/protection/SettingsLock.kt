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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SettingsLockVM : ViewModel(), KoinComponent {
    private val protectionSettings: ProtectionSettings by inject()

    val lockSensitiveSettings = protectionSettings.lockSensitiveSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val lockMethod = protectionSettings.lockMethod
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val useCustomLock = protectionSettings.useCustomLock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val customLockHashed = protectionSettings.customLockHashed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val lockLauncher = protectionSettings.lockLauncher
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setLockLauncher(locked: Boolean) = protectionSettings.setLockLauncher(locked)

    fun setLockSensitiveSettings(locked: Boolean) =
        protectionSettings.setLockSensitiveSettings(locked)

    fun setLockMethod(method: SettingsLockMethod) = protectionSettings.setLockMethod(method)

    fun setUseCustomLock(use: Boolean) = protectionSettings.setUseCustomLock(use)

    fun setCustomLock(pin: String) {
        protectionSettings.setCustomLockHashed(hashPin(pin, newSalt()))
    }

    suspend fun verifyCustomLock(pin: String): Boolean {
        val stored = protectionSettings.customLockHashed.first() ?: return false
        val salt = stored.substringBefore(':', "")
        if (salt.isEmpty()) return false
        return constantTimeEquals(stored, hashPin(pin, salt))
    }
}

/**
 * Salted SHA-256 PIN hash, stored as "salt:hash" (both Base64). This is not a substitute for the
 * system credential - it only exists for the optional separate-lock mode - but it must at least
 * not be trivially reversible the way a raw String.hashCode() would be.
 */
private fun hashPin(pin: String, salt: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    digest.update(android.util.Base64.decode(salt, android.util.Base64.NO_WRAP))
    val hash = digest.digest(pin.toByteArray(Charsets.UTF_8))
    return salt + ":" + android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
}

private fun newSalt(): String {
    val bytes = ByteArray(16)
    java.security.SecureRandom().nextBytes(bytes)
    return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
}

/** Length-constant comparison to avoid leaking the hash through timing. */
private fun constantTimeEquals(a: String, b: String): Boolean {
    val ba = a.toByteArray(Charsets.UTF_8)
    val bb = b.toByteArray(Charsets.UTF_8)
    if (ba.size != bb.size) return false
    var result = 0
    for (i in ba.indices) result = result or (ba[i].toInt() xor bb[i].toInt())
    return result == 0
}

private fun authenticatorsFor(method: SettingsLockMethod): Int = when (method) {
    SettingsLockMethod.DeviceCredential ->
        Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL

    SettingsLockMethod.BiometricsOnly -> Authenticators.BIOMETRIC_STRONG
}

/** Whether the device currently offers any way to pass the given lock method. */
fun canAuthenticateSettings(activity: FragmentActivity, method: SettingsLockMethod): Boolean {
    return BiometricManager.from(activity)
        .canAuthenticate(authenticatorsFor(method)) == BiometricManager.BIOMETRIC_SUCCESS
}

/**
 * Runs the system authentication prompt for the given method.
 *
 * Strictly fail-closed: every path other than an explicit success from the system prompt -
 * missing hardware, nothing enrolled, prompt errors, lockout after repeated failures, or an
 * exception while showing the prompt - reports failure and the protected content stays locked.
 */
fun authenticateSettings(
    activity: FragmentActivity,
    method: SettingsLockMethod,
    title: String,
    onAuthenticationFailed: (() -> Unit)? = null,
    onResult: (Boolean) -> Unit,
) {
    val authenticators = authenticatorsFor(method)

    if (BiometricManager.from(activity).canAuthenticate(authenticators) !=
        BiometricManager.BIOMETRIC_SUCCESS
    ) {
        onResult(false)
        return
    }

    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Includes user cancellation and LOCKOUT / LOCKOUT_PERMANENT after
                // repeated failed attempts.
                onResult(false)
            }

            override fun onAuthenticationFailed() {
                // A single wrong biometric/credential attempt - the prompt stays open and the
                // user can retry, so this fires separately from (and before) onResult.
                onAuthenticationFailed?.invoke()
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

    try {
        prompt.authenticate(promptInfo)
    } catch (e: Exception) {
        onResult(false)
    }
}

/**
 * Gates [content] behind the settings lock. If the lock is disabled, content shows directly.
 * If enabled, the system prompt opens automatically on entry, and again on every new entry to
 * the screen (the unlocked state lives only in this composition).
 *
 * Strictly fail-closed: content is only ever shown after an explicit successful authentication.
 * If the host is not a FragmentActivity, or the device currently has no usable authenticator
 * for the configured method, the screen stays locked (with an explanation in the latter case).
 */
@Composable
fun ProtectedSettingsScreen(
    content: @Composable () -> Unit,
) {
    val viewModel: SettingsLockVM = viewModel()
    val locked by viewModel.lockSensitiveSettings.collectAsStateWithLifecycle()
    val method by viewModel.lockMethod.collectAsStateWithLifecycle()
    val useCustomLock by viewModel.useCustomLock.collectAsStateWithLifecycle()

    var unlocked by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? FragmentActivity
    val promptTitle = stringResource(R.string.settings_lock_prompt_title)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    if (showPinDialog) {
        CustomLockDialog(
            title = stringResource(R.string.custom_lock_dialog_title),
            onConfirm = { pin ->
                scope.launch {
                    if (viewModel.verifyCustomLock(pin)) {
                        unlocked = true
                        showPinDialog = false
                    }
                }
            },
            onDismiss = { showPinDialog = false }
        )
    }

    when {
        locked == null || method == null || useCustomLock == null -> {}

        locked == false || unlocked -> content()

        else -> {
            val currentMethod = method!!
            val isCustom = useCustomLock == true
            val authAvailable = isCustom ||
                (activity != null && canAuthenticateSettings(activity, currentMethod))

            if (authAvailable) {
                LaunchedEffect(Unit) {
                    if (isCustom) {
                        showPinDialog = true
                    } else {
                        authenticateSettings(activity!!, currentMethod, promptTitle) {
                            unlocked = it
                        }
                    }
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
                    text = stringResource(
                        if (authAvailable) R.string.settings_locked_message
                        else R.string.settings_locked_no_authenticator
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                if (authAvailable) {
                    Button(onClick = {
                        if (isCustom) {
                            showPinDialog = true
                        } else {
                            authenticateSettings(activity!!, currentMethod, promptTitle) {
                                unlocked = it
                            }
                        }
                    }) {
                        Text(stringResource(R.string.settings_locked_unlock))
                    }
                }
            }
        }
    }
}
