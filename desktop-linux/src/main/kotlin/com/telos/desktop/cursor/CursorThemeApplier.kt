package com.telos.desktop.cursor

import com.telos.desktop.icons.FreedesktopThemeRoots
import com.telos.desktop.platform.PlatformEnvironment
import com.telos.desktop.platform.TelosRuntimePlatform
import java.awt.Cursor
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class CursorApplyMode {
    /** Compose-drawn Sweet/Breeze overlay (no XCursor). */
    ComposePreset,
    /** XCursor frames loaded into an AWT custom cursor (Linux best-effort). */
    XCursorAwt,
    /** Platform system AWT cursor mapping only (Windows / macOS honesty path). */
    PlatformSystem,
}

data class CursorApplyResult(
    val mode: CursorApplyMode,
    val theme: String?,
    val size: Int,
    val awtCursor: Cursor?,
    val note: String,
)

/**
 * Applies KDE-compatible cursor theme + size for the Telos Desktop session.
 *
 * Linux: sets `XCURSOR_THEME` / `XCURSOR_SIZE` for child processes and tries to
 * decode an XCursor `left_ptr` frame into an AWT custom cursor for the Compose
 * window. Windows / macOS: maps pointer states to stock AWT cursors; XCursor
 * packs are not pushed into the native OS pointer.
 *
 * Cold start: [apply] only resolves the theme name and publishes env / default
 * index. XCursor theme scans and frame decode run on first [cursorForState]
 * (or [ensureDecoded]) so Main's unlock path stays light.
 */
object CursorThemeApplier {
    @Volatile
    var activeTheme: String? = null
        private set
    @Volatile
    var activeSize: Int = 24
        private set
    @Volatile
    var lastResult: CursorApplyResult? = null
        private set

    @Volatile
    private var decodePending: Boolean = false

    private val awtByState = ConcurrentHashMap<CursorState, Cursor>()

    private val shapeAliases = mapOf(
        CursorState.DEFAULT_ARROW to listOf("left_ptr", "default", "arrow"),
        CursorState.POINTER_HAND to listOf("hand2", "pointer", "hand1"),
        CursorState.TEXT_IBEAM to listOf("xterm", "text", "ibeam"),
        CursorState.RESIZE_E to listOf("right_side", "sb_h_double_arrow", "size_hor"),
        CursorState.RESIZE_W to listOf("left_side", "sb_h_double_arrow", "size_hor"),
        CursorState.RESIZE_N to listOf("top_side", "sb_v_double_arrow", "size_ver"),
        CursorState.RESIZE_S to listOf("bottom_side", "sb_v_double_arrow", "size_ver"),
        CursorState.RESIZE_NE to listOf("top_right_corner", "size_bdiag"),
        CursorState.RESIZE_NW to listOf("top_left_corner", "size_fdiag"),
        CursorState.RESIZE_SE to listOf("bottom_right_corner", "size_fdiag"),
        CursorState.RESIZE_SW to listOf("bottom_left_corner", "size_bdiag"),
        CursorState.GRAB to listOf("fleur", "grabbing", "move"),
        CursorState.LOADING_RING to listOf("watch", "wait", "left_ptr_watch"),
    )

    fun apply(
        themeName: String?,
        size: Int,
        followSystemTheme: Boolean,
        hostTheme: String? = null,
    ): CursorApplyResult {
        val kdeSize = KdeCursorConfig.nearestKdeSize(size)
        activeSize = kdeSize
        val resolvedName = when {
            !themeName.isNullOrBlank() -> themeName.trim()
            followSystemTheme && !hostTheme.isNullOrBlank() -> hostTheme.trim()
            followSystemTheme -> KdeCursorConfig.load().theme
            else -> null
        }
        activeTheme = resolvedName
        awtByState.clear()
        decodePending = false

        val platform = PlatformEnvironment.current
        if (platform == TelosRuntimePlatform.WindowsDesktop || platform == TelosRuntimePlatform.MacDesktop) {
            val stock = Cursor.getDefaultCursor()
            applyToAwtWindows(stock)
            publishEnv(resolvedName, kdeSize)
            val note = if (platform == TelosRuntimePlatform.WindowsDesktop) {
                "Windows: stock AWT cursors only; XCursor themes are not applied to the Win32 pointer."
            } else {
                "macOS: stock AWT cursors only; XCursor themes are not applied to the Cocoa pointer."
            }
            return finish(
                CursorApplyResult(CursorApplyMode.PlatformSystem, resolvedName, kdeSize, stock, note),
            )
        }

        if (resolvedName.isNullOrBlank()) {
            publishEnv(null, kdeSize)
            return finish(
                CursorApplyResult(
                    CursorApplyMode.ComposePreset,
                    null,
                    kdeSize,
                    null,
                    "No XCursor theme selected; Compose overlay presets are used.",
                ),
            )
        }

        // Defer icon-root scan, default/index write, and XCursor decode until first pointer use.
        publishEnv(resolvedName, kdeSize)
        decodePending = true
        return finish(
            CursorApplyResult(
                CursorApplyMode.ComposePreset,
                resolvedName,
                kdeSize,
                null,
                "XCursor env published; theme scan/decode deferred until first pointer use.",
            ),
        )
    }

