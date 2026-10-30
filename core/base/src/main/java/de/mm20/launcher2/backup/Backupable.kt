package de.mm20.launcher2.backup

import java.io.File

/**
 * The parts of a backup that can be chosen when it is created and when it is restored.
 * A backup made before groups existed only has [Launcher].
 */
enum class BackupGroup(val key: String) {
    /** Settings, favorites, widgets, themes and everything else of the launcher */
    Launcher("launcher"),
    Notes("notes"),
    Calendar("calendar"),
    /** The list and the settings of Telos Downloads (not the downloaded files, not cookies) */
    Downloads("downloads"),
    /** Application APKs, split APKs, and external app configurations */
    Apps("apps"),
    /** App Data (requires Shizuku or Root) */
    AppData("app_data"),
    /** SMS, Call Logs, Wi-Fi networks */
    SystemData("system_data"),
    /** Virtual Apps settings and configuration */
    VirtualApps("virtual_apps");

    companion object {
        fun fromKey(key: String): BackupGroup? = entries.firstOrNull { it.key == key }
    }
}

interface Backupable {
    /** The part of a backup that this component belongs to */
    val group: BackupGroup get() = BackupGroup.Launcher

    suspend fun backup(toDir: File)
    suspend fun restore(fromDir: File)
}
