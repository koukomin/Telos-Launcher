package de.mm20.launcher2.weather

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.preferences.weather.WeatherAlertConfig
import de.mm20.launcher2.preferences.weather.WeatherSettings
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

enum class WeatherAlertType(val key: String) {
    Rain("rain"),
    HeavyRain("heavy_rain"),
    Snow("snow"),
    Thunder("thunder"),
    Heat("heat"),
    Frost("frost"),
    Wind("wind"),
    Uv("uv"),
}

/**
 * One kind of severe weather that the forecast expects.
 * [value] is the strongest value in the checked hours: the chance of rain in percent, the rain in
 * mm per hour, the highest or the lowest temperature in degrees Celsius, the wind in m/s or the UV
 * index. It is null when the forecast only has an icon for it.
 */
data class WeatherAlert(
    val type: WeatherAlertType,
    /** When it is first expected, in millis */
    val time: Long,
    val value: Double?,
    val location: String,
)

/** Decides from the stored forecast what is worth a notification. No Android in here, so that it can be tested. */
object WeatherAlertEvaluator {

    private const val HOUR = 3_600_000L
    private const val KELVIN = 273.15

    /** The rain, in mm per hour, from which rain counts as heavy when the provider has no icon for it */
    private const val HEAVY_RAIN_MM = 4.0

    @Suppress("DEPRECATION")
    private val thunderIcons = setOf(
        Forecast.THUNDER, Forecast.THUNDERSTORM, Forecast.HAIL,
        Forecast.HEAVY_THUNDERSTORM, Forecast.HEAVY_THUNDERSTORM_WITH_RAIN,
    )

    @Suppress("DEPRECATION")
    private val windIcons = setOf(Forecast.WIND, Forecast.STORM)

    fun evaluate(forecasts: List<Forecast>, config: WeatherAlertConfig, now: Long): List<WeatherAlert> {
        if (!config.enabled) return emptyList()
        val window = forecasts
            .filter { it.timestamp in (now - HOUR)..(now + config.hours * HOUR) }
            .sortedBy { it.timestamp }
        if (window.isEmpty()) return emptyList()
        val location = window.first().location
        val alerts = mutableListOf<WeatherAlert>()

        fun add(type: WeatherAlertType, hits: List<Forecast>, value: Double?) {
            hits.firstOrNull()?.let { alerts += WeatherAlert(type, it.timestamp, value, location) }
        }
        fun celsius(f: Forecast) = f.temperature - KELVIN

        if (config.rain) {
            // a provider without a chance of rain only has the icon
            val hits = window.filter { f -> f.precipProbability?.let { it >= config.rainProbability } ?: (f.icon == Forecast.RAIN) }
            add(WeatherAlertType.Rain, hits, hits.mapNotNull { it.precipProbability }.maxOrNull()?.toDouble())
        }
        if (config.heavyRain) {
            val hits = window.filter { it.icon == Forecast.HEAVY_RAIN || (it.precipitation ?: 0.0) >= HEAVY_RAIN_MM }
            add(WeatherAlertType.HeavyRain, hits, hits.mapNotNull { it.precipitation }.maxOrNull())
        }
        if (config.snow) {
            val hits = window.filter { it.icon == Forecast.SNOW || it.icon == Forecast.SLEET }
            add(WeatherAlertType.Snow, hits, null)
        }
        if (config.thunder) {
            val hits = window.filter { it.icon in thunderIcons }
            add(WeatherAlertType.Thunder, hits, null)
        }
        if (config.heat) {
            val hits = window.filter { celsius(it) >= config.heatTemperature || it.icon == Forecast.EXTREME_HEAT }
            add(WeatherAlertType.Heat, hits, hits.maxOfOrNull { celsius(it) })
        }
        if (config.frost) {
            val hits = window.filter { celsius(it) <= config.frostTemperature || it.icon == Forecast.EXTREME_COLD }
            add(WeatherAlertType.Frost, hits, hits.minOfOrNull { celsius(it) })
        }
        if (config.wind) {
            val limit = config.windSpeed / 3.6
            val hits = window.filter { (it.windSpeed ?: 0.0) >= limit || it.icon in windIcons }
            add(WeatherAlertType.Wind, hits, hits.mapNotNull { it.windSpeed }.maxOrNull())
        }
        if (config.uv) {
            val hits = window.filter { (it.uvIndex ?: 0.0) >= config.uvIndex }
            add(WeatherAlertType.Uv, hits, hits.mapNotNull { it.uvIndex }.maxOrNull())
        }
        return alerts
    }
}

/**
 * Shows the notifications for severe weather. It is run after every weather update (and when the
 * forecast has not changed, at the same rate), reads the stored forecast and shows what
 * [WeatherAlertEvaluator] finds. Each kind of alert is shown at most once in 12 hours.
 */
