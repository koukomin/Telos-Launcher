package de.mm20.launcher2.freeze

/** Packages that exist on a destination profile and are frozen on the source profile. */
object FreezeProfileCopy {
    fun packagesToApply(sourceFrozen: Set<String>, destinationInstalled: Set<String>): Set<String> {
        if (sourceFrozen.isEmpty() || destinationInstalled.isEmpty()) return emptySet()
        return sourceFrozen.intersect(destinationInstalled)
    }
}
