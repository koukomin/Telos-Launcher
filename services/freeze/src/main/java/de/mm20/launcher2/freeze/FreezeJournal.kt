package de.mm20.launcher2.freeze

/** A failed save must never leave an app thawed without a recovery record. */
internal suspend fun <T> saveThenThaw(entry: T, save: suspend (T) -> Unit, thaw: suspend (T) -> Boolean): Boolean {
    save(entry)
    return thaw(entry)
}

/** Never discard history until restoration was verified. */
internal suspend fun <T> restoreThenForget(entry: T, restore: suspend (T) -> Boolean, forget: suspend () -> Unit): Boolean {
    if (!restore(entry)) return false
    forget()
    return true
}

internal fun ownsFrozenUpdate(stored: String, received: String): Boolean =
    stored.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) && stored == received

internal suspend fun <T> launchWhenReady(
    attempts: Int = 40,
    find: suspend () -> T?,
    launch: (T) -> Boolean,
    wait: suspend () -> Unit = { kotlinx.coroutines.delay(125) },
): Boolean {
    require(attempts in 1..100)
    repeat(attempts) { index ->
        val target = find()
        if (target != null && launch(target)) return true
        if (index < attempts - 1) wait()
    }
    return false
}
