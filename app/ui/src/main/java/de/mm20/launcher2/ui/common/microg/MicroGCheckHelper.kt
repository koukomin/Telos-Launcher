package de.mm20.launcher2.ui.common.microg

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import de.mm20.launcher2.applock.SettingsDeepLinkContract

object MicroGCheckHelper {
    private const val GMS_PACKAGE = "com.google.android.gms"

    fun ensureGoogleServices(context: Context, onReady: () -> Unit) {
        if (!isGmsInstalled(context)) {
            Toast.makeText(context, "MicroG (GmsCore) is required for Google Drive. Please download it from Telos Store.", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent().setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                intent.putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, "store_dashboard")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {}
            return
        }

        val accountManager = AccountManager.get(context)
        val googleAccounts = accountManager.getAccountsByType("com.google")
        if (googleAccounts.isEmpty()) {
            Toast.makeText(context, "No Google account found. Opening MicroG to sign in...", Toast.LENGTH_LONG).show()
            openMicroGAccounts(context)
            return
        }

        onReady()
    }

    fun addGoogleAccount(context: Context) {
        if (!isGmsInstalled(context)) {
            Toast.makeText(context, "MicroG (GmsCore) is required. Please install it from Telos Store first.", Toast.LENGTH_LONG).show()
            return
        }
        openMicroGAccounts(context)
    }

    private fun isGmsInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(GMS_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openMicroGAccounts(context: Context) {
        try {
            val intent = Intent().apply {
                setClassName(GMS_PACKAGE, "org.microg.gms.settings.accounts.AccountsActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent("com.google.android.gms.settings.GOOGLE_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Could not open MicroG settings.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
