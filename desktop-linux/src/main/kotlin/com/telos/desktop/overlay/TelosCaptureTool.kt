package com.telos.desktop.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.telos.desktop.i18n.TelosI18n.t
import com.telos.desktop.linux.Sys
import com.telos.desktop.vault.PortableEnvironmentIsolator
import com.telos.desktop.vault.PortableTraceGuard
import com.telos.desktop.xdg.XdgUserDirsManager
import java.awt.Color
import java.awt.Rectangle
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

enum class CaptureMode { Full, Region, Window }

/**
 * Spectacle-class desktop screenshots with vault-first privacy defaults:
 * owner-only PNGs under Pictures/Screenshots, clipboard opt-in only.
 */
object TelosCaptureTool {
    /** Session UI toggle; also honors TELOS_SHOT_CLIPBOARD=1. */
    var copyToClipboard by mutableStateOf(envClipboardEnabled())

    /** When true, capture lands in a temp file and [TelosScreenshotOverlay] opens for redaction. */
    var redactBeforeSave by mutableStateOf(false)

    var menuOpen by mutableStateOf(false)

    /** Pending PNG awaiting optional redaction before final vault save. */
    var pendingRedact by mutableStateOf<File?>(null)

    fun envClipboardEnabled(): Boolean =
        System.getenv("TELOS_SHOT_CLIPBOARD")?.trim() == "1"

    fun clipboardWanted(): Boolean = copyToClipboard || envClipboardEnabled()

    /**
     * Vault Pictures/Screenshots when the session HOME is the vault, else portable
     * data Pictures/Screenshots, else XDG Pictures/Screenshots.
     */
    fun screenshotsDirectory(homeOverride: File? = null): File {
        val pics = picturesRoot(homeOverride)
        val dir = File(pics, "Screenshots")
        dir.mkdirs()
        lockPrivateDir(dir)
        return dir
    }

    fun picturesRoot(homeOverride: File? = null): File {
        homeOverride?.let { return File(it, "Pictures") }
        PortableEnvironmentIsolator.vaultHome?.takeIf { PortableEnvironmentIsolator.active }?.let {
            return File(it, "Pictures")
        }
        if (PortableTraceGuard.isPortable()) {
            return File(PortableTraceGuard.dataRoot(), "Pictures")
        }
        return XdgUserDirsManager.resolve().pictures
    }

    fun dest(dir: File = screenshotsDirectory()): File {
        dir.mkdirs()
        val name = "telos-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".png"
        val file = File(dir, name)
        if (!file.exists()) file.createNewFile()
        lockPrivate(file)
        return file
    }

    fun lockPrivate(file: File) {
        file.parentFile?.mkdirs()
        if (!file.exists()) file.createNewFile()
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
        runCatching {
            val path = file.toPath()
            if (Files.getFileStore(path).supportsFileAttributeView(
                    java.nio.file.attribute.PosixFileAttributeView::class.java,
                )
            ) {
                Files.setPosixFilePermissions(
                    path,
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                )
            }
        }
    }

