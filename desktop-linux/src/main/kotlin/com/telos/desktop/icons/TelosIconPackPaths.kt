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

    /**
     * @param create when true, creates the icons directory (install / download paths).
     * Discovery and cold-start only need [create]=false so Main does not mkdir on boot.
     */
    fun iconsRoot(create: Boolean = true): File {
        val root = resolveIconsRoot(create = create)
        if (create) root.mkdirs()
        return root
    }

    private fun resolveIconsRoot(create: Boolean): File {
        if (usePortableIcons()) {
            // Discovery (create=false) must not mkdir or flip the portable-active flag.
            val base = when {
                PortableTraceGuard.isPortable() -> PortableTraceGuard.dataRoot()
                create -> PortableTraceGuard.ensurePortableDataRoot()
                else -> PortableTraceGuard.dataRoot()
            }
            val root = File(base, "icons")
            if (create || PortableTraceGuard.isPortable()) {
                PortableTraceGuard.assertNoHostWrites(root.parentFile ?: root)
            }
            return root
        }
        val vaultMount = runCatching { PhantomVault.engine.mountPoint.takeIf { it.exists() } }.getOrNull()
        if (vaultMount != null) {
            return File(vaultMount, "icons")
        }
        val configParent = runCatching { PhantomVault.engine.configDir.parentFile }.getOrNull()
        if (configParent != null) {
            return File(configParent, "icons")
        }
        if (PortableTraceGuard.isPortable()) {
            return File(PortableTraceGuard.dataRoot(), "icons")
        }
        val home = System.getProperty("user.home")
        return File(home, ".local/share/telos/icons")
    }

    fun downloadsDir(): File = File(iconsRoot(create = true), ".downloads").apply { mkdirs() }

    fun stagingDir(): File = File(iconsRoot(create = true), ".staging").apply { mkdirs() }

    fun usePortableIcons(): Boolean =
        PortableTraceGuard.isPortable() ||
            PlatformEnvironment.settings.showPortableDesktopShell

    /**
     * Extra theme roots Telos owns (downloaded packs). Discovery-only: no mkdir.
     * Installers call [iconsRoot] with create=true before writing.
     */
    fun managedThemeRoots(): List<File> = listOf(iconsRoot(create = false))
}
