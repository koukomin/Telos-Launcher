package com.telos.desktop.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformFilterTest {
    @Test
    fun macHidesLuksKeepsDisplay() {
        val mac = SettingsVisibilityFilter.forOs("Mac OS X")
        assertTrue(mac.showDisplayResolution)
        assertFalse(mac.showLuksVault)
        assertFalse(mac.showLinuxIconThemeSelector)
        val bsd = SettingsVisibilityFilter.forOs("FreeBSD")
        assertTrue(bsd.showXdgUserDirs)
        assertFalse(bsd.showLuksVault)
        val linux = SettingsVisibilityFilter.forOs("Linux")
        assertTrue(linux.showLuksVault)
    }
}
