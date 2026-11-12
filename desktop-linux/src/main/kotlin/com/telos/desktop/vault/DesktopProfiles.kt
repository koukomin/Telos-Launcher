package com.telos.desktop.vault

import com.telos.desktop.network.DesktopNetworkController
import java.io.File
import java.util.Locale

/**
 * Light VOS-1 scaffolding: vault profile folders and the active profile name.
 */
object DesktopProfiles {
    data class ProfileFolder(val name: String, val directory: File)

    fun profilesRoot(): File = File(DesktopNetworkController.configHome(), "profiles").apply { mkdirs() }

    fun listProfileFolders(): List<ProfileFolder> {
        val root = profilesRoot()
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            ?.map { ProfileFolder(it.name, it) }
            .orEmpty()
    }

    fun activeProfile(vaultConfigDir: File? = null): String? {
        val dir = vaultConfigDir ?: runCatching { PhantomVault.engine.configDir }.getOrNull()
        return DesktopNetworkController.activeProfileName(dir)
    }
}
