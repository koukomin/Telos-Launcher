package com.telos.desktop.sync

import com.telos.desktop.settings.TelosDesktopSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class DesktopStoredSyncKeyTest {
    @Test
    fun storeLoadRoundTripIsNotPlaintext() {
        val dir = createTempDirectory("telos-sync-key").toFile()
        try {
            val secret = "session-passphrase-äΩ".toCharArray()
            assertTrue(DesktopStoredSyncKey.store(secret, dir))
            assertTrue(DesktopStoredSyncKey.hasStoredKey(dir))
            val raw = DesktopStoredSyncKey.location(dir).readBytes()
            val asText = raw.toString(Charsets.ISO_8859_1)
            assertFalse(asText.contains("session-passphrase"))
            val loaded = DesktopStoredSyncKey.load(dir)
            assertArrayEquals(secret, loaded)
            DesktopStoredSyncKey.clear(dir)
            assertFalse(DesktopStoredSyncKey.hasStoredKey(dir))
            assertNull(DesktopStoredSyncKey.load(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun emptyPassphraseIsRejected() {
        val dir = createTempDirectory("telos-sync-empty").toFile()
        try {
            assertFalse(DesktopStoredSyncKey.store(CharArray(0), dir))
            assertFalse(DesktopStoredSyncKey.hasStoredKey(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun maybeAutoImportSkipsWithoutStoredKeyOrKeePassPassword() {
        val root = createTempDirectory("telos-sync-skip").toFile()
        val prevHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", root.absolutePath)
            val telosCfg = File(root, ".config/telos").apply { mkdirs() }
            File(telosCfg, DesktopSessionSync.AUTO_IMPORT_FLAG).writeText("true")
            File(telosCfg, "sync").mkdirs()
            // Bundle missing and no stored key → no-op (never invents credentials).
            val configDir = File(root, "config").apply { mkdirs() }
            val settings = TelosDesktopSettings(configDir).also { it.load() }
            assertFalse(
                DesktopSessionSync.maybeAutoImport(
                    kdbx = null,
                    masterPassword = null,
                    vaultSettings = settings,
                ),
            )
        } finally {
            if (prevHome != null) System.setProperty("user.home", prevHome) else System.clearProperty("user.home")
            root.deleteRecursively()
        }
    }

    @Test
    fun maybeAutoImportUnlocksWithStoredKeyWithoutKeePassPrompt() {
        val root = createTempDirectory("telos-sync-auto").toFile()
        val prevHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", root.absolutePath)
            val configDir = File(root, "config").apply { mkdirs() }
            File(configDir, "desktop.properties").writeText("panel.blur=1\n")
            val pass = "boot-sync-key".toCharArray()
            assertTrue(DesktopStoredSyncKey.store(pass, configDir))

            val telosCfg = File(root, ".config/telos").apply { mkdirs() }
            File(telosCfg, DesktopSessionSync.AUTO_IMPORT_FLAG).writeText("true")
            val settings = TelosDesktopSettings(configDir).also { it.load() }
            val dest = File(telosCfg, "sync/${DesktopSessionSync.BUNDLE_NAME}").also { it.parentFile.mkdirs() }
            assertTrue(DesktopSessionSync.exportBundle(dest, pass.copyOf(), settings).isSuccess)

            val ok = DesktopSessionSync.maybeAutoImport(
                kdbx = null,
                masterPassword = null,
                vaultSettings = settings,
            )
            assertTrue(ok)
        } finally {
            if (prevHome != null) System.setProperty("user.home", prevHome) else System.clearProperty("user.home")
            root.deleteRecursively()
        }
    }
}
