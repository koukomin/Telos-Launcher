package com.telos.desktop.theme

import java.util.Properties

/**
 * Helix panel workspace accent strips (Tier B #10).
 *
 * Workspaces 1–4 map to the Cyber-Tech neon palette at low alpha under the panel digits.
 * Preference key: `theme.workspaceTints` (default on). Panic mute hides strips; Quiet Ops
 * only lowers alpha when those keys are present.
 */
object WorkspaceTintStrips {
    const val PREF_KEY = "theme.workspaceTints"

    /** Neon Green, Cyan, Magenta, Orange — opaque RGB (no alpha). */
    val WORKSPACE_RGB: LongArray = longArrayOf(
        0x00E676L, // 1 — Neon Green
        0x00F0FFL, // 2 — Neon Cyan
        0xFF007FL, // 3 — Neon Magenta
        0xFF9100L, // 4 — Neon Orange
    )

    private val PANIC_MUTE_KEYS = listOf(
        "theme.panicMute",
        "theme.panic_mute",
        "panicMute",
        "theme.muteNeon",
    )

    private val QUIET_OPS_BOOL_KEYS = listOf(
        "theme.quietOps",
        "theme.quiet_ops",
        "quietOps",
    )

    /**
     * RGB for a 1-based workspace index, or null outside 1..4.
     */
    fun rgbForWorkspace(oneBased: Int): Long? {
        if (oneBased !in 1..4) return null
        return WORKSPACE_RGB[oneBased - 1]
    }

    /**
     * Pack ARGB with [alpha01] (0..1) for Compose / panel drawing.
     */
    fun argbForWorkspace(oneBased: Int, alpha01: Float): Long? {
        val rgb = rgbForWorkspace(oneBased) ?: return null
        val a = (alpha01.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        return (a.toLong() shl 24) or (rgb and 0xFFFFFFL)
    }

    fun stripAlpha(quietOps: Boolean, active: Boolean): Float = when {
        quietOps && active -> 0.28f
        quietOps -> 0.16f
        active -> 0.40f
        else -> 0.26f
    }

    fun shouldShow(workspaceTintsEnabled: Boolean, panicMute: Boolean): Boolean =
        workspaceTintsEnabled && !panicMute

    fun parseWorkspaceTints(p: Properties, default: Boolean = true): Boolean {
        val raw = p.getProperty(PREF_KEY) ?: return default
        return raw.equals("true", ignoreCase = true)
    }

    /** Defensive: only true when an explicit mute key is present and true. */
    fun parsePanicMute(p: Properties): Boolean =
        PANIC_MUTE_KEYS.any { key ->
            p.getProperty(key)?.equals("true", ignoreCase = true) == true
        }

    /**
     * Defensive Quiet Ops detection. Missing keys → false (do not treat Quiet Ops
     * as implied default; Theme Studio may own intensity later).
     */
    fun parseQuietOps(p: Properties): Boolean {
        if (QUIET_OPS_BOOL_KEYS.any { key ->
                p.getProperty(key)?.equals("true", ignoreCase = true) == true
            }
        ) {
            return true
        }
        val intensity = p.getProperty("theme.intensity")?.lowercase()?.trim().orEmpty()
        return intensity == "quiet" || intensity == "quiet_ops" || intensity == "quietops"
    }
}
