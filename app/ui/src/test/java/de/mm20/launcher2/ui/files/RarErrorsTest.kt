package de.mm20.launcher2.ui.files

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RarErrorsTest {
    @Test fun encryptionMessages() {
        assertTrue(RarErrors.isEncryption("The file is encrypted, but currently not supported"))
        assertTrue(RarErrors.isEncryption("RAR encryption support unavailable"))
        assertTrue(RarErrors.isEncryption("Decryption is unsupported due to lack of crypto library"))
        assertTrue(RarErrors.isEncryption("The file content is password protected"))
    }

    @Test fun otherMessages() {
        assertFalse(RarErrors.isEncryption(null))
        assertFalse(RarErrors.isEncryption(""))
        assertFalse(RarErrors.isEncryption("Bad RAR file data"))
        assertFalse(RarErrors.isEncryption("Truncated RAR file data"))
    }
}
