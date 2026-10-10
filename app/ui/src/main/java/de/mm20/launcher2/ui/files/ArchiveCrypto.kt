package de.mm20.launcher2.ui.files

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** The archive (or a file in it) is encrypted and no password is known yet */
open class ArchiveCryptoException(message: String? = null) : IOException(message)

class ArchivePasswordRequiredException : ArchiveCryptoException("Password required")

class ArchiveWrongPasswordException : ArchiveCryptoException("Wrong password")

/**
 * The passwords of the archives that are open right now. They live in memory only, are never written
 * to disk and are dropped when the user leaves the archive (or the file manager is closed).
 */
object ArchiveSessions {
    private val passwords = ConcurrentHashMap<String, CharArray>()

    fun get(archive: String): CharArray? = passwords[archive]
    fun has(archive: String): Boolean = passwords.containsKey(archive)
    fun set(archive: String, password: CharArray) { passwords[archive] = password.copyOf() }
    fun clear(archive: String) { passwords.remove(archive) }
    fun clearAll() { passwords.clear() }
}
