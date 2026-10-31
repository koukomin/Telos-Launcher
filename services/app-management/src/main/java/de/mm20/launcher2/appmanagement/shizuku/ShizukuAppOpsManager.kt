package de.mm20.launcher2.appmanagement.shizuku

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShizukuAppOpsManager {
    private const val TAG = "ShizukuAppOpsManager"

    enum class OpMode(val code: Int) {
        Allow(0),
        Ignore(1),
        Deny(2),
        Default(3)
    }

    suspend fun setAppOpMode(packageName: String, opName: String, mode: OpMode): Boolean = withContext(Dispatchers.IO) {
        try {
            val command = "appops set $packageName $opName ${mode.code}"
            val result = de.mm20.launcher2.ktx.PrivilegedShell.run(command)
            result?.exitCode == 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set AppOp $opName for $packageName", e)
            false
        }
    }

    suspend fun restrictBackgroundExecution(packageName: String, restrict: Boolean): Boolean {
        val mode = if (restrict) OpMode.Ignore else OpMode.Allow
        return setAppOpMode(packageName, "RUN_IN_BACKGROUND", mode) &&
               setAppOpMode(packageName, "RUN_ANY_IN_BACKGROUND", mode)
    }
}