    /** Whether an XCursor decode is still outstanding after [apply]. */
    fun isDecodePending(): Boolean = decodePending

    /**
     * Completes deferred XCursor discovery/decode. Safe to call repeatedly;
     * no-op when nothing is pending. Runs disk I/O — call from Dispatchers.IO
     * when on a UI path.
     */
    fun ensureDecoded(): CursorApplyResult? {
        if (!decodePending) return lastResult
        synchronized(this) {
            if (!decodePending) return lastResult
            val resolvedName = activeTheme
            val kdeSize = activeSize
            if (resolvedName.isNullOrBlank()) {
                decodePending = false
                return lastResult
            }
            maybeWriteDefaultIndex(resolvedName)
            val themes = XCursorThemeDiscovery.listInstalled().associateBy { it.name }
            val theme = XCursorThemeDiscovery.find(resolvedName)
            if (theme == null) {
                decodePending = false
                return finish(
                    CursorApplyResult(
                        CursorApplyMode.ComposePreset,
                        resolvedName,
                        kdeSize,
                        null,
                        "Theme \"$resolvedName\" not found under icon roots; Compose overlay used. XCURSOR_* still exported for children.",
                    ),
                )
            }

            val file = shapeAliases[CursorState.DEFAULT_ARROW]
                ?.firstNotNullOfOrNull { XCursorThemeDiscovery.resolveCursorFile(theme, it, themes) }
            val frame = file?.let { XCursorReader.loadBestFrame(it, kdeSize) }
            val awt = if (frame != null) {
                runCatching {
                    Toolkit.getDefaultToolkit().createCustomCursor(
                        frame.image,
                        Point(frame.xHot.coerceIn(0, frame.width - 1), frame.yHot.coerceIn(0, frame.height - 1)),
                        resolvedName,
                    )
                }.getOrNull()
            } else null

            decodePending = false
            if (awt != null) {
                awtByState[CursorState.DEFAULT_ARROW] = awt
                applyToAwtWindows(awt)
                return finish(
                    CursorApplyResult(
                        CursorApplyMode.XCursorAwt,
                        resolvedName,
                        kdeSize,
                        awt,
                        "Loaded XCursor frame into AWT cursor; child processes get XCURSOR_THEME/SIZE.",
                    ),
                )
            }
            return finish(
                CursorApplyResult(
                    CursorApplyMode.ComposePreset,
                    resolvedName,
                    kdeSize,
                    null,
                    "Could not decode XCursor frames for AWT; Compose overlay used. XCURSOR_* exported for children.",
                ),
            )
        }
    }

    private fun finish(result: CursorApplyResult): CursorApplyResult {
        lastResult = result
        CursorUiState.bump()
        return result
    }

