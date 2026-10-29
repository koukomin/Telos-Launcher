package de.mm20.launcher2.profiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.LauncherUserInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ProfileWithState(
    val profile: Profile,
    val state: Profile.State,
)

class ProfileManager(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
) {
    private companion object {
        private const val TAG = "ProfileManager"
    }

    private val mutex = Mutex()

    private val userManager = context.getSystemService<UserManager>()!!
    private val launcherApps = context.getSystemService<LauncherApps>()!!

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val profileStates: MutableStateFlow<List<ProfileWithState>> =
        MutableStateFlow(emptyList())

    /**
     * List of profiles that are active and unlocked.
     */
    val activeProfiles: Flow<List<Profile>> = profileStates.map { rows ->
        rows.mapNotNull { if (it.state.locked) null else it.profile }
    }.shareIn(scope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val profiles: Flow<List<Profile>> = profileStates.map { rows ->
        rows.map { it.profile }
    }.shareIn(scope, SharingStarted.WhileSubscribed(5000), replay = 1)

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                scope.launch {
                    refreshProfiles()
                }
            }
        }
        context.registerReceiver(
            receiver, IntentFilter().apply {
                addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
                addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
                addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
                if (isAtLeastApiLevel(34)) {
                    addAction(Intent.ACTION_PROFILE_ADDED)
                    addAction(Intent.ACTION_PROFILE_REMOVED)
                }
                if (isAtLeastApiLevel(31)) {
                    addAction(Intent.ACTION_PROFILE_ACCESSIBLE)
                    addAction(Intent.ACTION_PROFILE_INACCESSIBLE)
                }

            }
        )
        scope.launch {
            if (isAtLeastApiLevel(35)) {
                permissionsManager.hasPermission(PermissionGroup.ManageProfiles).collectLatest {
                    refreshProfiles()
                }
            } else {
                refreshProfiles()
            }
        }
    }

    private suspend fun refreshProfiles() {
        mutex.withLock {
            val rows = mutableListOf<ProfileWithState>()
            for (userHandle in launcherApps.profiles) {
                val serial = userManager.getSerialNumberForUser(userHandle)
                if (android.os.Build.MANUFACTURER == "samsung" && serial == 150L) continue // Hide Samsung Secure Folder
                rows += ProfileWithState(
                    Profile(
                        type = getProfileType(userHandle),
                        userHandle = userHandle,
                        serial = serial,
                    ),
                    getProfileState(userHandle),
                )
            }
            profileStates.value = rows
        }
    }

    fun getProfile(userHandle: UserHandle): Flow<Profile?> {
        return profileStates.map { rows ->
            rows.find { it.profile.userHandle == userHandle }?.profile
        }
    }

    /**
     * Returns the first profile of the given type, or null if it doesn't exist.
     */
    fun getProfile(profileType: Profile.Type): Profile? {
        return profileStates.value.find { it.profile.type == profileType }?.profile
    }

    fun getProfileState(profile: Profile?): Flow<Profile.State?> {
        return profileStates.map { rows ->
            rows.find { it.profile == profile }?.state
        }
    }

    fun getProfileStateOnce(profile: Profile?): Profile.State? {
        return profileStates.value.find { it.profile == profile }?.state
    }

    private fun getProfileType(userHandle: UserHandle): Profile.Type {
        if (userHandle == Process.myUserHandle()) return Profile.Type.Personal
        if (isAtLeastApiLevel(35)) {
            val launcherUserInfo = launcherApps.getLauncherUserInfo(userHandle)
            return when (launcherUserInfo?.userType) {
                UserManager.USER_TYPE_PROFILE_PRIVATE -> Profile.Type.Private
                UserManager.USER_TYPE_PROFILE_MANAGED -> Profile.Type.Work
                else -> Profile.Type.Clone
            }
        }
        // Dual Apps on many OEMs use user 999. Work profiles are typically user 10.
        val id = Regex("(\\d+)").findAll(userHandle.toString()).lastOrNull()?.value?.toIntOrNull()
        if (id == 999) return Profile.Type.Clone
        return Profile.Type.Work
    }

    private fun getProfileState(userHandle: UserHandle): Profile.State {
        // === TELOS_PENDING_REVIEW_START: work_profile_quiet_mode_toggle ===
        val isQuietMode = if (isAtLeastApiLevel(24)) {
            userManager.isQuietModeEnabled(userHandle)
        } else {
            false
        }
        val locked = !userManager.isUserUnlocked(userHandle) || isQuietMode
        // === TELOS_PENDING_REVIEW_END: work_profile_quiet_mode_toggle ===
        val hidden = if (isAtLeastApiLevel(36) && locked) {
            launcherApps.getLauncherUserInfo(userHandle)
                ?.getUserConfig()
                ?.getBoolean(LauncherUserInfo.PRIVATE_SPACE_ENTRYPOINT_HIDDEN, false)
                ?: false
        } else {
            false
        }
        return Profile.State(locked = locked, hidden = hidden)
    }

    @RequiresApi(28)
    fun unlockProfile(profile: Profile) {
        try {
            userManager.requestQuietModeEnabled(false, profile.userHandle)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to unlock profile ${profile.serial}", e)
        }
    }

    @RequiresApi(28)
    fun lockProfile(profile: Profile) {
        try {
            userManager.requestQuietModeEnabled(true, profile.userHandle)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to lock profile ${profile.serial}", e)
        }
    }
}
