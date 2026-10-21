package de.mm20.launcher2.freeze

/** Per-app timing overrides the profile only for screen-off/idle triggers. */
internal fun screenOffFreezeDelays(
    candidates: Set<String>, overrides: Map<String, Int>, screenOff: Boolean,
    idle: Boolean, idleMinutes: Int,
): Map<String, Long> = candidates.mapNotNull { pkg ->
    val minutes = overrides[pkg]?.coerceIn(0, 1440)
        ?: if (screenOff) 0 else if (idle) idleMinutes.coerceIn(1, 1440) else null
    minutes?.let { pkg to it * 60_000L }
}.toMap()
