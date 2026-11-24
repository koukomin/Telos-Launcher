package de.mm20.launcher2.permissions

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import kotlin.reflect.KClass

/**
 * Shared Health Connect SDK/package helpers for Telos Fit.
 * Aether keeps its workout Health Connect manager; Fit does not depend on Amethyst.
 */
object HealthConnectAccess {
    const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
    const val GOOGLE_FIT_PACKAGE = "com.google.android.apps.fitness"
    const val GARMIN_PACKAGE = "com.garmin.android.apps.connectmobile"
    const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"
    const val HUAWEI_HEALTH_PACKAGE = "com.huawei.health"

    fun sdkAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun needsProviderUpdate(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) ==
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    fun clientOrNull(context: Context): HealthConnectClient? {
        if (!sdkAvailable(context)) return null
        return runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    fun readPermissions(vararg records: KClass<out Record>): Set<String> =
        records.map { HealthPermission.getReadPermission(it) }.toSet()

    suspend fun grantedPermissions(context: Context): Set<String> {
        val client = clientOrNull(context) ?: return emptySet()
        return runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
    }

    fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)

    fun openApp(context: Context, packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return runCatching {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    fun openHealthConnectSettings(context: Context): Boolean {
        val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
