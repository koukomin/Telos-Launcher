package com.telos.desktop.icons

import com.telos.desktop.platform.PlatformEnvironment
import com.telos.desktop.vault.PhantomVault
import com.telos.desktop.vault.PortableTraceGuard
import java.io.File

/**
 * Install / discovery roots for freedesktop icon themes managed by Telos Desktop.
 *
 * Rules:
 * - Portable Data / Portable Vault / Win·mac shell → `TelosPortableData/icons/` (never host HOME).
 * - Linux vault session → `{vault_mount}/icons/` (sibling of `config/`, not host profile).
 * - Downloads and extracts stay under that root; system themes are read-only discovery.
 */
object TelosIconPackPaths {
    const val BUILTIN_CANDY = "CandyCyber"

    fun iconsRoot(): File {
        if (usePortableIcons()) {
            val root = File(PortableTraceGuard.ensurePortableDataRoot(), "icons")
            PortableTraceGuard.assertNoHostWrites(root.parentFile ?: root)
            return root.apply { mkdirs() }
        }
        val vaultMount = runCatching { PhantomVault.engine.mountPoint.takeIf { it.exists() } }.getOrNull()
        if (vaultMount != null) {
            return File(vaultMount, "icons").apply { mkdirs() }
        }
        val configParent = runCatching { PhantomVault.engine.configDir.parentFile }.getOrNull()
        if (configParent != null) {
            return File(configParent, "icons").apply { mkdirs() }
        }
        if (PortableTraceGuard.isPortable()) {
            return File(PortableTraceGuard.dataRoot(), "icons").apply { mkdirs() }
        }
        val home = System.getProperty("user.home")
        return File(home, ".local/share/telos/icons").apply { mkdirs() }
    }

    fun downloadsDir(): File = File(iconsRoot(), ".downloads").apply { mkdirs() }

    fun stagingDir(): File = File(iconsRoot(), ".staging").apply { mkdirs() }

    fun usePortableIcons(): Boolean =
        PortableTraceGuard.isPortable() ||
            PlatformEnvironment.settings.showPortableDesktopShell

    /** Extra theme roots Telos owns (downloaded packs). */
    fun managedThemeRoots(): List<File> = listOf(iconsRoot())
}
