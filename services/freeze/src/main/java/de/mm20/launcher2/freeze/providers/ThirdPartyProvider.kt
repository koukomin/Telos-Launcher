package de.mm20.launcher2.freeze.providers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import de.mm20.launcher2.freeze.FreezeBackendType

/**
 * Handles freezing/unfreezing via third-party apps like Ice Box, Hail, or Amarok.
 * These apps typically expose Intent-based APIs.
 */
internal class ThirdPartyProvider(private val context: Context) {

    fun isAvailable(type: FreezeBackendType): Boolean {
        val packageName = when (type) {
            FreezeBackendType.IceBox -> "com.catchingnow.icebox"
            FreezeBackendType.Hail -> "com.aistra.hail"
            FreezeBackendType.Amarok -> "com.vegardit.amarok"
            FreezeBackendType.Island -> "com.oasisfeng.island"
            FreezeBackendType.Shelter -> "net.typeblog.shelter"
            else -> return false
        }
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun setPackagesSuspended(type: FreezeBackendType, packageNames: List<String>, suspended: Boolean): Boolean {
        val action = when (type) {
            FreezeBackendType.IceBox -> if (suspended) "com.catchingnow.icebox.intent.action.FREEZE" else "com.catchingnow.icebox.intent.action.UNFREEZE"
            FreezeBackendType.Hail -> if (suspended) "com.aistra.hail.intent.action.FREEZE" else "com.aistra.hail.intent.action.UNFREEZE"
            FreezeBackendType.Amarok -> if (suspended) "com.vegardit.amarok.intent.action.FREEZE" else "com.vegardit.amarok.intent.action.UNFREEZE"
            // Island/Shelter might need different handling, but some versions support these actions or similar.
            // For now, using these as placeholders if they support the standard Hail/IceBox intents (which some do).
            FreezeBackendType.Island -> if (suspended) "com.oasisfeng.island.intent.action.FREEZE" else "com.oasisfeng.island.intent.action.UNFREEZE"
            FreezeBackendType.Shelter -> if (suspended) "net.typeblog.shelter.intent.action.FREEZE" else "net.typeblog.shelter.intent.action.UNFREEZE"
            else -> return false
        }

        val intent = Intent(action).apply {
            putExtra("com.catchingnow.icebox.intent.extra.PACKAGE_LIST", packageNames.toTypedArray())
            // Also add standard extras for other apps
            putExtra("packages", packageNames.toTypedArray())
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            // Some apps require setting the package to ensure it goes to the right one
            `package` = when (type) {
                FreezeBackendType.IceBox -> "com.catchingnow.icebox"
                FreezeBackendType.Hail -> "com.aistra.hail"
                FreezeBackendType.Amarok -> "com.vegardit.amarok"
                FreezeBackendType.Island -> "com.oasisfeng.island"
                FreezeBackendType.Shelter -> "net.typeblog.shelter"
                else -> null
            }
        }
        context.sendBroadcast(intent)
        return true
    }
}
