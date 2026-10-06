package de.mm20.launcher2.permissions

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ktx.tryStartActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import androidx.core.net.toUri

interface PermissionsManager {
    fun requestPermission(context: AppCompatActivity, permissionGroup: PermissionGroup)

    /**
     * Check if this permission is granted right now without receiving further updates
     * about the granted state.
     * @return true if the given permission group is fully granted
     */
    fun checkPermissionOnce(permissionGroup: PermissionGroup): Boolean

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    )

    fun onResume() {

    }

    fun hasPermission(permissionGroup: PermissionGroup): Flow<Boolean>

    /**
     * True once [requestPermission] has been called at least once for this permission group in
     * this app session. Android 13+ silently blocks the very first grant attempt for
     * Accessibility/Notification-listener permissions from sideloaded apps ("Restricted
     * Settings") without telling the app why, so this is used to show guidance about that
     * mechanism only after the user has actually tried once - not proactively, since most
     * permission groups don't need it and it would just be noise before that.
     */
    fun hasAttemptedRequest(permissionGroup: PermissionGroup): Flow<Boolean>

    /**
     * Special function for the Notification listener to report its status.
     * May not be called by anything else.
     */
    fun reportNotificationListenerState(running: Boolean)

    /**
     * Special function for the accessibility service to report its status.
     * May not be called by anything else.
     */
    fun reportAccessibilityServiceState(running: Boolean)
}

enum class PermissionGroup {
    Calendar,
    Tasks,
    Location,
    Contacts,
    ExternalStorage,
    Notifications,
    AppShortcuts,
    Accessibility,
    ManageProfiles,
    Call,
    CallLog,
    UsageAccess,
    OverlayWindow,
    Bluetooth,
    NotificationPolicy,
    WriteSettings,
    PhoneState,
    Camera,
}