class WeatherAlertManager(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: WeatherSettings,
) {

    suspend fun check(now: Long = System.currentTimeMillis()) {
        val config = settings.alerts.first()
        if (!config.enabled) return
        val forecasts = database.weatherDao().getForecasts().first().map { Forecast(it) }
        val alerts = WeatherAlertEvaluator.evaluate(forecasts, config, now)
        val lastSent = settings.alertLastSent.first()
        for (alert in alerts) {
            val last = lastSent[alert.type.key] ?: 0L
            if (now - last < COOLDOWN) continue
            if (notify(alert, now)) settings.setAlertSent(alert.type.key, now)
        }
    }

    /** A notification as it looks for rain, so that you can see what to expect */
    suspend fun sendTest(): Boolean {
        val now = System.currentTimeMillis()
        val location = database.weatherDao().getForecasts(1).first().firstOrNull()?.location ?: context.getString(R.string.weather_alert_test_location)
        return notify(WeatherAlert(WeatherAlertType.Rain, now + 2 * 3_600_000L, 80.0, location), now, test = true)
    }

    private suspend fun notify(alert: WeatherAlert, now: Long, test: Boolean = false): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.weather_alert_channel), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val system = settings.measurementSystem.first()
        val title = context.getString(titleRes(alert.type))
        val text = buildText(alert, now, system)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        manager.notify(
            NOTIFICATION_BASE + alert.type.ordinal + if (test) 100 else 0,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_weather_alert)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        )
        return true
    }

    private fun titleRes(type: WeatherAlertType) = when (type) {
        WeatherAlertType.Rain -> R.string.weather_alert_rain
        WeatherAlertType.HeavyRain -> R.string.weather_alert_heavy_rain
        WeatherAlertType.Snow -> R.string.weather_alert_snow
        WeatherAlertType.Thunder -> R.string.weather_alert_thunder
        WeatherAlertType.Heat -> R.string.weather_alert_heat
        WeatherAlertType.Frost -> R.string.weather_alert_frost
        WeatherAlertType.Wind -> R.string.weather_alert_wind
        WeatherAlertType.Uv -> R.string.weather_alert_uv
    }

    private fun buildText(alert: WeatherAlert, now: Long, system: MeasurementSystem): String {
        val imperial = system == MeasurementSystem.UnitedStates ||
            (system == MeasurementSystem.System && Locale.getDefault().country in IMPERIAL_COUNTRIES)
        val detail = when (alert.type) {
            WeatherAlertType.Rain -> alert.value?.let { context.getString(R.string.weather_alert_detail_rain, it.roundToInt()) }
            WeatherAlertType.HeavyRain -> alert.value?.let {
                if (imperial) context.getString(R.string.weather_alert_detail_rain_amount, String.format(Locale.getDefault(), "%.2f", it / 25.4), "in")
                else context.getString(R.string.weather_alert_detail_rain_amount, String.format(Locale.getDefault(), "%.1f", it), "mm")
            }
            WeatherAlertType.Heat -> alert.value?.let { context.getString(R.string.weather_alert_detail_heat, temperature(it, imperial)) }
            WeatherAlertType.Frost -> alert.value?.let { context.getString(R.string.weather_alert_detail_frost, temperature(it, imperial)) }
            WeatherAlertType.Wind -> alert.value?.let {
                if (imperial) context.getString(R.string.weather_alert_detail_wind, (it * 2.23694).roundToInt(), "mph")
                else context.getString(R.string.weather_alert_detail_wind, (it * 3.6).roundToInt(), "km/h")
            }
            WeatherAlertType.Uv -> alert.value?.let { context.getString(R.string.weather_alert_detail_uv, it.roundToInt()) }
            WeatherAlertType.Snow, WeatherAlertType.Thunder -> null
        }
        val whenText = whenText(alert.time, now)
        return listOfNotNull(detail, whenText, alert.location.takeIf { it.isNotBlank() }).joinToString(" · ")
    }

    private fun temperature(celsius: Double, imperial: Boolean): String =
        if (imperial) "${(celsius * 9 / 5 + 32).roundToInt()}°F" else "${celsius.roundToInt()}°C"

    private fun whenText(time: Long, now: Long): String {
        if (time <= now + 30 * 60_000L) return context.getString(R.string.weather_alert_now)
        val time24 = DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_TIME)
        val sameDay = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_YEAR) ==
            Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.DAY_OF_YEAR)
        val text = if (sameDay) time24 else DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY)
        return context.getString(R.string.weather_alert_from, text)
    }

    companion object {
        private const val CHANNEL_ID = "weather_alerts"
        private const val NOTIFICATION_BASE = 6100
        private const val COOLDOWN = 12 * 3_600_000L
        private val IMPERIAL_COUNTRIES = setOf("US", "LR", "MM")
    }
}
