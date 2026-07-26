# Implementation Plan - Advanced Power-User Features (Step 1)

This plan outlines the first step in implementing advanced features for Telos Launcher: Shizuku integration and the foundation for FOSS update tracking.

## User Review Required

> [!IMPORTANT]
> This plan involves system-level interactions using Shizuku and Hidden API access. It assumes the user has Shizuku installed and configured on their device.

> [!NOTE]
> For Obtainium integration, we are assuming a broadcast action `com.itachi1706.obtainium.ACTION_UPDATE_AVAILABLE`. We will need to verify if this is the correct action supported by the user's version of Obtainium.

## Proposed Changes

### 1. Dependencies
We will add the necessary Shizuku and HiddenApiBypass dependencies to the new `services:app-management` module. These are already defined in `libs.versions.toml`.

### 2. Shizuku Integration
We will implement `ShizukuManager` to handle:
- **Authorization**: Checking if Shizuku is available and if the launcher has permission.
- **Force Stop**: Programmatically force-stopping apps via `IActivityManager`.
- **Freeze/Disable**: Toggling the app state between `ENABLED` and `DISABLED_USER` via `IPackageManager`.

### 3. FOSS Update Tracker (Foundation)
We will implement a `BroadcastReceiver` to listen for Obtainium update events.
- **Action**: `com.itachi1706.obtainium.ACTION_UPDATE_AVAILABLE`
- **Logic**: Extract package name and version info from the intent and prepare it for the launcher backend.

## Proposed Files

#### [NEW] [services/app-management/build.gradle.kts](file:///home/koukos/Projects/Telos-Launcher/services/app-management/build.gradle.kts)
#### [NEW] [ShizukuManager.kt](file:///home/koukos/Projects/Telos-Launcher/services/app-management/src/main/java/de/mm20/launcher2/appmanagement/ShizukuManager.kt)
#### [NEW] [ObtainiumUpdateReceiver.kt](file:///home/koukos/Projects/Telos-Launcher/services/app-management/src/main/java/de/mm20/launcher2/appmanagement/ObtainiumUpdateReceiver.kt)

## Verification Plan

### Automated Tests
- We will add unit tests for `ShizukuManager` where possible (mocking the binder).
- We will add unit tests for `ObtainiumUpdateReceiver` by sending mock intents.

### Manual Verification
1.  **Shizuku Authorization**: Open the launcher settings (to be implemented in Step 2) and verify that Shizuku permission can be requested and granted.
2.  **Force Stop**: Manually trigger a force stop for a test app and verify it is killed.
3.  **Freeze/Disable**: Manually trigger a freeze for a test app and verify it disappears from the system (or is disabled).
4.  **Obtainium**: Send a manual broadcast via ADB to verify the receiver works:
    `adb shell am broadcast -a com.itachi1706.obtainium.ACTION_UPDATE_AVAILABLE --es package_name "com.example.app"`
