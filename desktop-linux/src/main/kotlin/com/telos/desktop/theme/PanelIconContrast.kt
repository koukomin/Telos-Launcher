package com.telos.desktop.theme

import androidx.compose.ui.graphics.Color
import com.telos.desktop.vault.PortableEnvironmentIsolator
import com.telos.desktop.vault.PortableTraceGuard
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Properties
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wallpaper-aware light/dark contrast for Helix panel glyphs and Compose-controlled
 * StatusNotifier tray labels.
 *
 * Preference key: `panel.iconContrast` = `auto` | `light` | `dark` (default `auto`).
 * In auto mode, samples the top strip of the local wallpaper (region behind the panel)
 * on [Dispatchers.IO], caches by content digest, and picks light icons on a dark strip
 * or dark icons on a light strip. Pixels never leave the device.
 *
 * Host SNI pixmaps rendered outside Compose (e.g. by `telos-sni`) are not recolored here.
 */
object PanelIconContrast {
    const val PREF_KEY = "panel.iconContrast"

    /** WCAG relative-luminance midpoint: above → dark icons, below → light icons. */
    const val LUMINANCE_MIDPOINT = 0.45

    /** Fraction of wallpaper height treated as the panel strip (top edge). */
    const val PANEL_STRIP_FRACTION = 0.08

    private const val SAMPLE_WIDTH = 64
    private const val SAMPLE_STRIP_HEIGHT = 8

    enum class Mode {
        Auto,
        Light,
        Dark,
        ;

        val propertyValue: String
            get() = when (this) {
                Auto -> "auto"
                Light -> "light"
                Dark -> "dark"
            }

        companion object {
            fun parse(raw: String?): Mode = when (raw?.trim()?.lowercase()) {
                "light" -> Light
                "dark" -> Dark
                else -> Auto
            }
        }
    }

    /** Glyph / tray label tone after resolving preference + wallpaper. */
    enum class Tone {
        /** Light glyphs for dark wallpaper strips. */
        Light,
        /** Dark glyphs for light wallpaper strips. */
        Dark,
    }

    data class Sample(
        val meanLuminance: Double,
        val tone: Tone,
        val digest: String,
        val wallpaperPath: String?,
        val fromFallback: Boolean,
    )

    @Volatile
    private var cachedDigest: String? = null

    @Volatile
    private var cachedPath: String? = null

    @Volatile
    private var cachedMode: Mode? = null

    @Volatile
    private var cachedSample: Sample? = null

    fun clearCache() {
        cachedDigest = null
        cachedPath = null
        cachedMode = null
        cachedSample = null
    }

    fun parseMode(p: Properties, default: Mode = Mode.Auto): Mode =
        Mode.parse(p.getProperty(PREF_KEY) ?: default.propertyValue)

    fun parseMode(raw: String?): Mode = Mode.parse(raw)

