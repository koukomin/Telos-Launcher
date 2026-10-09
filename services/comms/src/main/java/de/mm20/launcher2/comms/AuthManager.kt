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

        val builder = BiometricPrompt.PromptInfo.Builder().setTitle(title)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            // BIOMETRIC_STRONG or DEVICE_CREDENTIAL is not supported on API 28-29: the (deprecated)
            // device credential flag is the supported way to allow PIN/pattern/password there
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        val promptInfo = builder.build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(false)
        }
    }

    fun hasCustomPin(): Boolean {
        return sharedPrefs.contains("vault_pin_hash")
    }

    fun setCustomPin(pin: String) {
        val salt = generateSalt()
        sharedPrefs.edit()
            .putString("vault_pin_salt", salt)
            .putString("vault_pin_hash", pbkdf2(pin, salt))
            .putInt("vault_pin_iterations", ITERATIONS)
            .putInt("vault_pin_failures", 0)
            .apply()
    }

    /** Seconds until another guess is allowed, 0 when it is allowed now */
    fun lockedForSeconds(): Long {
        val until = sharedPrefs.getLong("vault_pin_locked_until", 0L)
        return ((until - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    }

    fun authenticateCustom(pin: String): Boolean {
        // a short PIN has few combinations: guesses are slowed down, not just hashed slowly
        if (lockedForSeconds() > 0) return false
        val salt = sharedPrefs.getString("vault_pin_salt", null) ?: return false
        val storedHash = sharedPrefs.getString("vault_pin_hash", null) ?: return false
        val legacy = !sharedPrefs.contains("vault_pin_iterations")
        val computed = if (legacy) legacyHash(pin, salt) else pbkdf2(pin, salt)
        val ok = MessageDigest.isEqual(computed.toByteArray(), storedHash.toByteArray())
        if (ok) {
            sharedPrefs.edit().putInt("vault_pin_failures", 0).remove("vault_pin_locked_until").apply()
            if (legacy) setCustomPin(pin) // move an old PIN to the stronger hash
        } else {
            val failures = sharedPrefs.getInt("vault_pin_failures", 0) + 1
            val edit = sharedPrefs.edit().putInt("vault_pin_failures", failures)
            if (failures >= FREE_ATTEMPTS) {
                // 30 s after the 5th wrong PIN, then twice as long for every further one, at most an hour
                val seconds = (30L shl (failures - FREE_ATTEMPTS).coerceAtMost(7)).coerceAtMost(3600L)
                edit.putLong("vault_pin_locked_until", System.currentTimeMillis() + seconds * 1000)
            }
            edit.apply()
        }
        return ok
    }

    private fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        return saltBytes.joinToString("") { "%02x".format(it) }
    }

    private fun pbkdf2(pin: String, salt: String): String {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt.toByteArray(), ITERATIONS, 256)
        val bytes = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun legacyHash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray())
        val hashBytes = digest.digest(pin.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val ITERATIONS = 200_000
        const val FREE_ATTEMPTS = 5
    }
}
