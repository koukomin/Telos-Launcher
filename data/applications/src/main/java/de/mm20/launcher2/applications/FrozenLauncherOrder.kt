package de.mm20.launcher2.applications

/** Preserve the real launcher component instead of selecting an arbitrary disabled icon alias. */
internal fun <T> orderFrozenLaunchers(
    candidates: List<T>, previousKeys: Set<String>, key: (T) -> String, enabled: (T) -> Boolean,
): List<T> = candidates.sortedWith(compareByDescending<T> { key(it) in previousKeys }.thenByDescending { enabled(it) })
