package de.mm20.launcher2.ui.fit

import android.content.Context
import android.content.Intent

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class FitHealthSourceId { HealthConnect, GoogleFit, Garmin, Samsung, Huawei }

data class FitVendorApp(
    val id: FitHealthSourceId,
    val packageName: String,
)

data class FitDaySnapshot(
    val steps: Long? = null,
    val caloriesKcal: Long? = null,
    val distanceMeters: Double? = null,
    val heartRateBpm: Long? = null,
    val sleepHours: Float? = null,
    val weightKg: Double? = null,
)

object FitHealthHub {
    val vendors = listOf(
        FitVendorApp(FitHealthSourceId.HealthConnect, "com.google.android.apps.healthdata"),
        FitVendorApp(FitHealthSourceId.GoogleFit, "com.google.android.apps.fitness"),
        FitVendorApp(FitHealthSourceId.Garmin, "com.garmin.android.apps.connectmobile"),
        FitVendorApp(FitHealthSourceId.Samsung, "com.sec.android.app.shealth"),
        FitVendorApp(FitHealthSourceId.Huawei, "com.huawei.health"),
    )

    val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

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

    fun sdkAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun grantedPermissions(context: Context): Set<String> {
        if (!sdkAvailable(context)) return emptySet()
        return HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
    }

    suspend fun readToday(context: Context): FitDaySnapshot {
        val client = HealthConnectClient.getOrCreate(context)
        val start = Instant.now().truncatedTo(ChronoUnit.DAYS)
        val end = Instant.now()
        val range = TimeRangeFilter.between(start, end)
        val agg = runCatching {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        HeartRateRecord.BPM_AVG,
                    ),
                    timeRangeFilter = range,
                )
            )
        }.getOrNull()
        val sleep = runCatching {
            client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = range)
            ).records.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
        }.getOrNull()
        val weight = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start.minus(30, ChronoUnit.DAYS), end),
                )
            ).records.maxByOrNull { it.time }?.weight?.inKilograms
        }.getOrNull()
        return FitDaySnapshot(
            steps = agg?.get(StepsRecord.COUNT_TOTAL),
            caloriesKcal = agg?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories?.toLong(),
            distanceMeters = agg?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters,
            heartRateBpm = agg?.get(HeartRateRecord.BPM_AVG),
            sleepHours = sleep?.let { it / 60f },
            weightKg = weight,
        )
    }
}
