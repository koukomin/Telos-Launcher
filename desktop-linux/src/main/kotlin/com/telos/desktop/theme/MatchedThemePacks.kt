package com.telos.desktop.theme

/**
 * Documented one-click icon↔cursor matched packs (local GPL downloads only).
 *
 * - Papirus ↔ BreezeX
 * - candy-icons ↔ Bibata (CandyCyber remains the built-in Compose chrome glyph fallback)
 */
data class MatchedThemePack(
    val id: String,
    val displayName: String,
    val iconPackCatalogId: String?,
    val iconThemeNames: List<String>,
    val cursorCatalogIds: List<String>,
    val cursorThemeFolders: List<String>,
    /** Built-in CandyCyber chrome is complementary, not a downloadable Freedesktop theme. */
    val candyCyberChrome: Boolean = false,
)

object MatchedThemePacks {
    val papirusBreezex = MatchedThemePack(
        id = "papirus-breezex",
        displayName = "Papirus + BreezeX",
        iconPackCatalogId = "papirus",
        iconThemeNames = listOf("Papirus-Dark", "Papirus", "Papirus-Light"),
        cursorCatalogIds = listOf("breezex-dark", "breezex-light"),
        cursorThemeFolders = listOf("BreezeX-Dark", "BreezeX-Light"),
    )

    val candyBibata = MatchedThemePack(
        id = "candy-bibata",
        displayName = "Candy Icons + Bibata",
        iconPackCatalogId = "candy-icons",
        iconThemeNames = listOf("candy-icons"),
        cursorCatalogIds = listOf("bibata-modern-classic", "bibata-original-classic"),
        cursorThemeFolders = listOf("Bibata-Modern-Classic", "Bibata-Original-Classic"),
        candyCyberChrome = true,
    )

    val all: List<MatchedThemePack> = listOf(papirusBreezex, candyBibata)

    fun byId(id: String): MatchedThemePack? = all.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun forIconPackCatalogId(iconPackId: String): MatchedThemePack? =
        all.firstOrNull { it.iconPackCatalogId.equals(iconPackId, ignoreCase = true) }

    fun forCursorCatalogId(cursorId: String): MatchedThemePack? =
        all.firstOrNull { pack ->
            pack.cursorCatalogIds.any { it.equals(cursorId, ignoreCase = true) }
        }

    fun forIconThemeName(themeName: String): MatchedThemePack? {
        val n = themeName.trim()
        if (n.isEmpty()) return null
        return all.firstOrNull { pack ->
            pack.iconThemeNames.any { it.equals(n, ignoreCase = true) } ||
                (pack.candyCyberChrome && n.equals("CandyCyber", ignoreCase = true))
        }
    }

    fun forCursorThemeFolder(folder: String): MatchedThemePack? {
        val n = folder.trim()
        if (n.isEmpty()) return null
        return all.firstOrNull { pack ->
            pack.cursorThemeFolders.any { it.equals(n, ignoreCase = true) }
        }
    }

    /** Preferred theme folder names to apply after a successful match install. */
    fun preferredIconTheme(pack: MatchedThemePack): String =
        pack.iconThemeNames.firstOrNull() ?: pack.iconPackCatalogId.orEmpty()

    fun preferredCursorTheme(pack: MatchedThemePack): String =
        pack.cursorThemeFolders.firstOrNull().orEmpty()

    fun preferredCursorCatalogId(pack: MatchedThemePack): String =
        pack.cursorCatalogIds.firstOrNull().orEmpty()
}
