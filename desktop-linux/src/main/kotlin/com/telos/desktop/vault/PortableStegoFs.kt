package com.telos.desktop.vault

import java.io.File

/**
 * Portable media-carrier layout under [PortableTraceGuard] data root.
 *
 * Carriers and the optional assembled linear image live next to the portable
 * session — never under the host profile home when Portable Data mode is on.
 */
object PortableStegoFs {
    const val CARRIERS_DIR = "carriers"
    const val ASSEMBLED_IMAGE = "stego_assembled.tvault"

    fun dataRoot(): File = PortableTraceGuard.dataRoot()

    fun carriersRoot(root: File = dataRoot()): File = File(root, CARRIERS_DIR)

    fun manifestFile(root: File = dataRoot()): File =
        File(carriersRoot(root), StegoCarrierManifest.FILE_NAME)

    fun assembledImage(root: File = dataRoot()): File =
        File(root, ASSEMBLED_IMAGE)

    /**
     * Ensures the portable carriers directory exists and is outside the host
     * profile home / XDG folders.
     */
    fun ensureCarriersRoot(): File {
        val root = PortableTraceGuard.ensurePortableDataRoot()
        PortableTraceGuard.assertNoHostWrites(root)
        return carriersRoot(root).apply { mkdirs() }
    }

    /** Prefer an existing pool path; otherwise the portable carriers folder. */
    fun resolvePool(explicit: File?): File {
        if (explicit != null) return explicit
        val portable = carriersRoot()
        if (portable.isDirectory && (portable.listFiles()?.isNotEmpty() == true || manifestFile().isFile)) {
            return portable
        }
        return ensureCarriersRoot()
    }
}
