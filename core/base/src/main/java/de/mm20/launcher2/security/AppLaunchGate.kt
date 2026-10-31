package de.mm20.launcher2.security

import android.content.Context

/**
 * Same-process hook so launchers can cloak developer settings before [startActivity]
 * without `:data:applications` depending on `:services:app-management`.
 */
object AppLaunchGate {
    @Volatile
    var preLaunch: ((Context, String) -> Unit)? = null

    @Volatile
    var onHomeResumed: ((Context) -> Unit)? = null

    fun notifyPreLaunch(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        preLaunch?.invoke(context.applicationContext, packageName)
    }

    fun notifyHomeResumed(context: Context) {
        onHomeResumed?.invoke(context.applicationContext)
    }
}
