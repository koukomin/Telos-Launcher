package com.telos.desktop.theme

import com.telos.desktop.cursor.CursorThemeCatalog
import com.telos.desktop.icons.TelosIconPackCatalog
import com.telos.desktop.icons.TelosIconPackInstaller
import com.telos.desktop.icons.LinuxIconThemeResolver
import com.telos.desktop.settings.TelosDesktopSettings

/**
 * One-click apply of a documented icon↔cursor matched pack (local GPL downloads).
 */
object MatchedPackInstaller {
    data class Result(
        val ok: Boolean,
        val packId: String,
        val iconTheme: String?,
        val cursorTheme: String?,
        val message: String,
    )

    fun apply(
        pack: MatchedThemePack,
        settings: TelosDesktopSettings,
        icons: LinuxIconThemeResolver,
        downloadMissing: Boolean = true,
    ): Result {
        var iconTheme: String? = null
        var cursorTheme: String? = null

        val iconCatalogId = pack.iconPackCatalogId
        if (iconCatalogId != null) {
            val entry = TelosIconPackCatalog.byId(iconCatalogId)
                ?: return Result(false, pack.id, null, null, "Unknown icon pack $iconCatalogId")
            val installed = TelosIconPackInstaller.isInstalled(entry, icons)
            if (!installed) {
                if (!downloadMissing) {
                    return Result(false, pack.id, null, null, "Icon pack not installed")
                }
                val ir = TelosIconPackInstaller.install(entry)
                if (!ir.ok) {
                    return Result(false, pack.id, null, null, ir.message.ifBlank { "Icon install failed" })
                }
                icons.invalidate()
                iconTheme = ir.themeNames.firstOrNull { it in pack.iconThemeNames }
                    ?: ir.themeNames.firstOrNull()
                    ?: MatchedThemePacks.preferredIconTheme(pack)
            } else {
                iconTheme = icons.listInstalledThemes()
                    .firstOrNull { t ->
                        t.name in pack.iconThemeNames ||
                            t.name.equals(entry.displayName, true) ||
                            t.name.equals(entry.id, true)
                    }?.name ?: MatchedThemePacks.preferredIconTheme(pack)
            }
        }

        val cursorId = MatchedThemePacks.preferredCursorCatalogId(pack)
        val cursorEntry = CursorThemeCatalog.entries.firstOrNull { it.id == cursorId }
        if (cursorEntry != null) {
            val already = com.telos.desktop.cursor.XCursorThemeDiscovery.listInstalled()
                .any { it.name in pack.cursorThemeFolders }
            if (!already) {
                if (!downloadMissing) {
                    return Result(false, pack.id, iconTheme, null, "Cursor theme not installed")
                }
                val cr = CursorThemeCatalog.download(cursorEntry)
                if (!cr.ok) {
                    return Result(false, pack.id, iconTheme, null, cr.message)
                }
                com.telos.desktop.cursor.XCursorThemeDiscovery.invalidate()
                cursorTheme = cr.themeFolder ?: cursorEntry.themeFolder
            } else {
                cursorTheme = pack.cursorThemeFolders.firstOrNull { folder ->
                    com.telos.desktop.cursor.XCursorThemeDiscovery.listInstalled()
                        .any { it.name.equals(folder, ignoreCase = true) }
                } ?: MatchedThemePacks.preferredCursorTheme(pack)
            }
        }

        if (iconTheme != null) {
            settings.iconTheme = iconTheme
            icons.preferredTheme = iconTheme
        }
        if (cursorTheme != null) {
            settings.cursorTheme = cursorTheme
        }
        settings.followSystemTheme = false
        settings.save()
        return Result(
            ok = true,
            packId = pack.id,
            iconTheme = iconTheme,
            cursorTheme = cursorTheme,
            message = "Matched ${pack.displayName}",
        )
    }
}
