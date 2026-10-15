package de.mm20.launcher2.freeze

/** Dormant seasonal/icon aliases are normal when another launcher activity remains enabled. */
internal fun isComponentFreeze(hasEnabledLauncher: Boolean, hasDisabledComponents: Boolean) =
    !hasEnabledLauncher && hasDisabledComponents