    fun cursorForState(state: CursorState): Cursor {
        ensureDecoded()
        awtByState[state]?.let { return it }
        val last = lastResult
        if (last?.mode == CursorApplyMode.XCursorAwt && state == CursorState.DEFAULT_ARROW && last.awtCursor != null) {
            return last.awtCursor
        }
        if (last?.mode == CursorApplyMode.XCursorAwt) {
            val themeName = last.theme ?: return stockFor(state)
            val theme = XCursorThemeDiscovery.find(themeName) ?: return stockFor(state)
            val themes = XCursorThemeDiscovery.listInstalled().associateBy { it.name }
            val file = shapeAliases[state]
                ?.firstNotNullOfOrNull { XCursorThemeDiscovery.resolveCursorFile(theme, it, themes) }
                ?: return stockFor(state)
            val frame = XCursorReader.loadBestFrame(file, last.size) ?: return stockFor(state)
            val cursor = runCatching {
                Toolkit.getDefaultToolkit().createCustomCursor(
                    frame.image,
                    Point(frame.xHot.coerceIn(0, frame.width - 1), frame.yHot.coerceIn(0, frame.height - 1)),
                    "$themeName-$state",
                )
            }.getOrDefault(stockFor(state))
            awtByState[state] = cursor
            return cursor
        }
        return stockFor(state)
    }

    fun inject(pb: ProcessBuilder) {
        val env = pb.environment()
        val theme = activeTheme
        if (!theme.isNullOrBlank()) {
            env["XCURSOR_THEME"] = theme
        }
        env["XCURSOR_SIZE"] = activeSize.toString()
        // Help toolkits that resolve via XDG data dirs find vault/portable installs.
        val dataHome = FreedesktopThemeRoots.installRoot().parentFile?.absolutePath
        if (dataHome != null) {
            val existing = env["XDG_DATA_DIRS"].orEmpty()
            if (!existing.contains(dataHome)) {
                env["XDG_DATA_DIRS"] = if (existing.isBlank()) {
                    "$dataHome:/usr/local/share:/usr/share"
                } else {
                    "$dataHome:$existing"
                }
            }
        }
    }

    fun usesComposeOverlay(): Boolean {
        if (decodePending) return true
        val mode = lastResult?.mode ?: return true
        return mode == CursorApplyMode.ComposePreset
    }

    private fun stockFor(state: CursorState): Cursor = when (state) {
        CursorState.POINTER_HAND -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        CursorState.TEXT_IBEAM -> Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
        CursorState.RESIZE_E, CursorState.RESIZE_W -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
        CursorState.RESIZE_N, CursorState.RESIZE_S -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
        CursorState.RESIZE_NE, CursorState.RESIZE_SW -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)
        CursorState.RESIZE_NW, CursorState.RESIZE_SE -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR)
        CursorState.GRAB -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
        CursorState.LOADING_RING -> Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
        CursorState.DEFAULT_ARROW -> Cursor.getDefaultCursor()
    }

    private fun applyToAwtWindows(cursor: Cursor) {
        runCatching {
            Window.getWindows().forEach { w ->
                w.cursor = cursor
            }
        }
    }

    private fun publishEnv(theme: String?, size: Int) {
        // Document for session helpers; Java cannot mutate the true process environ.
        runCatching {
            val runtime = System.getenv("XDG_RUNTIME_DIR")
                ?: System.getProperty("java.io.tmpdir")
            val file = File(runtime, "telos-cursor.env")
            val body = buildString {
                if (!theme.isNullOrBlank()) append("XCURSOR_THEME=").append(theme).append('\n')
                append("XCURSOR_SIZE=").append(size).append('\n')
            }
            file.writeText(body)
            file.setReadable(true, true)
            file.setWritable(true, true)
        }
        System.setProperty("telos.cursor.theme", theme.orEmpty())
        System.setProperty("telos.cursor.size", size.toString())
    }

    /**
     * Writes `…/icons/default/index.theme` Inherits= under the *session* install root
     * (vault / portable). Never writes host HOME unless Host Home boot (isolator inactive
     * and portable flag off — then installRoot is already under user.home).
     */
    private fun maybeWriteDefaultIndex(theme: String) {
        // installRoot() is already …/icons under vault, portable, or host home.
        writeDefaultIndex(FreedesktopThemeRoots.installRoot(), theme)
    }

    private fun writeDefaultIndex(iconsRoot: File, theme: String) {
        runCatching {
            val def = File(iconsRoot, "default")
            def.mkdirs()
            File(def, "index.theme").writeText(
                """
                [Icon Theme]
                Name=Default
                Comment=Telos Desktop cursor default
                Inherits=$theme
                """.trimIndent() + "\n",
            )
        }
    }
}
