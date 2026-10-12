package de.mm20.launcher2.ui.files

import java.io.File

/** F-Droid flavor: no RAR reader (it needs a prebuilt native library, which F-Droid does not accept). Same API as the "default" flavor. */
internal object RarBackend {
    const val isAvailable: Boolean = false

    fun open(file: File): RarSession = throw ArchiveRarUnavailableException()
}
