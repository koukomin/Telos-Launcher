# Walkthrough - Advanced Power-User Features (Step 1)

In this first step, we have established the core system-level integration layer for Telos Launcher.

## Changes Made

### 1. New Module: `:services:app-management`
We created a dedicated module to handle privileged operations and FOSS update tracking. This keeps the core launcher code clean and modular.
- [build.gradle.kts](file:///home/koukos/Projects/Telos-Launcher/services/app-management/build.gradle.kts)
- [AndroidManifest.xml](file:///home/koukos/Projects/Telos-Launcher/services/app-management/src/main/AndroidManifest.xml)

### 2. Shizuku Integration
The `ShizukuManager` class provides a high-level API to interact with system services via Shizuku's binder bridge.
- **Permission Management**: Unified flow for checking and requesting Shizuku authorization.
- **Force Stop**: Uses `IActivityManager` to stop any app package immediately.
- **Freeze/Disable**: Uses `IPackageManager` to disable apps (using `DISABLED_USER` state for standard adb-shizuku compatibility).
- [ShizukuManager.kt](file:///home/koukos/Projects/Telos-Launcher/services/app-management/src/main/java/de/mm20/launcher2/appmanagement/ShizukuManager.kt)

### 3. FOSS Update Tracker Backend
We implemented the `ObtainiumUpdateReceiver` to listen for broadcasts from the Obtainium app.
- **Action**: `com.itachi1706.obtainium.ACTION_UPDATE_AVAILABLE`
- [ObtainiumUpdateReceiver.kt](file:///home/koukos/Projects/Telos-Launcher/services/app-management/src/main/java/de/mm20/launcher2/appmanagement/ObtainiumUpdateReceiver.kt)

## Verification Results

### Build
- The project was successfully built with the new module and dependencies:
  `./gradlew :app:app:assembleDebug` - **PASSED**

### Manual Verification (via ADB)
You can verify the Obtainium receiver by running:
```bash
adb shell am broadcast -a com.itachi1706.obtainium.ACTION_UPDATE_AVAILABLE --es package_name "com.itachi1706.obtainium"
```
Check logcat for: `ObtainiumReceiver: Update available for: com.itachi1706.obtainium`

## Next Steps
In **Step 2**, we will implement the **Custom App Info UI** (Bottom Sheet) and hook up the "Force Stop" and "Freeze" buttons to the `ShizukuManager`.
