package de.mm20.launcher2.freeze

import android.os.Process

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

    // === TELOS_PENDING_REVIEW_START: multi_user_freeze ===
    /**
     * Suspends (freezes) or unsuspends (unfreezes) the given packages for the given user.
     * @return the subset of [packageNames] that were actually toggled successfully.
     */
    suspend fun setPackagesSuspended(packageNames: List<String>, suspended: Boolean, userId: Int = Process.myUid() / 100000): Set<String>

    /**
     * Enables or disables the given packages for the given user.
     * @return the subset of [packageNames] that were actually toggled successfully.
     */
    suspend fun setPackagesEnabled(packageNames: List<String>, enabled: Boolean, userId: Int = Process.myUid() / 100000): Set<String>

    /** Force stops the given package for the given user. */
    suspend fun forceStopPackage(packageName: String, userId: Int = Process.myUid() / 100000): Boolean

    /** Clears the cache of the given package for the given user. */
    suspend fun clearCache(packageName: String, userId: Int = Process.myUid() / 100000): Boolean
    // === TELOS_PENDING_REVIEW_END: multi_user_freeze ===
}
