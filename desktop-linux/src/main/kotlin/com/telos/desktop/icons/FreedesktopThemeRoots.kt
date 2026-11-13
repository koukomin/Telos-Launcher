package com.telos.desktop.icons

import com.telos.desktop.vault.PortableEnvironmentIsolator
import com.telos.desktop.vault.PortableTraceGuard
import java.io.File

/**
 * Shared Freedesktop icon / cursor theme search roots.
 *
 * Layout (same for icon packs and XCursor themes):
 * ```
 * <root>/<ThemeName>/index.theme
 * <root>/<ThemeName>/cursors/     # XCursor / Plasma cursor themes
 * <root>/<ThemeName>/<size>/…     # icon packs
 * ```
 *
 * Portable / vault sessions keep installs under the portable or vault data root
 * (`…/icons` and `…/.local/share/icons`). Host HOME roots are scanned for discovery
 * only; downloads never write there unless the user chose Host Home boot.
 */
object FreedesktopThemeRoots {
    /** Writable install root for downloaded themes (portable/vault-safe). */
    fun installRoot(): File {
        PortableEnvironmentIsolator.vaultHome?.let { home ->
            return File(home, ".local/share/icons").also { it.mkdirs() }
        }
        if (PortableTraceGuard.isPortable()) {
            return File(PortableTraceGuard.dataRoot(), "icons").also { it.mkdirs() }
        }
        val xdg = System.getenv("XDG_DATA_HOME")?.trim()?.takeIf { it.isNotEmpty() }
        if (xdg != null) return File(xdg, "icons").also { it.mkdirs() }
        val home = File(System.getProperty("user.home"))
        return File(home, ".local/share/icons").also { it.mkdirs() }
    }

    /** Read-only + writable roots used for discovery (deduped, existing dirs preferred). */
    fun searchRoots(includeHostSystem: Boolean = true): List<File> {
        val out = LinkedHashSet<File>()
        fun add(f: File?) {
            if (f == null) return
            out += f
        }

        PortableEnvironmentIsolator.vaultHome?.let { home ->
            add(File(home, ".local/share/icons"))
            add(File(home, ".icons"))
        }
        if (PortableTraceGuard.isPortable()) {
            val root = PortableTraceGuard.dataRoot()
            add(File(root, "icons"))
            add(File(root, "share/icons"))
            add(File(root, ".local/share/icons"))
        }

        val xdgData = System.getenv("XDG_DATA_HOME")?.trim()?.takeIf { it.isNotEmpty() }
        if (xdgData != null) add(File(xdgData, "icons"))

        val home = File(System.getProperty("user.home"))
        add(File(home, ".local/share/icons"))
        add(File(home, ".icons"))

        if (includeHostSystem) {
            // Host profile for discovery when vault $HOME differs from TELOS_HOST_HOME.
            val host = PortableTraceGuard.hostProfileHome()
            if (host.canonicalFile != home.canonicalFile) {
                add(File(host, ".local/share/icons"))
                add(File(host, ".icons"))
            }
            add(File("/usr/share/icons"))
            add(File("/usr/local/share/icons"))
            System.getenv("XDG_DATA_DIRS")?.split(':')?.forEach { dir ->
                if (dir.isNotBlank()) add(File(dir.trim(), "icons"))
            }
        }
        return out.toList()
    }
}
