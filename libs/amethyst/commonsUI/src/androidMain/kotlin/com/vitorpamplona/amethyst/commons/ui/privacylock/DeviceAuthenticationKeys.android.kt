/*
 * Copyright (c) 2026 Telos contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vitorpamplona.amethyst.commons.ui.privacylock

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Authentication proofs only: these keys never encrypt account data or replace its master key. */
internal object DeviceAuthenticationKeys {
    private const val STORE = "AndroidKeyStore"
    private const val BIOMETRIC_ALIAS = "amethyst-device-auth-biometric-v1"
    private const val CREDENTIAL_ALIAS = "amethyst-device-auth-credential-v1"

    fun biometricCipher(): Cipher {
        try {
            return cipher(key(BIOMETRIC_ALIAS, credential = false))
        } catch (_: KeyPermanentlyInvalidatedException) {
            // The proof key contains no user data; enrollment changes can safely replace it.
            KeyStore.getInstance(STORE).apply { load(null); deleteEntry(BIOMETRIC_ALIAS) }
            return cipher(key(BIOMETRIC_ALIAS, credential = false))
        }
    }

    fun credentialKey(): SecretKey {
        val credentialKey = key(CREDENTIAL_ALIAS, credential = true)
        try {
            cipher(credentialKey)
        } catch (_: UserNotAuthenticatedException) {
            // Expected before showing the credential prompt; the key itself is still valid.
        } catch (_: KeyPermanentlyInvalidatedException) {
            // Disabling/replacing the screen lock can invalidate a previous proof key.
            KeyStore.getInstance(STORE).apply { load(null); deleteEntry(CREDENTIAL_ALIAS) }
            return key(CREDENTIAL_ALIAS, credential = true)
        }
        return credentialKey
    }

    fun confirmCredential(key: SecretKey, challenge: ByteArray) {
        // A forged RESULT_OK cannot use this key without a recent system credential unlock.
        cipher(key).doFinal(challenge)
    }

    private fun cipher(key: SecretKey): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, key)
    }

    private fun key(alias: String, credential: Boolean): SecretKey {
        val store = KeyStore.getInstance(STORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(!credential)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(
                        if (credential) 5 else 0,
                        if (credential) KeyProperties.AUTH_DEVICE_CREDENTIAL
                        else KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(if (credential) 5 else -1)
                }
            }.build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE).run {
            init(spec)
            generateKey()
        }
    }
}