    fun lockPrivateDir(dir: File) {
        dir.mkdirs()
        dir.setReadable(false, false)
        dir.setWritable(false, false)
        dir.setExecutable(false, false)
        dir.setReadable(true, true)
        dir.setWritable(true, true)
        dir.setExecutable(true, true)
        runCatching {
            val path = dir.toPath()
            if (Files.getFileStore(path).supportsFileAttributeView(
                    java.nio.file.attribute.PosixFileAttributeView::class.java,
                )
            ) {
                Files.setPosixFilePermissions(
                    path,
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                    ),
                )
            }
        }
    }

    fun openMenu() {
        menuOpen = true
    }

    fun fullscreen(): File? = capture(CaptureMode.Full)

    fun area(): File? = capture(CaptureMode.Region)

    fun window(): File? = capture(CaptureMode.Window)

    fun capture(mode: CaptureMode): File? {
        menuOpen = false
        val staging = if (redactBeforeSave) {
            File.createTempFile("telos-shot-", ".png").also { lockPrivate(it) }
        } else {
            dest()
        }
        val ok = when (mode) {
            CaptureMode.Full -> captureFullscreen(staging)
            CaptureMode.Region -> captureArea(staging)
            CaptureMode.Window -> captureWindow(staging)
        }
        if (!ok || !staging.isFile || staging.length() == 0L) {
            staging.delete()
            TelosNotificationCenter.post(t("desktop_shot_title"), t("desktop_shot_failed"))
            return null
        }
        lockPrivate(staging)
        if (redactBeforeSave) {
            pendingRedact = staging
            return staging
        }
        return finalizeCapture(staging)
    }

    /** Move a staged/redacted PNG into the vault Screenshots folder and apply privacy defaults. */
    fun finalizeCapture(source: File, alreadyInDest: Boolean = false): File? {
        val out = if (alreadyInDest || source.parentFile?.canonicalFile == screenshotsDirectory().canonicalFile) {
            source
        } else {
            val target = dest()
            if (!source.renameTo(target)) {
                source.copyTo(target, overwrite = true)
                source.delete()
            }
            target
        }
        lockPrivate(out)
        maybeClipboard(out)
        TelosNotificationCenter.post(t("desktop_shot_title"), t("desktop_shot_saved") + ": " + out.name)
        pendingRedact = null
        return out
    }

    fun cancelPending() {
        pendingRedact?.delete()
        pendingRedact = null
    }

    /**
     * Black out [rect] (pixel coords in the image) and save into the vault Screenshots folder.
     */
    fun applyRedactionAndSave(source: File, rect: Rectangle): File? {
        if (rect.width <= 0 || rect.height <= 0) {
            return finalizeCapture(source)
        }
        val img = ImageIO.read(source) ?: return finalizeCapture(source)
        val g = img.createGraphics()
        g.color = Color.BLACK
        val x = rect.x.coerceIn(0, img.width - 1)
        val y = rect.y.coerceIn(0, img.height - 1)
        val w = rect.width.coerceAtMost(img.width - x)
        val h = rect.height.coerceAtMost(img.height - y)
        g.fillRect(x, y, w, h)
        g.dispose()
        val out = dest()
        ImageIO.write(img, "png", out)
        source.delete()
        return finalizeCapture(out, alreadyInDest = true)
    }

    internal fun captureFullscreen(file: File): Boolean {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("mac") || os.contains("darwin")) {
            return Sys.which("screencapture")?.let {
                Sys.run(it, "-x", file.absolutePath, timeoutSec = 15).ok
            } == true
        }
        if (tryTools(
                listOf(
                    listOf("grim", "-t", "png", file.absolutePath),
                    listOf("spectacle", "-b", "-n", "-f", "-o", file.absolutePath),
                    listOf("gnome-screenshot", "-f", file.absolutePath),
                    listOf("scrot", file.absolutePath),
                    listOf("import", "-window", "root", file.absolutePath),
                ),
            )
        ) {
            return true
        }
        return false
    }

    internal fun captureArea(file: File): Boolean {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("mac") || os.contains("darwin")) {
            return Sys.which("screencapture")?.let {
                Sys.run(it, "-i", "-x", file.absolutePath, timeoutSec = 60).ok
            } == true
        }
        if (Sys.which("grim") != null && Sys.which("slurp") != null) {
            val geo = Sys.run("slurp", timeoutSec = 60).stdout.trim()
            if (geo.isNotBlank() && Sys.run("grim", "-t", "png", "-g", geo, file.absolutePath, timeoutSec = 8).ok) {
                return true
            }
        }
        return tryTools(
            listOf(
                listOf("spectacle", "-b", "-n", "-r", "-o", file.absolutePath),
                listOf("gnome-screenshot", "-a", "-f", file.absolutePath),
                listOf("scrot", "-s", file.absolutePath),
                listOf("import", file.absolutePath),
            ),
            timeoutSec = 90,
        )
    }

    internal fun captureWindow(file: File): Boolean {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("mac") || os.contains("darwin")) {
            return Sys.which("screencapture")?.let {
                Sys.run(it, "-w", "-x", file.absolutePath, timeoutSec = 60).ok
            } == true
        }
        // Wayland: select a window region with slurp+grim when possible.
        if (Sys.which("grim") != null && Sys.which("slurp") != null) {
            val geo = Sys.run("slurp", timeoutSec = 60).stdout.trim()
            if (geo.isNotBlank() && Sys.run("grim", "-t", "png", "-g", geo, file.absolutePath, timeoutSec = 8).ok) {
                return true
            }
        }
        return tryTools(
            listOf(
                listOf("spectacle", "-b", "-n", "-a", "-o", file.absolutePath),
                listOf("gnome-screenshot", "-w", "-f", file.absolutePath),
                listOf("import", file.absolutePath),
            ),
            timeoutSec = 90,
        )
    }

    private fun tryTools(candidates: List<List<String>>, timeoutSec: Long = 20): Boolean {
        for (args in candidates) {
            val bin = args.firstOrNull() ?: continue
            if (Sys.which(bin) == null) continue
            if (Sys.run(*args.toTypedArray(), timeoutSec = timeoutSec).ok) return true
        }
        return false
    }

    private fun maybeClipboard(file: File) {
        if (!clipboardWanted()) return
        val path = com.telos.desktop.session.UnlockCli.shQuote(file.absolutePath)
        when {
            Sys.which("wl-copy") != null ->
                Sys.run("sh", "-c", "wl-copy --type image/png < $path", timeoutSec = 8)
            Sys.which("xclip") != null ->
                Sys.run("sh", "-c", "xclip -selection clipboard -t image/png -i $path", timeoutSec = 8)
            Sys.which("pbcopy") != null ->
                // macOS pbcopy is text-oriented; best-effort via osascript file reference is skipped for privacy.
                Unit
        }
    }
}