    /** WCAG relative luminance of an opaque sRGB ARGB pixel. Pure arithmetic for unit tests. */
    fun relativeLuminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb ushr shift) and 255) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /**
     * Map strip luminance → icon tone. Dark strip (low Y) → light icons; light strip → dark icons.
     */
    fun toneFromLuminance(meanLuminance: Double): Tone =
        if (meanLuminance > LUMINANCE_MIDPOINT) Tone.Dark else Tone.Light

    fun resolveTone(mode: Mode, meanLuminance: Double?): Tone = when (mode) {
        Mode.Light -> Tone.Light
        Mode.Dark -> Tone.Dark
        Mode.Auto -> toneFromLuminance(meanLuminance ?: 0.15)
    }

    fun glyphColor(tone: Tone): Color = when (tone) {
        Tone.Light -> Color(0xFFF5F7FA)
        Tone.Dark -> Color(0xFF14121C)
    }

    fun labelColor(tone: Tone, accent: Boolean = false): Color = when {
        accent && tone == Tone.Light -> Color(0xFF00F0FF)
        accent && tone == Tone.Dark -> Color(0xFF006B75)
        else -> glyphColor(tone)
    }

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
        PortableEnvironmentIsolator.vaultHome?.takeIf { PortableEnvironmentIsolator.active }?.let { vh ->
            val root = runCatching { vh.canonicalFile }.getOrElse { vh.absoluteFile }
            if (canonical.path.startsWith(root.path)) return true
        }
        if (PortableTraceGuard.isPortable()) {
            val root = runCatching { PortableTraceGuard.dataRoot().canonicalFile }
                .getOrElse { PortableTraceGuard.dataRoot().absoluteFile }
            return canonical.path.startsWith(root.path)
        }
        return homeOverride == null
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

    fun fallbackSample(mode: Mode, reasonPath: String? = null): Sample {
        val lum = 0.15
        return Sample(
            meanLuminance = lum,
            tone = resolveTone(mode, lum),
            digest = "fallback",
            wallpaperPath = reasonPath,
            fromFallback = true,
        )
    }

    /** Mean WCAG luminance of ARGB pixels (panel-strip samples). Visible for unit tests. */
    fun meanLuminance(pixels: IntArray): Double {
        if (pixels.isEmpty()) return 0.15
        var sum = 0.0
        for (p in pixels) sum += relativeLuminance(p)
        return sum / pixels.size
    }

    /**
     * Sample [file]'s top strip and resolve icon tone for [mode].
     * Call from [Dispatchers.IO] / background only. Digest-cached.
     */
    fun sampleFile(file: File?, mode: Mode): Sample {
        if (mode != Mode.Auto) {
            return Sample(
                meanLuminance = if (mode == Mode.Light) 0.1 else 0.9,
                tone = resolveTone(mode, null),
                digest = mode.propertyValue,
                wallpaperPath = file?.absolutePath,
                fromFallback = file == null || !file.isFile,
            )
        }
        if (file == null || !file.isFile) return fallbackSample(mode, file?.absolutePath)
        val pathKey = file.absolutePath
        val digest = runCatching { contentDigest(file) }.getOrElse {
            return fallbackSample(mode, pathKey)
        }
        synchronized(this) {
            val hit = cachedSample
            if (hit != null &&
                cachedDigest == digest &&
                cachedPath == pathKey &&
                cachedMode == mode &&
                !hit.fromFallback
            ) {
                return hit
            }
        }
        val lum = runCatching { extractStripLuminance(file) }.getOrNull()
            ?: return fallbackSample(mode, pathKey).also { putCache(pathKey, digest, mode, it) }
        val sample = Sample(
            meanLuminance = lum,
            tone = resolveTone(mode, lum),
            digest = digest,
            wallpaperPath = pathKey,
            fromFallback = false,
        )
        putCache(pathKey, digest, mode, sample)
        return sample
    }

    private fun putCache(path: String, digest: String, mode: Mode, sample: Sample) {
        synchronized(this) {
            cachedPath = path
            cachedDigest = digest
            cachedMode = mode
            cachedSample = sample
        }
    }

    suspend fun resolve(
        mode: Mode,
        wallpaperPath: String?,
        homeOverride: File? = null,
    ): Sample = withContext(Dispatchers.IO) {
        sampleFile(resolveWallpaperFile(wallpaperPath, homeOverride), mode)
    }

    private fun extractStripLuminance(file: File): Double? {
        val raw = ImageIO.read(file) ?: return null
        val stripH = max(1, (raw.height * PANEL_STRIP_FRACTION).toInt())
        val cropped = BufferedImage(raw.width, stripH, BufferedImage.TYPE_INT_ARGB)
        val g = cropped.createGraphics()
        g.drawImage(raw, 0, 0, raw.width, stripH, 0, 0, raw.width, stripH, null)
        g.dispose()
        val scaled = scaleTo(cropped, SAMPLE_WIDTH, SAMPLE_STRIP_HEIGHT)
        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getRGB(0, 0, w, h, pixels, 0, w)
        return meanLuminance(pixels)
    }

    private fun scaleTo(src: BufferedImage, tw: Int, th: Int): BufferedImage {
        if (src.width == tw && src.height == th &&
            (src.type == BufferedImage.TYPE_INT_ARGB || src.type == BufferedImage.TYPE_INT_RGB)
        ) {
            return src
        }
        val scaled = src.getScaledInstance(tw, th, Image.SCALE_SMOOTH)
        val out = BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(scaled, 0, 0, null)
        g.dispose()
        return out
    }
}
