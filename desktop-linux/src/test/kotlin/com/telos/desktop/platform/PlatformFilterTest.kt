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
        assertTrue(mac.showLinuxIconThemeSelector)
        val bsd = SettingsVisibilityFilter.forOs("FreeBSD")
        assertTrue(bsd.showXdgUserDirs)
        assertFalse(bsd.showLuksVault)
        val linux = SettingsVisibilityFilter.forOs("Linux")
        assertTrue(linux.showLuksVault)
        assertTrue(linux.showLabwcSession)
        assertTrue(linux.showNftSessionNetGuard)
        val win = SettingsVisibilityFilter.forOs("Windows 11")
        assertTrue(win.showPortableDesktopShell)
        assertFalse(win.showLuksVault)
        assertFalse(win.showLabwcSession)
        assertFalse(win.showNftSessionNetGuard)
        assertTrue(win.showDisplayResolution)
        assertTrue(win.showLinuxIconThemeSelector)
    }
}
