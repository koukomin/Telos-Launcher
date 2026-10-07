package de.mm20.launcher2.comms.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/** Whether Telos is the phone's default SMS app, and how to ask the user to make it so. */
object SmsRole {
    fun isDefault(context: Context): Boolean =
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) == context.packageName }.getOrDefault(false)

    fun requestIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 29) {
            context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
}
