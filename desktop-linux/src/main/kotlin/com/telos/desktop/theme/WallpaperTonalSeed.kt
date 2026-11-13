package com.telos.desktop.theme

import androidx.compose.ui.graphics.Color
import com.telos.desktop.vault.PortableEnvironmentIsolator
import com.telos.desktop.vault.PortableTraceGuard
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local wallpaper → Material-style tonal seed for Telos Desktop chrome.
 *
 * Samples vault/portable wallpaper on [Dispatchers.IO] only. Derives a seed colour
 * and tonal containers on-device — never uploads pixels. `:libs:material-color-utilities`
 * is an Android library, so desktop uses a JVM-local quantize + chroma score inspired
 * by the same Material scoring constants (target chroma 48).
 *
 * Cache key is the SHA-256 content digest; changing [wallpaperPath] or file bytes
 * invalidates. Sampling failure falls back to neon cyan ([FALLBACK_SEED_ARGB]).
 */
object WallpaperTonalSeed {
    /** Neon cyan — Telos secondary / safe fallback when sampling fails. */
    const val FALLBACK_SEED_ARGB: Int = 0xFF00F0FF.toInt()

    private const val SAMPLE_EDGE = 64
    private const val QUANT_BITS = 5
    private const val TARGET_CHROMA = 48.0
    private const val CUTOFF_CHROMA = 5.0

    @Volatile
    private var cachedDigest: String? = null

    @Volatile
    private var cachedPath: String? = null

    @Volatile
    private var cachedResult: WallpaperTonalPalette? = null

    data class WallpaperTonalPalette(
        val seedArgb: Int,
        val primaryArgb: Int,
        val primaryContainerArgb: Int,
        val secondaryContainerArgb: Int,
        val surfaceContainerArgb: Int,
        val digest: String,
        val wallpaperPath: String?,
        val fromFallback: Boolean,
    ) {
        val seedColor: Color get() = Color(seedArgb)
    }

    fun clearCache() {
        cachedDigest = null
        cachedPath = null
        cachedResult = null
    }

    fun usesWallpaperAccent(themeAccent: String, wallpaperTonal: Boolean): Boolean {
        if (wallpaperTonal) return true
        return themeAccent.trim().equals("wallpaper", ignoreCase = true)
    }

