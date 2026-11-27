package de.mm20.launcher2.oem

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Samsung Modes & Routines Integration Bridge for OneUI.
 * Syncs Telos Context Profiles with Samsung Modes & Routines
 * (e.g. Driving, Work, Sleep).
 */
object SamsungRoutinesBridge {
    private const val TAG = "SamsungRoutinesBridge"
    private const val ROUTINES_PACKAGE = "com.samsung.android.app.routines"

    fun triggerRoutineAction(context: Context, routineName: String): Boolean {
        if (OemIntegrationEngine.detectFlavor() != AndroidFlavor.OneUI) return false
        val intent = Intent("com.samsung.android.app.routines.ACTION_TRIGGER")
            .setPackage(ROUTINES_PACKAGE)
            .putExtra("routine_name", routineName)
        return runCatching {
            context.sendBroadcast(intent)
            Log.i(TAG, "Triggered Samsung Routine: $routineName")
            true
        }.getOrDefault(false)
    }
}
