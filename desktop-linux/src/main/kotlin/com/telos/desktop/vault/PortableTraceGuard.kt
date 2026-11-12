package com.telos.desktop.vault

import com.telos.desktop.platform.PlatformEnvironment
import java.io.File

/**
 * Tracks whether Telos Desktop runs in a portable data root (no host profile writes).
 */
object PortableTraceGuard {
    private var selectedRoot: File? = null

    fun isPortable(): Boolean =
        PortableEnvironmentIsolator.active ||
            selectedRoot != null ||
            (PlatformEnvironment.settings.showPortableDesktopShell &&
                System.getProperty("telos.portable.active") == "1")

    fun dataRoot(): File = selectedRoot ?: explicitOverride() ?: defaultPortableDataRoot()

    fun selectDataRoot(root: File) {
        selectedRoot = root
    }

    /** Clears an explicit portable root selection (tests / exit portable mode). */
    fun clearSelectedRoot() {
        selectedRoot = null
        System.clearProperty("telos.portable.active")
    }

    fun ensurePortableDataRoot(): File =
        dataRoot().apply { mkdirs() }.also {
            selectedRoot = it
            System.setProperty("telos.portable.active", "1")
        }

    fun explicitOverride(): File? {
        System.getProperty("telos.portable.data")?.trim()?.takeIf { it.isNotEmpty() }?.let { return File(it) }
        System.getenv("TELOS_PORTABLE_DATA")?.trim()?.takeIf { it.isNotEmpty() }?.let { return File(it) }
        return null
    }

    fun defaultPortableDataRoot(): File {
        jarSiblingDir()?.let { return File(it, "TelosPortableData") }
        val host = hostProfileHome()
        return File(host.parentFile ?: host, "TelosPortableData")
    }

    fun hostProfileHome(): File {
        System.getenv("TELOS_HOST_HOME")?.trim()?.takeIf { it.isNotEmpty() }?.let { return File(it) }
        System.getenv("USERPROFILE")?.trim()?.takeIf { it.isNotEmpty() }?.let { return File(it) }
        System.getenv("HOME")?.trim()?.takeIf { it.isNotEmpty() }?.let { return File(it) }
        return File(System.getProperty("user.home"))
    }

    fun assertNoHostWrites(allowedRoot: File) {
        val root = allowedRoot.canonicalFile
        val host = hostProfileHome().canonicalFile
        if (root == host) {
            throw IllegalStateException("Portable root must not equal host profile home")
        }
        val directHostTraces = listOf(
            File(host, ".config"),
            File(host, ".cache"),
            File(host, ".local"),
        ).map { it.canonicalFile }
        if (root in directHostTraces) {
            throw IllegalStateException("Portable root must not replace host XDG folders")
        }
    }

    fun jarSiblingDir(): File? {
        val loc = PortableTraceGuard::class.java.protectionDomain?.codeSource?.location ?: return null
        return runCatching {
            val f = File(loc.toURI())
            if (f.isFile) f.parentFile else f
        }.getOrNull()
    }
}