    /**
     * Resolve wallpaper under vault / portable / explicit path. Never invents a host-home
     * path when Portable Data is active.
     */
    fun resolveWallpaperFile(
        configuredPath: String?,
        homeOverride: File? = null,
    ): File? {
        val configured = configuredPath?.trim()?.takeIf { it.isNotEmpty() }?.let { File(it) }
        if (configured != null && configured.isFile && isAllowedWallpaperRoot(configured, homeOverride)) {
            return configured
        }
        val roots = wallpaperSearchRoots(homeOverride)
        val names = listOf(
            "wallpaper.png", "wallpaper.jpg", "wallpaper.jpeg", "wallpaper.webp",
            "Wallpaper.png", "Wallpaper.jpg", "background.png", "background.jpg",
            "desktop.png", "desktop.jpg",
        )
        for (root in roots) {
            for (name in names) {
                val f = File(root, name)
                if (f.isFile) return f
            }
            root.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTS }
                ?.sortedBy { it.name.lowercase() }
                ?.firstOrNull()
                ?.let { return it }
        }
        return null
    }

    private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

    private fun wallpaperSearchRoots(homeOverride: File?): List<File> {
        val out = ArrayList<File>(6)
        homeOverride?.let {
            out += File(it, "Pictures")
            out += File(it, ".config/telos")
        }
        PortableEnvironmentIsolator.vaultHome?.takeIf { PortableEnvironmentIsolator.active }?.let {
            out += File(it, "Pictures")
            out += File(it, ".config/telos")
        }
        if (PortableTraceGuard.isPortable()) {
            out += File(PortableTraceGuard.dataRoot(), "Pictures")
            out += File(PortableTraceGuard.dataRoot(), "config")
        } else if (homeOverride == null && PortableEnvironmentIsolator.vaultHome == null) {
            val home = File(System.getProperty("user.home"))
            out += File(home, "Pictures")
            out += File(home, ".config/telos")
        }
        return out.distinctBy { it.absolutePath }
    }

    private fun isAllowedWallpaperRoot(file: File, homeOverride: File?): Boolean {
        val canonical = runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }
        val allowed = wallpaperSearchRoots(homeOverride).mapNotNull {
            runCatching { it.canonicalFile }.getOrNull()
        }
        if (allowed.any { canonical.path.startsWith(it.path) }) return true
        // Explicit configured path under session HOME when vault isolator is on.
        PortableEnvironmentIsolator.vaultHome?.takeIf { PortableEnvironmentIsolator.active }?.let { vh ->
            val root = runCatching { vh.canonicalFile }.getOrElse { vh.absoluteFile }
            if (canonical.path.startsWith(root.path)) return true
        }
        if (PortableTraceGuard.isPortable()) {
            val root = runCatching { PortableTraceGuard.dataRoot().canonicalFile }
                .getOrElse { PortableTraceGuard.dataRoot().absoluteFile }
            return canonical.path.startsWith(root.path)
        }
        return homeOverride == null // non-portable: configured path accepted
    }

    fun contentDigest(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    fun fallbackPalette(reasonPath: String? = null): WallpaperTonalPalette {
        return deriveContainers(
            seedArgb = FALLBACK_SEED_ARGB,
            digest = "fallback",
            wallpaperPath = reasonPath,
            fromFallback = true,
        )
    }

    /**
     * Sample [file] and return a tonal palette. Call from [Dispatchers.IO] / background only.
     * Uses content digest cache; path change or digest mismatch recomputes.
     */
    fun sampleFile(file: File?): WallpaperTonalPalette {
        if (file == null || !file.isFile) return fallbackPalette(file?.absolutePath)
        val pathKey = file.absolutePath
        val digest = runCatching { contentDigest(file) }.getOrElse {
            return fallbackPalette(pathKey)
        }
        synchronized(this) {
            val hit = cachedResult
            if (hit != null &&
                cachedDigest == digest &&
                cachedPath == pathKey &&
                !hit.fromFallback
            ) {
                return hit
            }
        }
        val seed = runCatching { extractSeedArgb(file) }.getOrNull()
            ?: return fallbackPalette(pathKey).also { putCache(pathKey, digest, it) }
        val palette = deriveContainers(seed, digest, pathKey, fromFallback = false)
        putCache(pathKey, digest, palette)
        return palette
    }

    private fun putCache(path: String, digest: String, palette: WallpaperTonalPalette) {
        synchronized(this) {
            cachedPath = path
            cachedDigest = digest
            cachedResult = palette
        }
    }

    suspend fun resolveAccent(
        themeAccent: String,
        wallpaperTonal: Boolean,
        wallpaperPath: String?,
        homeOverride: File? = null,
        hostFallback: Color = Color(FALLBACK_SEED_ARGB),
    ): Color = withContext(Dispatchers.IO) {
        if (!usesWallpaperAccent(themeAccent, wallpaperTonal)) {
            return@withContext hostFallback
        }
        val file = resolveWallpaperFile(wallpaperPath, homeOverride)
        sampleFile(file).seedColor
    }

    suspend fun resolvePalette(
        themeAccent: String,
        wallpaperTonal: Boolean,
        wallpaperPath: String?,
        homeOverride: File? = null,
    ): WallpaperTonalPalette = withContext(Dispatchers.IO) {
        if (!usesWallpaperAccent(themeAccent, wallpaperTonal)) {
            return@withContext fallbackPalette(wallpaperPath)
        }
        sampleFile(resolveWallpaperFile(wallpaperPath, homeOverride))
    }

    /** Visible for unit tests: score ARGB pixel populations without ImageIO. */
    fun seedFromPixels(pixels: IntArray): Int {
        if (pixels.isEmpty()) return FALLBACK_SEED_ARGB
        val populations = HashMap<Int, Int>(min(pixels.size, 512))
        for (argb in pixels) {
            val q = quantizeArgb(argb)
            populations[q] = (populations[q] ?: 0) + 1
        }
        return scoreSeed(populations)
    }

    private fun extractSeedArgb(file: File): Int? {
        val raw = ImageIO.read(file) ?: return null
        val scaled = scaleDown(raw, SAMPLE_EDGE)
        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getRGB(0, 0, w, h, pixels, 0, w)
        return seedFromPixels(pixels)
    }

    private fun scaleDown(src: BufferedImage, maxEdge: Int): BufferedImage {
        val w = src.width
        val h = src.height
        if (w <= maxEdge && h <= maxEdge) {
            return if (src.type == BufferedImage.TYPE_INT_ARGB || src.type == BufferedImage.TYPE_INT_RGB) {
                src
            } else {
                val copy = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                val g = copy.createGraphics()
                g.drawImage(src, 0, 0, null)
                g.dispose()
                copy
            }
        }
        val scale = maxEdge.toDouble() / max(w, h).toDouble()
        val nw = max(1, (w * scale).toInt())
        val nh = max(1, (h * scale).toInt())
        val scaled = src.getScaledInstance(nw, nh, Image.SCALE_SMOOTH)
        val out = BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(scaled, 0, 0, null)
        g.dispose()
        return out
    }

    private fun quantizeArgb(argb: Int): Int {
        val shift = 8 - QUANT_BITS
        val r = ((argb ushr 16) and 0xFF) ushr shift shl shift
        val g = ((argb ushr 8) and 0xFF) ushr shift shl shift
        val b = (argb and 0xFF) ushr shift shl shift
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun scoreSeed(populations: Map<Int, Int>): Int {
        if (populations.isEmpty()) return FALLBACK_SEED_ARGB
        val total = populations.values.sum().coerceAtLeast(1).toDouble()
        var best = FALLBACK_SEED_ARGB
        var bestScore = Double.NEGATIVE_INFINITY
        for ((color, pop) in populations) {
            val chroma = approxChroma(color)
            if (chroma < CUTOFF_CHROMA) continue
            val proportion = pop / total
            val chromaWeight = if (chroma >= TARGET_CHROMA) {
                0.3 * (chroma - TARGET_CHROMA)
            } else {
                0.1 * (chroma - TARGET_CHROMA)
            }
            val score = 0.7 * proportion * 100.0 + chromaWeight
            if (score > bestScore) {
                bestScore = score
                best = color
            }
        }
        return if (bestScore == Double.NEGATIVE_INFINITY) FALLBACK_SEED_ARGB else best
    }

    /** Cheap RGB chroma proxy (max−min channel), scaled ~0–100 like Material chroma order-of-magnitude. */
    private fun approxChroma(argb: Int): Double {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return (max(r, max(g, b)) - min(r, min(g, b))).toDouble() * (100.0 / 255.0)
    }

    fun deriveContainers(
        seedArgb: Int,
        digest: String,
        wallpaperPath: String?,
        fromFallback: Boolean,
    ): WallpaperTonalPalette {
        val hsl = rgbToHsl(seedArgb)
        val primary = hslToRgb(hsl[0], min(1f, hsl[1] * 1.05f), 0.55f)
        val primaryContainer = hslToRgb(hsl[0], min(1f, hsl[1] * 0.85f), 0.28f)
        val secondaryContainer = hslToRgb((hsl[0] + 40f) % 360f, min(1f, hsl[1] * 0.7f), 0.26f)
        // Opaque tonal surface — keep RGB tone near Helix surfaceContainer (#241E36).
        val surfaceContainer = hslToRgb(hsl[0], min(0.35f, hsl[1] * 0.45f), 0.16f)
        return WallpaperTonalPalette(
            seedArgb = seedArgb or 0xFF000000.toInt(),
            primaryArgb = primary,
            primaryContainerArgb = primaryContainer,
            secondaryContainerArgb = secondaryContainer,
            surfaceContainerArgb = surfaceContainer,
            digest = digest,
            wallpaperPath = wallpaperPath,
            fromFallback = fromFallback,
        )
    }

    private fun rgbToHsl(argb: Int): FloatArray {
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val l = (maxC + minC) / 2f
        if (abs(maxC - minC) < 1e-6f) return floatArrayOf(0f, 0f, l)
        val d = maxC - minC
        val s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
        val h = when (maxC) {
            r -> ((g - b) / d + if (g < b) 6f else 0f) / 6f
            g -> ((b - r) / d + 2f) / 6f
            else -> ((r - g) / d + 4f) / 6f
        }
        return floatArrayOf(h * 360f, s, l)
    }

    private fun hslToRgb(hDeg: Float, s: Float, l: Float): Int {
        val h = ((hDeg % 360f) + 360f) % 360f / 360f
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        fun hue2rgb(t0: Float): Float {
            var t = t0
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            return when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < 1f / 2f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
        }
        val r = (hue2rgb(h + 1f / 3f) * 255f).toInt().coerceIn(0, 255)
        val g = (hue2rgb(h) * 255f).toInt().coerceIn(0, 255)
        val b = (hue2rgb(h - 1f / 3f) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
