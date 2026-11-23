package de.mm20.launcher2.freeze

/** Any package that cannot start until Telos thaws it (every app, every profile). */
internal fun needsLaunchThaw(enabled: Boolean, suspended: Boolean, stopped: Boolean): Boolean =
    !enabled || suspended || stopped
