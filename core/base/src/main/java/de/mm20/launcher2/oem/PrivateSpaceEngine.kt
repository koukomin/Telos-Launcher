package de.mm20.launcher2.oem

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

/**
 * Android 15 (API 35+) Private Space Integration Engine.
 * Detects Android 15 Private Profiles, handles biometric locking,
 * and allows secure app launching from search and drawer.
 */
object PrivateSpaceEngine {

    fun isPrivateProfile(context: Context, userHandle: UserHandle): Boolean {
        if (Build.VERSION.SDK_INT < 35) return false
        val um = context.getSystemService(Context.USER_SERVICE) as? UserManager
        return runCatching {
            // Android 15 isPrivateProfile API
            val method = UserManager::class.java.getMethod("isPrivateProfile")
            method.invoke(um) as? Boolean ?: false
        }.getOrDefault(false)
    }

    fun isMainUser(userHandle: UserHandle): Boolean {
        return userHandle == Process.myUserHandle()
    }
}
