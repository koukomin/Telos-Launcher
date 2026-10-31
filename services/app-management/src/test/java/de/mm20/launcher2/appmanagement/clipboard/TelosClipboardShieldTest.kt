package de.mm20.launcher2.appmanagement.clipboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelosClipboardShieldTest {
    @Test
    fun detectsIban() {
        assertTrue(TelosClipboardShield.isSensitiveText("DE89370400440532013000"))
    }

    @Test
    fun detectsCardNumber() {
        assertTrue(TelosClipboardShield.isSensitiveText("4111111111111111"))
    }

    @Test
    fun detectsEthWallet() {
        assertTrue(TelosClipboardShield.isSensitiveText("0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb0"))
    }

    @Test
    fun detectsOtp() {
        assertTrue(TelosClipboardShield.isSensitiveText("482193"))
    }

    @Test
    fun detectsPasswordHeuristic() {
        assertTrue(TelosClipboardShield.isSensitiveText("S3cret!pass"))
    }

    @Test
    fun ignoresOrdinaryText() {
        assertFalse(TelosClipboardShield.isSensitiveText("hello world"))
        assertFalse(TelosClipboardShield.isSensitiveText("Telos Launcher"))
    }
}
