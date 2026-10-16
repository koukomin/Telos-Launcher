package de.mm20.launcher2.comms.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.provider.Settings

/** Whether Telos is the phone's default SMS app, and how to ask the user to make it so. */
object SmsRole {
    fun isDefault(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 29 && context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true) true
        else Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }.getOrDefault(false)

    fun settingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

    fun requestIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_SMS))
                roles.createRequestRoleIntent(RoleManager.ROLE_SMS)
            else settingsIntent()
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
}
