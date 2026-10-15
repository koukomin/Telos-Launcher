package de.mm20.launcher2.freeze

/** Apply the new protection before clearing the old one, never briefly thawing an app. */
internal suspend fun transitionFreezeState(
    packages: List<String>,
    frozen: Boolean,
    disable: Boolean = false,
    suspendPackages: suspend (List<String>, Boolean) -> Set<String>,
    enablePackages: suspend (List<String>, Boolean) -> Set<String>,
): Set<String> {
    if (packages.isEmpty()) return emptySet()
    val requested = packages.toSet()
    if (!frozen) {
        val unsuspended = suspendPackages(packages, false).intersect(requested)
        val enabled = enablePackages(packages, true).intersect(requested)
        // Both restrictions must be cleared. A union silently reported partial failures as success.
        return unsuspended.intersect(enabled)
    }
    return if (disable) {
        val disabled = enablePackages(packages, false).intersect(requested)
        if (disabled.isEmpty()) emptySet()
        else suspendPackages(disabled.toList(), false).intersect(disabled)
    } else {
        val suspended = suspendPackages(packages, true).intersect(requested)
        if (suspended.isEmpty()) emptySet()
        else enablePackages(suspended.toList(), true).intersect(suspended)
    }
}

internal fun enabledAction(state: Int): String? = when (state) {
    0 -> "default-state"
    1 -> "enable"
    2 -> "disable"
    3 -> "disable-user"
    4 -> "disable-until-used"
    else -> null
}
