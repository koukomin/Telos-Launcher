package de.mm20.launcher2.freeze

/**
 * A source of elevated privileges (Shizuku, root, ...) that can suspend and unsuspend packages.
 * All operations here are best-effort: implementations should return `false` rather than throw
 * when the underlying privilege source is unavailable or a command fails.
 */
internal interface PrivilegedAccessProvider {
    /** Whether the underlying privilege source (Shizuku service, root) is reachable at all. */
    suspend fun isAvailable(): Boolean

    /** Whether we currently hold permission to use it, without prompting the user. */
    suspend fun hasPermission(): Boolean

    /** Prompts the user if necessary. Suspends until the user responds. Returns the granted state. */
    suspend fun requestPermission(): Boolean

    /** Suspends (freezes) or unsuspends (unfreezes) the given packages for the current user. */
    suspend fun setPackagesSuspended(packageNames: List<String>, suspended: Boolean): Boolean
}
