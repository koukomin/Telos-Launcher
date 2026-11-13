package com.telos.desktop.theme

import java.io.File

/**
 * Locked-boot / unlock theatre rules (Tier B #7).
 *
 * Pre-boot uses Quiet Ops + vault glyph only — never a user wallpaper that
 * could leak identity. After a successful unlock, the session may crossfade
 * to the vault wallpaper path and chosen [ThemeIntensity]. No particle spam.
 */
object UnlockTheatre {
    const val DEFAULT_REVEAL_MS = 420

    /** Pre-boot must never paint a user wallpaper (identity / OpSec). */
    fun allowsUserWallpaper(unlocked: Boolean): Boolean = unlocked

    /**
     * Resolve a wallpaper file only when unlocked and the path stays under the
     * vault mount (or portable data root). Relative paths are resolved against
     * [vaultRoot]. Host-absolute paths outside the vault are rejected.
     */
    fun resolveWallpaperFile(
        unlocked: Boolean,
        vaultRoot: File?,
        wallpaperPath: String?,
    ): File? {
        if (!unlocked) return null
        val raw = wallpaperPath?.trim().orEmpty()
        if (raw.isEmpty() || vaultRoot == null) return null
        val root = runCatching { vaultRoot.canonicalFile }.getOrNull() ?: return null
        if (!root.isDirectory) return null
        val candidate = if (File(raw).isAbsolute) {
            File(raw)
        } else {
            File(root, raw)
        }
        val file = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        if (!file.isFile) return null
        val rootPath = root.path.let { if (it.endsWith(File.separator)) it else it + File.separator }
        if (file.path != root.path && !file.path.startsWith(rootPath)) return null
        val name = file.name.lowercase()
        val okExt = name.endsWith(".png") || name.endsWith(".jpg") ||
            name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".bmp")
        return file.takeIf { okExt }
    }

    /**
     * Crossfade alphas for Quiet Ops scrim → wallpaper reveal.
     * [progress] 0 = fully locked Quiet Ops; 1 = fully revealed session.
     */
    fun revealAlphas(progress: Float): RevealAlphas {
        val p = progress.coerceIn(0f, 1f)
        return RevealAlphas(
            quietOpsScrim = 1f - p,
            wallpaper = p,
            intensityMix = p,
        )
    }

    data class RevealAlphas(
        val quietOpsScrim: Float,
        val wallpaper: Float,
        val intensityMix: Float,
    )
}
