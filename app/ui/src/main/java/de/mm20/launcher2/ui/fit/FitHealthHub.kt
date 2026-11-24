package de.mm20.launcher2.ui.fit

import android.content.Context
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
import de.mm20.launcher2.permissions.HealthConnectAccess
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
        FitVendorApp(FitHealthSourceId.HealthConnect, HealthConnectAccess.HEALTH_CONNECT_PACKAGE),
        FitVendorApp(FitHealthSourceId.GoogleFit, HealthConnectAccess.GOOGLE_FIT_PACKAGE),
        FitVendorApp(FitHealthSourceId.Garmin, HealthConnectAccess.GARMIN_PACKAGE),
        FitVendorApp(FitHealthSourceId.Samsung, HealthConnectAccess.SAMSUNG_HEALTH_PACKAGE),
        FitVendorApp(FitHealthSourceId.Huawei, HealthConnectAccess.HUAWEI_HEALTH_PACKAGE),
    )

    val readPermissions: Set<String> = HealthConnectAccess.readPermissions(
        StepsRecord::class,
        HeartRateRecord::class,
        SleepSessionRecord::class,
        WeightRecord::class,
        TotalCaloriesBurnedRecord::class,
        DistanceRecord::class,
        ExerciseSessionRecord::class,
    )

    fun isInstalled(context: Context, packageName: String): Boolean =
        HealthConnectAccess.isInstalled(context, packageName)

    fun openApp(context: Context, packageName: String): Boolean =
        HealthConnectAccess.openApp(context, packageName)

    fun openHealthConnectSettings(context: Context): Boolean =
        HealthConnectAccess.openHealthConnectSettings(context)

    fun sdkAvailable(context: Context): Boolean = HealthConnectAccess.sdkAvailable(context)

    suspend fun grantedPermissions(context: Context): Set<String> =
        HealthConnectAccess.grantedPermissions(context)

    suspend fun readToday(context: Context): FitDaySnapshot {
        val client = HealthConnectAccess.clientOrNull(context) ?: return FitDaySnapshot()
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
        val energy = agg?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        val distance = agg?.get(DistanceRecord.DISTANCE_TOTAL)
        val bpm = agg?.get(HeartRateRecord.BPM_AVG)
        return FitDaySnapshot(
            steps = agg?.get(StepsRecord.COUNT_TOTAL),
            caloriesKcal = energy?.inKilocalories?.toLong(),
            distanceMeters = distance?.inMeters,
            heartRateBpm = bpm,
            sleepHours = sleep?.div(60.0)?.toFloat(),
            weightKg = weight,
        )
    }
}
