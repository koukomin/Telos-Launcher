# Telos Virtual Apps Backup Guide

This document outlines the mandatory backup requirements for AI Agents and Developers when creating or modifying Telos Virtual Apps.

## The Telos Backup Engine

Telos Launcher features an integrated, zero-trust encrypted backup engine ("Telos Backup"). It handles configuration settings, SQLite databases, Room stores, SharedPreferences, DataStores, and external App Data via Shizuku/Root.

## Mandatory Requirements for AI Agents

Whenever you create a new Telos Virtual App (e.g. Telos Calculator, Telos Viewer, Telos Sync, Telos Backup), you **MUST** ensure its configuration data is registered with the Telos Backup engine.

### 1. Identify User Data and Settings
- If your virtual app uses `LauncherDataStore` (Preferences DataStore), it is automatically backed up as part of the `Launcher` group.
- If your virtual app uses a custom Room database (e.g. `CalendarDatabase`, `NotesDatabase`), you must register it as a `Backupable` component.

### 2. Implementing `Backupable`
Create a class that implements `de.mm20.launcher2.backup.Backupable`. 

```kotlin
import de.mm20.launcher2.backup.BackupGroup
import de.mm20.launcher2.backup.Backupable
import java.io.File

class MyVirtualAppBackup(private val context: Context) : Backupable {
    
    override val group = BackupGroup.VirtualApps
    
    override suspend fun backup(toDir: File) {
        // Implement logic to copy your SQLite database or JSON config to `toDir`
    }
    
    override suspend fun restore(fromDir: File) {
        // Implement logic to restore your database from `fromDir`
    }
}
```

### 3. Registering the Component
You must inject or provide your `Backupable` implementation to the dependency injection graph (Koin) so the `BackupManager` can discover it during scheduled or manual backup runs.

### 4. Zero-Trust Security
Do NOT store plain-text passwords or tokens in standard SharedPreferences. Use the `de.mm20.launcher2.preferences.Security` or Android KeyStore wrappers if your Virtual App handles credentials (e.g. SMB/WebDAV passwords). The Telos Backup engine encrypts the entire backup ZIP file (AES-256-GCM + Argon2id), but resting data must still follow Zero-Trust principles.
