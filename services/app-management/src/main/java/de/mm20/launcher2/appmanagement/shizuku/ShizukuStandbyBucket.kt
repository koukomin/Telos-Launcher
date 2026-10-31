package de.mm20.launcher2.appmanagement.shizuku

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShizukuStandbyBucket {
    private const val TAG = "ShizukuStandbyBucket"

    enum class Bucket(val value: String) {
        Active("active"),
        WorkingSet("working_set"),
        Frequent("frequent"),
        Rare("rare"),
        Restricted("restricted")
    }

    suspend fun setStandbyBucket(packageName: String, bucket: Bucket): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@withContext false
        try {
            val command = "am set-standby-bucket $packageName ${bucket.value}"
            val result = de.mm20.launcher2.ktx.PrivilegedShell.run(command)
            result?.exitCode == 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set Standby Bucket for $packageName", e)
            false
        }
    }
}
