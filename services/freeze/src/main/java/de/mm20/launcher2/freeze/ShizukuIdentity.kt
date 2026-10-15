package de.mm20.launcher2.freeze

/** Root uses the installed build's real ID; ADB-backed binder calls belong to the shell. */
internal fun shizukuCallingPackage(root: Boolean, ownPackage: String): String =
    if (root) ownPackage else "com.android.shell"
