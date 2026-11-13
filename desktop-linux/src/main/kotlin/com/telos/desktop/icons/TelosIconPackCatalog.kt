package com.telos.desktop.icons

/**
 * Catalog of OSS freedesktop icon themes Telos may download on request.
 * Packs are **not** bundled; users download archives into the Telos icons root.
 * These are desktop freedesktop themes — not Android ADW launcher icon packs.
 */
data class TelosIconPackEntry(
    val id: String,
    val displayName: String,
    val license: String,
    val homepageUrl: String,
    val archiveUrl: String,
    /** Preferred directory names under the archive that contain index.theme. */
    val preferredThemeDirs: List<String> = emptyList(),
    val summary: String = "",
)

object TelosIconPackCatalog {
    val entries: List<TelosIconPackEntry> = listOf(
        TelosIconPackEntry(
            id = "papirus",
            displayName = "Papirus",
            license = "GPL-3.0",
            homepageUrl = "https://github.com/PapirusDevelopmentTeam/papirus-icon-theme",
            archiveUrl = "https://github.com/PapirusDevelopmentTeam/papirus-icon-theme/archive/refs/tags/20250501.tar.gz",
            preferredThemeDirs = listOf("Papirus", "Papirus-Dark", "Papirus-Light"),
            summary = "Material-style freedesktop icon theme",
        ),
        TelosIconPackEntry(
            id = "tela",
            displayName = "Tela",
            license = "GPL-3.0",
            homepageUrl = "https://github.com/vinceliuice/Tela-icon-theme",
            archiveUrl = "https://github.com/vinceliuice/Tela-icon-theme/archive/refs/heads/master.tar.gz",
            preferredThemeDirs = listOf("Tela", "Tela-dark", "Tela-light"),
            summary = "Flat colorful freedesktop icon theme",
        ),
        TelosIconPackEntry(
            id = "candy-icons",
            displayName = "Candy Icons",
            license = "GPL-3.0",
            homepageUrl = "https://github.com/EliverLara/candy-icons",
            archiveUrl = "https://github.com/EliverLara/candy-icons/archive/refs/heads/master.tar.gz",
            preferredThemeDirs = listOf("candy-icons"),
            summary = "Candy-style freedesktop icons (separate from Telos CandyCyber chrome glyphs)",
        ),
    )

    fun byId(id: String): TelosIconPackEntry? = entries.firstOrNull { it.id == id }
}
