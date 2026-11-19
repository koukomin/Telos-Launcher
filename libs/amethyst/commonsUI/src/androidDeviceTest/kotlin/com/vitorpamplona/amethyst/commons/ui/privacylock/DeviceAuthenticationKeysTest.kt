/*
 * Copyright (c) 2026 Telos contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vitorpamplona.amethyst.commons.ui.privacylock

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.security.keystore.UserNotAuthenticatedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** Uses only a fresh disposable emulator: this test must never alter a user's screen lock. */
@RunWith(AndroidJUnit4::class)
class DeviceAuthenticationKeysTest {
    @Test
    fun credentialProofRequiresAnActualSystemAuthentication() {
        assumeTrue(
            "Screen-lock test runs only on a disposable emulator",
            Build.HARDWARE in setOf("ranchu", "goldfish") &&
                (Build.FINGERPRINT.contains("generic") || Build.MODEL.startsWith("sdk_gphone")),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        assertFalse("The emulator must start without a screen lock", keyguard.isDeviceSecure)
        val pin = "673814" // Public synthetic fixture; never used outside the disposable emulator.
        try {
            shell("locksettings set-pin $pin")
            assertTrue("Could not configure the emulator fixture", keyguard.isDeviceSecure)
            val key = DeviceAuthenticationKeys.credentialKey()
            // Expire any authentication token issued by fixture creation.
            Thread.sleep(6_000)
            assertThrows(UserNotAuthenticatedException::class.java) {
                DeviceAuthenticationKeys.confirmCredential(key, byteArrayOf(1, 2, 3))
            }
            val verified = shell("locksettings verify --old $pin")
            assertTrue(verified, verified.contains("verified successfully", ignoreCase = true))
            DeviceAuthenticationKeys.confirmCredential(key, byteArrayOf(4, 5, 6))
        } finally {
            shell("locksettings clear --old $pin")
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry("amethyst-device-auth-credential-v1")
            }
        }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }
}
