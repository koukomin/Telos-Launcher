package com.telos.desktop.vault

import com.telos.desktop.network.DesktopNetworkController
import java.io.File
import java.util.Locale
import java.util.Properties

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

    /**
     * Persist the active vault profile name and append a profile_switch audit event.
     * Does not switch the mounted volume; multi-vault UI remains future work.
     */
    fun setActiveProfile(name: String, vaultConfigDir: File? = null) {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return
        val dir = vaultConfigDir ?: runCatching { PhantomVault.engine.configDir }.getOrNull()
        if (dir != null) {
            val propsFile = File(dir, "desktop.properties")
            val props = Properties()
            if (propsFile.isFile) propsFile.inputStream().use { props.load(it) }
            props.setProperty("profile.active", cleaned)
            propsFile.parentFile?.mkdirs()
            propsFile.outputStream().use { props.store(it, "telos-desktop") }
            runCatching {
                propsFile.setReadable(false, false)
                propsFile.setWritable(false, false)
                propsFile.setReadable(true, true)
                propsFile.setWritable(true, true)
            }
        }
        val marker = File(DesktopNetworkController.configHome(), "profile.active")
        marker.parentFile?.mkdirs()
        marker.writeText(cleaned)
        TelosSessionAuditLog.append("profile_switch", cleaned)
    }
}
