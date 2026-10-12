package de.mm20.launcher2.ui.files

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * RAR support for Telos Files, shared by all flavors. The reader itself ([RarBackend]) exists once per
 * flavor: "default" reads RAR and RAR5 with libarchive (prebuilt native code), "fdroid" has a stub with the
 * same API because F-Droid does not accept prebuilt binaries. Telos only reads RAR, it never creates it.
 */

/** The RAR archive is encrypted (file names or file data). libarchive cannot decrypt RAR, so there is no password to ask for. */
class ArchiveRarEncryptedException : IOException("Encrypted RAR archives are not supported")

/** This build has no RAR reader (the F-Droid flavor) */
class ArchiveRarUnavailableException : IOException("RAR archives are not supported in this build")

/** One entry of a RAR archive. [isLink] is true for symbolic and hard links and special files: Telos never lists or unpacks those. */
class RarEntry(
    val path: String,
    val isDir: Boolean,
    val isLink: Boolean,
    val size: Long,
    val modified: Long,
    val encrypted: Boolean,
)

/** A RAR archive that is read front to back. Closing it releases the native reader. */
interface RarSession : Closeable {
    /** The next entry, or null at the end. Throws [ArchiveRarEncryptedException] when the headers are encrypted. */
    fun next(): RarEntry?

    /** Writes the data of the entry that [next] returned to [dest] (created or replaced) */
    fun copyCurrentTo(dest: File)

    /**
     * The data of the entry that [next] returned, as a stream. From here on the session belongs to the
     * stream: closing the stream closes the session, and [next] must not be called any more.
     */
    fun openCurrent(): InputStream
}

/** Pure helpers for the error messages of the RAR reader, so they can be unit tested */
object RarErrors {
    /** Whether a libarchive error message says that the archive is encrypted (and libarchive cannot decrypt it) */
    fun isEncryption(message: String?): Boolean {
        val m = message?.lowercase() ?: return false
        return "encrypt" in m || "password" in m || "decryption" in m
    }
}
