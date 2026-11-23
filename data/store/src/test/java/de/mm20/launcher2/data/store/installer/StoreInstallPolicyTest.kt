package de.mm20.launcher2.data.store.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreInstallPolicyTest {
    @Test
    fun sessionSettingStillUsesShizukuWhenAuthorized() {
        assertEquals(listOf("shizuku"), StoreInstallPolicy.backends("session", shizukuReady = true, rootReady = false))
        assertTrue(StoreInstallPolicy.canInstallSilently("session", shizukuReady = true, rootReady = false, deviceOwner = false))
    }

    @Test
    fun sessionWithoutPrivilegeIsNotSilent() {
        assertEquals(emptyList<String>(), StoreInstallPolicy.backends("session", false, false))
        assertFalse(StoreInstallPolicy.canInstallSilently("session", false, false, deviceOwner = false))
        assertTrue(StoreInstallPolicy.canInstallSilently("session", false, false, deviceOwner = true))
    }

    @Test
    fun explicitShizukuDoesNotFallBackToRoot() {
        assertEquals(emptyList<String>(), StoreInstallPolicy.backends("shizuku", shizukuReady = false, rootReady = true))
    }
}
