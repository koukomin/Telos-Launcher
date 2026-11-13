package com.telos.desktop.theme

import com.telos.desktop.apps.FlatpakSandboxTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SandboxChromeTintTest {
    @Test
    fun tiersMapToDocumentedNeonAccents() {
        assertEquals(VaultNeonTokens.accentOrange, SandboxChromeTint.forTier(FlatpakSandboxTier.Host))
        assertEquals(VaultNeonTokens.accentCyan, SandboxChromeTint.forTier(FlatpakSandboxTier.NetIsolated))
        assertEquals(VaultNeonTokens.accentGreen, SandboxChromeTint.forTier(FlatpakSandboxTier.VaultFs))
    }

    @Test
    fun standardUsesMutedInQuietOpsAndMagentaInNeon() {
        val quiet = SandboxChromeTint.forTier(FlatpakSandboxTier.Standard, ThemeIntensity.QuietOps)
        val neon = SandboxChromeTint.forTier(FlatpakSandboxTier.Standard, ThemeIntensity.VaultNeon)
        assertEquals(SandboxChromeTint.StandardMuted, quiet)
        assertEquals(SandboxChromeTint.Standard, neon)
        assertNotEquals(quiet, neon)
    }

    @Test
    fun borderAlphaFollowsIntensity() {
        assertTrue(
            SandboxChromeTint.borderAlpha(ThemeIntensity.VaultNeon) >
                SandboxChromeTint.borderAlpha(ThemeIntensity.QuietOps),
        )
    }
}
