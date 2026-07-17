package de.mm20.launcher2.permissions

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.SettingsLockMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AuthenticationService(
    private val context: Context,
    private val dataStore: LauncherDataStore,
) {
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        description: String? = null
    ): Boolean {
        val settings = dataStore.data.first()
        if (!settings.protectionLockSensitiveSettings) return true

        if (settings.protectionUseCustomLock) {
            // TODO: Implement custom PIN/Pattern UI dialog
            // For now, return false as it's not yet implemented
            return false
        }

        val allowedAuthenticators = when (settings.protectionLockMethod) {
            SettingsLockMethod.DeviceCredential -> BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            SettingsLockMethod.BiometricsOnly -> BiometricManager.Authenticators.BIOMETRIC_WEAK
        }

        val biometricManager = BiometricManager.from(context)
        if (biometricManager.canAuthenticate(allowedAuthenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            return false
        }

        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(context)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(false)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onAuthenticationFailed() {
                    // This is called for every failed attempt, don't resume yet
                }
            }

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(description)
                .setAllowedAuthenticators(allowedAuthenticators)
                .build()

            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            biometricPrompt.authenticate(promptInfo)
        }
    }

    fun isProtectionEnabled(): Boolean {
        return runCatching {
            val settings = dataStore.data.first()
            settings.protectionLockSensitiveSettings
        }.getOrDefault(false)
    }
}
