package com.telos.desktop.theme

/**
 * Day/night theme preference for Telos Desktop VOS.
 * Stored as `theme.dayNight` in vault `desktop.properties`.
 *
 * - [Off]: do not auto-flip; leave chrome as last applied / Quiet Ops default
 * - [Auto]: Paper Ops by day, Quiet Ops by night (timezone-local)
 * - [Light]: force Paper Ops
 * - [Dark]: force Quiet Ops
 */
enum class DayNightMode(val propertyValue: String) {
    Off("off"),
    Auto("auto"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun parse(raw: String?): DayNightMode = when (raw?.trim()?.lowercase()) {
            "auto", "automatic" -> Auto
            "light", "paper", "day", "paper_ops", "paper-ops" -> Light
            "dark", "quiet", "night", "quiet_ops", "quiet-ops" -> Dark
            else -> Off
        }
    }
}

/** Effective chrome intensity after day/night resolution. */
enum class DayNightPhase(val intensityProperty: String) {
    /** Dark charcoal Quiet Ops. */
    QuietOps("quiet"),
    /** Light Paper Ops. */
    PaperOps("paper"),
    ;
}
