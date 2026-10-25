package de.mm20.launcher2.freeze

private val componentPackage = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")
private val componentClass = Regex("[A-Za-z0-9_.$]+")

/** Component names eventually reach a privileged shell; accept identifiers, never command text. */
internal fun validComponentEdit(pkg: String, name: String, state: Int, protected: Set<String>): Boolean =
    pkg !in protected && componentPackage.matches(pkg) && componentClass.matches(name) && state in 0..2
