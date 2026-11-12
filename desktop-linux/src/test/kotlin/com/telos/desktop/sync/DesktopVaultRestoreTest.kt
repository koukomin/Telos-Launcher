package com.telos.desktop.sync

import com.telos.desktop.settings.TelosDesktopSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class DesktopVaultRestoreTest {
    @Test
    fun previewAndConfirmedRestoreRoundTrip() {
        val root = createTempDirectory("telos-vault-restore").toFile()
        val prevHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", root.absolutePath)
            com.telos.desktop.network.DesktopNetworkController.clearConfigHomeCache()
            val configDir = File(root, "config").apply { mkdirs() }
            File(configDir, "desktop.properties").writeText("panel.blur=2\n")
            val telosCfg = File(root, ".config/telos").apply { mkdirs() }
            File(telosCfg, "dns.txt").writeText("dns=https://dns.example/dns-query\n")
            // Non-secret companion file — WireGuard private keys may be filtered from export.
            File(telosCfg, "dns-block.txt").writeText("tracker.example\n")
            File(telosCfg, "wireguard").mkdirs()
            File(telosCfg, "wireguard/telos-wg.conf").writeText(
                "[Interface]\nPrivateKey=SHOULD_NOT_APPEAR_IN_AUDIT\nAddress=10.0.0.2/32\n",
            )
            val settings = TelosDesktopSettings(configDir).also { it.load() }
            val pass = "restore-passphrase".toCharArray()
            val bundle = File(telosCfg, "sync/${DesktopSessionSync.BUNDLE_NAME}").also { it.parentFile.mkdirs() }
            assertTrue(DesktopSessionSync.exportBundle(bundle, pass.copyOf(), settings).isSuccess)

            File(configDir, "desktop.properties").writeText("panel.blur=9\n")
            File(telosCfg, "dns.txt").writeText("dns=changed\n")

            val preview = DesktopVaultRestore.preview(bundle, pass.copyOf()).getOrThrow()
            assertTrue(preview.hasDesktopProperties)
            assertTrue(preview.telosConfigFileCount >= 1)
            assertTrue(preview.confirmToken.startsWith(DesktopVaultRestore.CONFIRM_PREFIX))

            val denied = DesktopVaultRestore.applyConfirmed(
                bundle,
                pass.copyOf(),
                confirmToken = "wrong",
                expectedToken = preview.confirmToken,
                vaultSettings = settings,
            )
            assertTrue(denied.isFailure)

            val applied = DesktopVaultRestore.applyConfirmed(
                bundle,
                pass.copyOf(),
                confirmToken = preview.confirmToken,
                expectedToken = preview.confirmToken,
                vaultSettings = settings,
            ).getOrThrow()
            assertTrue(applied.restoredDesktopProperties)
            assertTrue(File(configDir, "desktop.properties").readText().contains("panel.blur=2"))
            assertEquals("dns=https://dns.example/dns-query\n", File(telosCfg, "dns.txt").readText())

            val audit = File(root, ".config/telos/opsec/audit.log")
            if (audit.isFile) {
                val text = audit.readText()
                assertFalse(text.contains("SHOULD_NOT_APPEAR_IN_AUDIT"))
                assertTrue(text.contains("vault_restore"))
            }
        } finally {
            com.telos.desktop.network.DesktopNetworkController.clearConfigHomeCache()
            if (prevHome != null) System.setProperty("user.home", prevHome) else System.clearProperty("user.home")
            root.deleteRecursively()
        }
    }
}
