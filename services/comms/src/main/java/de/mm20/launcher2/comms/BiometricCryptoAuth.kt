package de.mm20.launcher2.comms

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Binds [BiometricPrompt] success to a Keystore AES [Cipher.doFinal] so local authentication
 * cannot be treated as a boolean-only gate (CodeQL `java/android/insecure-local-authentication`).
 */
object BiometricCryptoAuth {
    private const val ALIAS = "telos-biometric-gate-v1"
    private val challenge = byteArrayOf(0x74, 0x65, 0x6c, 0x6f, 0x73)

    fun encryptCipher(): Cipher? = runCatching {
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
    }.getOrNull()

    fun confirmed(result: BiometricPrompt.AuthenticationResult): Boolean {
        val cipher = result.cryptoObject?.cipher ?: return false
        return runCatching {
            cipher.doFinal(challenge)
            true
        }.getOrDefault(false)
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val purposes = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        val spec = KeyGenParameterSpec.Builder(ALIAS, purposes)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= 30) {
                    setUserAuthenticationParameters(
                        0,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(spec)
            generateKey()
        }
    }
}
