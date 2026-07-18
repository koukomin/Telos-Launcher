package de.mm20.launcher2.freeze

/** Live OS-level state of a package, as read directly from PackageManager. */
enum class AppFreezeState {
    Normal,
    Suspended,
    Disabled,
}
