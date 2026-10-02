package de.mm20.launcher2.comms

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.coroutines.resume

class AuthManager : KoinComponent {
    private val context: Context by inject()
    private val sharedPrefs = context.getSharedPreferences("telos_vault_prefs", Context.MODE_PRIVATE)

    suspend fun authenticateNative(activity: FragmentActivity, title: String): Boolean = suspendCancellableCoroutine { cont ->
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (cont.isActive) cont.resume(false)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    if (cont.isActive) cont.resume(true)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Allow retry natively
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    fun hasCustomPin(): Boolean {
        return sharedPrefs.contains("vault_pin_hash")
    }

    fun setCustomPin(pin: String) {
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        sharedPrefs.edit()
            .putString("vault_pin_salt", salt)
            .putString("vault_pin_hash", hash)
            .apply()
    }

    fun authenticateCustom(pin: String): Boolean {
        val salt = sharedPrefs.getString("vault_pin_salt", null) ?: return false
        val storedHash = sharedPrefs.getString("vault_pin_hash", null) ?: return false
        val computedHash = hashPin(pin, salt)
        return computedHash == storedHash
    }

    private fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        return saltBytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray())
        val hashBytes = digest.digest(pin.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