internal class PermissionsManagerImpl(
    private val context: Context
) : PermissionsManager {

    private val pendingPermissionRequests = mutableSetOf<PermissionGroup>()

    private val calendarPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.Calendar)
    )
    private val tasksPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.Tasks)
    )
    private val contactsPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.Contacts)
    )
    private val externalStoragePermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.ExternalStorage)
    )
    private val locationPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.Location)
    )
    private val notificationsPermissionState = MutableStateFlow(false)
    private val accessibilityPermissionState = MutableStateFlow(false)
    private val appShortcutsPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.AppShortcuts)
    )
    private val manageProfilesPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.ManageProfiles)
    )
    private val callPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.Call)
    )
    private val callLogPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.CallLog)
    )
    private val usageAccessPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.UsageAccess)
    )
    private val overlayWindowPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.OverlayWindow)
    )
    private val bluetoothPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.Bluetooth)
    )
    private val notificationPolicyPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.NotificationPolicy)
    )
    private val writeSettingsPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.WriteSettings)
    )
    private val phoneStatePermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.PhoneState)
    )
    private val cameraPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.Camera)
    )

    private val accessibilityRequestAttempted = MutableStateFlow(false)
    private val notificationsRequestAttempted = MutableStateFlow(false)

    override fun requestPermission(context: AppCompatActivity, permissionGroup: PermissionGroup) {
        when (permissionGroup) {
            PermissionGroup.Calendar -> {
                ActivityCompat.requestPermissions(
                    context,
                    calendarPermissions,
                    permissionGroup.ordinal
                )
            }

            PermissionGroup.Tasks -> {
                ActivityCompat.requestPermissions(
                    context,
                    taskPermissions,
                    permissionGroup.ordinal
                )
            }

            PermissionGroup.Location -> {
                ActivityCompat.requestPermissions(
                    context,
                    locationPermissions,
                    permissionGroup.ordinal
                )
            }

            PermissionGroup.Contacts -> {
                ActivityCompat.requestPermissions(
                    context,
                    contactPermissions,
                    permissionGroup.ordinal
                )
            }

            PermissionGroup.ExternalStorage -> {
                if (isAtLeastApiLevel(Build.VERSION_CODES.R)) {
                    val intent =
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).also {
                            it.data = "package:${context.packageName}".toUri()
                        }
                    context.tryStartActivity(intent)
                    pendingPermissionRequests.add(PermissionGroup.ExternalStorage)
                } else {
                    ActivityCompat.requestPermissions(
                        context,
                        externalStoragePermissions,
                        permissionGroup.ordinal
                    )
                }
            }

            PermissionGroup.Notifications -> {
                try {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    notificationsRequestAttempted.value = true
                } catch (e: ActivityNotFoundException) {
                    CrashReporter.logException(e)
                }
            }

            PermissionGroup.ManageProfiles,
            PermissionGroup.AppShortcuts -> {
                if (isAtLeastApiLevel(29)) {
                    val roleManager = context.getSystemService<RoleManager>()
                    context.startActivityForResult(
                        roleManager!!.createRequestRoleIntent(RoleManager.ROLE_HOME),
                        permissionGroup.ordinal
                    )
                } else {
                    context.tryStartActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                }
                pendingPermissionRequests.add(PermissionGroup.AppShortcuts)
            }

            PermissionGroup.Accessibility -> {
                try {
                    context.tryStartActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    pendingPermissionRequests.add(PermissionGroup.Accessibility)
                    accessibilityRequestAttempted.value = true
                } catch (e: ActivityNotFoundException) {
                    CrashReporter.logException(e)
                }
            }

            PermissionGroup.Call -> {
                ActivityCompat.requestPermissions(
                    context,
                    callPermissions,
                    permissionGroup.ordinal
                )
            }

            PermissionGroup.UsageAccess -> {
                try {
                    context.tryStartActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    pendingPermissionRequests.add(PermissionGroup.UsageAccess)
                } catch (e: ActivityNotFoundException) {
                    CrashReporter.logException(e)
                }
            }

            PermissionGroup.OverlayWindow -> {
                try {
                    context.tryStartActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri(),
                        )
                    )
                    pendingPermissionRequests.add(PermissionGroup.OverlayWindow)
                } catch (e: ActivityNotFoundException) {
                    CrashReporter.logException(e)
                }
            }

            PermissionGroup.Bluetooth -> {
                if (isAtLeastApiLevel(31)) {
                    ActivityCompat.requestPermissions(
                        context,
                        bluetoothPermissions,
                        permissionGroup.ordinal
                    )
                }
            }

            PermissionGroup.NotificationPolicy -> {
                try {
                    context.tryStartActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    pendingPermissionRequests.add(PermissionGroup.NotificationPolicy)
                } catch (e: ActivityNotFoundException) {
                    CrashReporter.logException(e)
                }
            }

            PermissionGroup.WriteSettings -> {
                try {
                    context.tryStartActivity(
                        Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            "package:${context.packageName}".toUri(),
                        )
                    )
                    pendingPermissionRequests.add(PermissionGroup.WriteSettings)
                } catch (e: ActivityNotFoundException) {
                    CrashReporter.logException(e)
                }
            }

            PermissionGroup.PhoneState -> {
                ActivityCompat.requestPermissions(
                    context,
                    phoneStatePermissions,
                    permissionGroup.ordinal
                )
            }

            PermissionGroup.Camera -> {
                ActivityCompat.requestPermissions(
                    context,
                    cameraPermissions,
                    permissionGroup.ordinal
                )
            }
            PermissionGroup.CallLog -> {
                ActivityCompat.requestPermissions(
                    context,
                    arrayOf(
                        android.Manifest.permission.READ_CALL_LOG,
                        android.Manifest.permission.WRITE_CALL_LOG,
                    ),
                    permissionGroup.ordinal
                )
            }
        }
    }

    override fun checkPermissionOnce(permissionGroup: PermissionGroup): Boolean {
        return when (permissionGroup) {
            PermissionGroup.Calendar -> {
                calendarPermissions.all { context.checkPermission(it) }
            }

            PermissionGroup.Tasks -> {
                taskPermissions.all { context.checkPermission(it) }
            }

            PermissionGroup.Location -> {
                locationPermissions.any { context.checkPermission(it) }
            }

            PermissionGroup.Contacts -> {
                contactPermissions.all { context.checkPermission(it) }
            }

            PermissionGroup.ExternalStorage -> {
                if (isAtLeastApiLevel(Build.VERSION_CODES.R)) {
                    Environment.isExternalStorageManager()
                } else {
                    externalStoragePermissions.all { context.checkPermission(it) }
                }
            }

            PermissionGroup.Notifications -> {
                notificationsPermissionState.value
            }

            PermissionGroup.AppShortcuts -> {
                context.getSystemService<LauncherApps>()?.hasShortcutHostPermission() == true
            }

            PermissionGroup.ManageProfiles -> {
                if (isAtLeastApiLevel(29)) {
                    context.getSystemService<RoleManager>()?.isRoleHeld(RoleManager.ROLE_HOME) == true
                } else false
            }

            PermissionGroup.Accessibility -> {
                accessibilityPermissionState.value
            }

            PermissionGroup.Call -> {
                callPermissions.all { context.checkPermission(it) }
            }

            PermissionGroup.UsageAccess -> {
                val appOps = context.getSystemService<AppOpsManager>()
                val mode = appOps?.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
                mode == AppOpsManager.MODE_ALLOWED
            }

            PermissionGroup.OverlayWindow -> {
                Settings.canDrawOverlays(context)
            }

            PermissionGroup.Bluetooth -> {
                !isAtLeastApiLevel(31) || bluetoothPermissions.all { context.checkPermission(it) }
            }

            PermissionGroup.NotificationPolicy -> {
                context.getSystemService<NotificationManager>()
                    ?.isNotificationPolicyAccessGranted == true
            }

            PermissionGroup.WriteSettings -> {
                Settings.System.canWrite(context)
            }

            PermissionGroup.PhoneState -> {
                phoneStatePermissions.all { context.checkPermission(it) }
            }

            PermissionGroup.Camera -> {
                cameraPermissions.all { context.checkPermission(it) }
            }
            PermissionGroup.CallLog -> ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun hasPermission(permissionGroup: PermissionGroup): Flow<Boolean> {
        return when (permissionGroup) {
            PermissionGroup.Calendar -> calendarPermissionState
            PermissionGroup.Tasks -> tasksPermissionState
            PermissionGroup.Location -> locationPermissionState
            PermissionGroup.Contacts -> contactsPermissionState
            PermissionGroup.ExternalStorage -> externalStoragePermissionState
            PermissionGroup.Notifications -> notificationsPermissionState
            PermissionGroup.AppShortcuts -> appShortcutsPermissionState
            PermissionGroup.Accessibility -> accessibilityPermissionState
            PermissionGroup.ManageProfiles -> manageProfilesPermissionState
            PermissionGroup.Call -> callPermissionState
            PermissionGroup.CallLog -> callLogPermissionState
            PermissionGroup.UsageAccess -> usageAccessPermissionState
            PermissionGroup.OverlayWindow -> overlayWindowPermissionState
            PermissionGroup.Bluetooth -> bluetoothPermissionState
            PermissionGroup.NotificationPolicy -> notificationPolicyPermissionState
            PermissionGroup.WriteSettings -> writeSettingsPermissionState
            PermissionGroup.PhoneState -> phoneStatePermissionState
            PermissionGroup.Camera -> cameraPermissionState
        }
    }

    override fun hasAttemptedRequest(permissionGroup: PermissionGroup): Flow<Boolean> {
        return when (permissionGroup) {
            PermissionGroup.Accessibility -> accessibilityRequestAttempted
            PermissionGroup.Notifications -> notificationsRequestAttempted
            else -> flowOf(false)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val permissionGroup = PermissionGroup.entries.getOrNull(requestCode) ?: return
        val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        when (permissionGroup) {
            PermissionGroup.Calendar -> calendarPermissionState.value = granted
            PermissionGroup.Tasks -> tasksPermissionState.value = granted
            PermissionGroup.Location -> locationPermissionState.value = granted
            PermissionGroup.Contacts -> contactsPermissionState.value = granted
            PermissionGroup.ExternalStorage -> externalStoragePermissionState.value = granted
            PermissionGroup.Notifications -> notificationsPermissionState.value = granted
            PermissionGroup.AppShortcuts -> appShortcutsPermissionState.value = granted
            PermissionGroup.Accessibility -> accessibilityPermissionState.value = granted
            PermissionGroup.ManageProfiles -> manageProfilesPermissionState.value = granted
            PermissionGroup.Call -> callPermissionState
            PermissionGroup.CallLog -> callLogPermissionState.value = granted
            PermissionGroup.UsageAccess -> usageAccessPermissionState.value = granted
            PermissionGroup.OverlayWindow -> overlayWindowPermissionState.value = granted
            PermissionGroup.Bluetooth -> bluetoothPermissionState.value = granted
            PermissionGroup.NotificationPolicy -> notificationPolicyPermissionState.value = granted
            PermissionGroup.WriteSettings -> writeSettingsPermissionState.value = granted
            PermissionGroup.PhoneState -> phoneStatePermissionState.value = granted
            PermissionGroup.Camera -> cameraPermissionState.value = granted
        }
    }

    override fun onResume() {
        externalStoragePermissionState.value = checkPermissionOnce(PermissionGroup.ExternalStorage)
        appShortcutsPermissionState.value = checkPermissionOnce(PermissionGroup.AppShortcuts)
        manageProfilesPermissionState.value = checkPermissionOnce(PermissionGroup.ManageProfiles)
        usageAccessPermissionState.value = checkPermissionOnce(PermissionGroup.UsageAccess)
        overlayWindowPermissionState.value = checkPermissionOnce(PermissionGroup.OverlayWindow)
        bluetoothPermissionState.value = checkPermissionOnce(PermissionGroup.Bluetooth)
        notificationPolicyPermissionState.value = checkPermissionOnce(PermissionGroup.NotificationPolicy)
        writeSettingsPermissionState.value = checkPermissionOnce(PermissionGroup.WriteSettings)
    }

    override fun reportNotificationListenerState(running: Boolean) {
        notificationsPermissionState.value = running
    }

    override fun reportAccessibilityServiceState(running: Boolean) {
        accessibilityPermissionState.value = running
    }

    companion object {
        private val calendarPermissions = arrayOf(Manifest.permission.READ_CALENDAR)
        private val taskPermissions = arrayOf("org.tasks.permission.READ_TASKS")
        private val locationPermissions = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        private val contactPermissions = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
        )
        private val externalStoragePermissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        private val callPermissions = arrayOf(Manifest.permission.CALL_PHONE)
        private val phoneStatePermissions = arrayOf(Manifest.permission.READ_PHONE_STATE)
        private val cameraPermissions = arrayOf(Manifest.permission.CAMERA)
        private val bluetoothPermissions: Array<String> =
            if (isAtLeastApiLevel(31)) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()
    }
}
