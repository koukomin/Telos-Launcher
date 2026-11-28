package de.mm20.launcher2.weather

import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.preferences.weather.WeatherSettings
import de.mm20.launcher2.search.VirtualAppAppearance
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class WeatherOngoingNotifier(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: WeatherSettings,
) {

    suspend fun update() {
        val enabled = settings.ongoingNotificationEnabled.first()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        if (!enabled) {
            manager.cancel(NOTIFICATION_ID)
            return
        }

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val forecasts = database.weatherDao().getForecasts().first().map { Forecast(it) }
        if (forecasts.isEmpty()) return

        val current = forecasts.first()
        val system = settings.measurementSystem.first()
        val isImperial = system == MeasurementSystem.UnitedStates

        val locationName = current.location.ifBlank { context.getString(I18nR.string.widget_name_weather) }
        val tempC = current.temperature - KELVIN
        val feelsC = current.temperature - KELVIN

        val tempStr = formatTemp(tempC, isImperial)
        val feelsStr = formatTemp(feelsC, isImperial)

        val todayMin = forecasts.take(24).minOfOrNull { it.temperature - KELVIN } ?: tempC
        val todayMax = forecasts.take(24).maxOfOrNull { it.temperature - KELVIN } ?: tempC
        val minMaxStr = "${formatTemp(todayMax, isImperial)}/${formatTemp(todayMin, isImperial)}"

        val humidity = current.humidity?.roundToInt() ?: 70
        val windSpeedMs = current.windSpeed ?: 1.0
        val windSpeedStr = if (isImperial) "${(windSpeedMs * 2.23694).roundToInt()} mph" else "${(windSpeedMs * 3.6).roundToInt()} km/h"
        val uv = current.uvIndex?.roundToInt() ?: 2
        val pressure = current.pressure?.roundToInt() ?: 1013

        // Build channel
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(I18nR.string.preference_weather_ongoing_notification),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        // Summary Text
        val title = "$tempStr - $locationName"
        val summaryLine = "${context.getString(I18nR.string.weather_metric_feels_like)}: $feelsStr  $minMaxStr | $humidity%"

        // Build Hourly Strip for BigText
        val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
        val hourlyItems = forecasts.take(8).joinToString("  ") { fc ->
            val h = try {
                Instant.ofEpochMilli(fc.timestamp).atZone(ZoneId.systemDefault()).format(timeFormat)
            } catch (e: Exception) { "12:00" }
            val precip = fc.precipProbability ?: 0
            val t = formatTemp(fc.temperature - KELVIN, isImperial)
            val iconEmoji = when (fc.icon) {
                Forecast.RAIN, Forecast.HEAVY_RAIN, Forecast.LIGHT_RAIN -> "🌧"
                Forecast.CLEAR -> "☀️"
                Forecast.SNOW -> "❄️"
                Forecast.THUNDER, Forecast.THUNDERSTORM -> "🌩"
                else -> "☁️"
            }
            "$h: $t ($precip% $iconEmoji)"
        }

        val bigText = "$title\n$summaryLine\n${context.getString(I18nR.string.weather_metric_wind)}: $windSpeedStr  ${context.getString(I18nR.string.weather_metric_uv)}: $uv  ${context.getString(I18nR.string.weather_metric_pressure)}: $pressure hPa\n\n$hourlyItems"

        val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("telos_weather_app://weather")).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val smallIconRes = VirtualAppAppearance.glyph("telos_weather_app") ?: BaseR.drawable.ic_app_weather_fg

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setContentTitle(title)
            .setContentText(summaryLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatTemp(celsius: Double, imperial: Boolean): String {
        val valDeg = if (imperial) (celsius * 9 / 5 + 32).roundToInt() else celsius.roundToInt()
        return "$valDeg°"
    }

    companion object {
        const val CHANNEL_ID = "weather_ongoing_channel"
        const val NOTIFICATION_ID = 6150
        private const val KELVIN = 273.15
    }
}
