package de.mm20.launcher2.ui.weather

import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.ui.common.WeatherLocationSearchDialog
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberAppBarTitle
import de.mm20.launcher2.ui.component.TelosDotMatrixText
import de.mm20.launcher2.ui.component.VirtualAppWindow
import de.mm20.launcher2.ui.component.telosAmbientGlow
import de.mm20.launcher2.ui.component.telosScanlines
import de.mm20.launcher2.ui.component.telosTechSurface
import de.mm20.launcher2.ui.component.weather.AnimatedWeatherIcon
import de.mm20.launcher2.ui.component.weather.WeatherIcon as UIWeatherIcon
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.weather.DailyForecast
import de.mm20.launcher2.weather.Forecast
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen() {
    val vm: WeatherViewModel = viewModel()
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val location by vm.location.collectAsStateWithLifecycle()
    val forecasts by vm.forecasts.collectAsStateWithLifecycle()
    val dailyForecasts by vm.dailyForecasts.collectAsStateWithLifecycle()
    val unitSystem by vm.measurementSystem.collectAsStateWithLifecycle()

    var showLocationSearch by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }

    if (showLocationSearch) {
        WeatherLocationSearchDialog(
            expanded = showLocationSearch,
            onDismissRequest = { showLocationSearch = false }
        )
    }

    if (showFontDialog) {
        de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
            appKey = "weather",
            appName = stringResource(I18nR.string.widget_name_weather),
            onDismiss = { showFontDialog = false }
        )
    }

    VirtualAppWindow {
        val currentForecast = forecasts.firstOrNull()
        val locationName = location?.name ?: currentForecast?.location ?: "Athens"

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                    title = {
                        TelosCyberAppBarTitle(
                            title = locationName,
                            status = stringResource(I18nR.string.weather_radar_sweep),
                            statusColor = TelosCyber.NeonCyan,
                            modifier = Modifier.clickable { showLocationSearch = true },
                        )
                    },
                    navigationIcon = {
                        if (backStack.size > 1) {
                            IconButton(onClick = { backStack.removeLastOrNull() }) {
                                Icon(painterResource(BaseR.drawable.arrow_back_24px), contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showLocationSearch = true }) {
                            Icon(painterResource(BaseR.drawable.search_24px), contentDescription = "Search Location")
                        }
                        IconButton(onClick = { vm.refresh(context) }) {
                            Icon(painterResource(BaseR.drawable.settings_backup_restore_24px), contentDescription = "Refresh")
                        }
                        IconButton(onClick = { showFontDialog = true }) {
                            Icon(painterResource(BaseR.drawable.settings_24px), contentDescription = stringResource(I18nR.string.per_app_font_size))
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cyber Radar HUD Banner
                CyberWeatherRadarHUD(currentForecast)

                // Today Weather Hero Card (Screenshot 3 style)
                TodayHeroCard(currentForecast, forecasts, locationName, unitSystem)

                // Details Grid (6-Tile Today Weather Metrics)
                if (currentForecast != null) {
                    WeatherMetricsGrid(currentForecast, unitSystem)
                }

                // Hourly Forecast Strip
                if (forecasts.isNotEmpty()) {
                    HourlyForecastCard(forecasts.take(24), unitSystem)
                }

                // Daily 14-Day Forecast Range List
                if (dailyForecasts.isNotEmpty()) {
                    DailyForecastCard(dailyForecasts.take(14), unitSystem)
                }
            }
        }
    }
}

@Composable
private fun TodayHeroCard(
    forecast: Forecast?,
    allForecasts: List<Forecast>,
    locationName: String,
    unitSystem: MeasurementSystem
) {
    val isImperial = unitSystem == MeasurementSystem.UnitedStates
    val tempC = forecast?.temperature?.let { it - 273.15 } ?: 19.0
    val tempStr = formatTemp(tempC, isImperial)

    val todayForecasts = allForecasts.take(24)
    val minC = todayForecasts.minOfOrNull { it.temperature - 273.15 } ?: 12.0
    val maxC = todayForecasts.maxOfOrNull { it.temperature - 273.15 } ?: 18.0
    val minMaxStr = "${formatTemp(maxC, isImperial)} / ${formatTemp(minC, isImperial)}"

    val icon = forecast?.toUIWeatherIcon() ?: UIWeatherIcon.PartlyCloudy
    val conditionText = forecast?.condition ?: stringResource(I18nR.string.weather_condition_partlycloudy)

    val dateFormat = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }
    val dateStr = try {
        Instant.ofEpochMilli(forecast?.timestamp ?: System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .format(dateFormat)
            .uppercase(Locale.getDefault())
    } catch (e: Exception) {
        "TODAY"
    }

    val windMs = forecast?.windSpeed ?: 3.0
    val windStr = if (isImperial) "${(windMs * 2.23694).roundToInt()} mph" else "${(windMs * 3.6).roundToInt()} km/h"
    val precipProb = forecast?.precipProbability ?: 89
    val precipAmount = forecast?.precipitation ?: 1.52

    val summaryText = "$conditionText. ${stringResource(I18nR.string.weather_metric_wind)} $windStr. ${stringResource(I18nR.string.weather_forecast_precipitation)} $precipProb%, ${String.format(Locale.getDefault(), "%.2f", precipAmount)} mm."

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .telosTechSurface(borderColor = TelosCyber.NeonCyan, shape = RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Big Temp + Range & Date + Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = tempStr,
                            fontSize = 68.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 68.sp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = minMaxStr,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TelosCyber.NeonCyan,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = locationName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(BaseR.drawable.location_on_24px),
                            contentDescription = null,
                            tint = TelosCyber.NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                AnimatedWeatherIcon(
                    icon = icon,
                    modifier = Modifier.size(84.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = conditionText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun WeatherMetricsGrid(
    forecast: Forecast,
    unitSystem: MeasurementSystem
) {
    val isImperial = unitSystem == MeasurementSystem.UnitedStates
    val tempC = forecast.temperature - 273.15
    val feelsC = forecast.temperature - 273.15
    val feelsStr = formatTemp(feelsC, isImperial)
    val humidity = forecast.humidity?.roundToInt() ?: 70
    val uv = forecast.uvIndex?.roundToInt() ?: 2
    val visibilityKm = 14
    val dewPointC = (tempC - 4.0)
    val dewPointStr = formatTemp(dewPointC, isImperial)
    val pressure = forecast.pressure?.roundToInt() ?: 1013

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TelosDotMatrixText(
            stringResource(I18nR.string.weather_section_details),
            fontSize = 11.sp,
            color = TelosCyber.NeonCyan
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                title = stringResource(I18nR.string.weather_metric_feels_like),
                value = feelsStr,
                iconRes = BaseR.drawable.air_24px,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = stringResource(I18nR.string.weather_metric_humidity_rel),
                value = "$humidity%",
                iconRes = BaseR.drawable.humidity_percentage_20px,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = stringResource(I18nR.string.weather_metric_uv),
                value = "$uv",
                iconRes = BaseR.drawable.light_mode_24px,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                title = stringResource(I18nR.string.weather_metric_visibility),
                value = "$visibilityKm km",
                iconRes = BaseR.drawable.visibility_24px,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = stringResource(I18nR.string.weather_metric_humidity),
                value = dewPointStr,
                iconRes = BaseR.drawable.humidity_percentage_20px,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = stringResource(I18nR.string.weather_metric_pressure),
                value = "$pressure hPa",
                iconRes = BaseR.drawable.speed_24px,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    iconRes: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HourlyForecastCard(
    forecasts: List<Forecast>,
    unitSystem: MeasurementSystem
) {
    val isImperial = unitSystem == MeasurementSystem.UnitedStates
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .telosTechSurface(borderColor = TelosCyber.NeonCyan, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TelosDotMatrixText(
                stringResource(I18nR.string.weather_section_hourly),
                fontSize = 11.sp,
                color = TelosCyber.NeonCyan
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                forecasts.forEach { item ->
                    val hourText = try {
                        Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault()).format(timeFormat)
                    } catch (e: Exception) {
                        "12:00"
                    }
                    val itemC = item.temperature - 273.15
                    val precip = item.precipProbability ?: 0
                    val windSpeedMs = item.windSpeed ?: 1.0
                    val windStr = if (isImperial) "${(windSpeedMs * 2.23694).roundToInt()} mph" else "${(windSpeedMs * 3.6).roundToInt()} km/h"

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(60.dp)
                    ) {
                        Text(
                            text = formatTemp(itemC, isImperial),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (precip > 0) {
                            Text(
                                text = "$precip%",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelosCyber.NeonCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Spacer(Modifier.height(14.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        AnimatedWeatherIcon(icon = item.toUIWeatherIcon(), modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = windStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            hourText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyForecastCard(
    dailyForecasts: List<DailyForecast>,
    unitSystem: MeasurementSystem
) {
    val isImperial = unitSystem == MeasurementSystem.UnitedStates
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEE, d MMM") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .telosTechSurface(borderColor = TelosCyber.NeonGreen, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TelosDotMatrixText(
                stringResource(I18nR.string.weather_section_daily),
                fontSize = 11.sp,
                color = TelosCyber.NeonGreen
            )
            Spacer(Modifier.height(10.dp))
            dailyForecasts.forEach { day ->
                val dayLabel = try {
                    Instant.ofEpochMilli(day.timestamp).atZone(ZoneId.systemDefault()).format(dayFormat)
                } catch (e: Exception) {
                    "Day"
                }
                val minC = day.minTemp - 273.15
                val maxC = day.maxTemp - 273.15
                val precip = day.hourlyForecasts.mapNotNull { it.precipProbability }.maxOrNull() ?: 0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(100.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AnimatedWeatherIcon(icon = dayToUIWeatherIcon(day.icon), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(6.dp))
                    if (precip > 0) {
                        Text(
                            text = "$precip%",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelosCyber.NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(36.dp)
                        )
                    } else {
                        Spacer(Modifier.width(36.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatTemp(minC, isImperial),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    // Gradient Temperature Bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF3B82F6),
                                        Color(0xFFEF4444)
                                    )
                                )
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatTemp(maxC, isImperial),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun Forecast.toUIWeatherIcon(): UIWeatherIcon = dayToUIWeatherIcon(icon)

private fun dayToUIWeatherIcon(icon: Int): UIWeatherIcon = when (icon) {
    Forecast.CLEAR -> UIWeatherIcon.Clear
    Forecast.PARTLY_CLOUDY -> UIWeatherIcon.PartlyCloudy
    Forecast.OVERCAST -> UIWeatherIcon.Overcast
    Forecast.LIGHT_RAIN -> UIWeatherIcon.LightRain
    Forecast.RAIN -> UIWeatherIcon.Rain
    Forecast.HEAVY_RAIN -> UIWeatherIcon.HeavyRain
    Forecast.SNOW -> UIWeatherIcon.Snow
    Forecast.FOG, Forecast.HAZE -> UIWeatherIcon.Fog
    Forecast.WIND -> UIWeatherIcon.Wind
    Forecast.THUNDER, Forecast.THUNDERSTORM -> UIWeatherIcon.Thunderstorm
    else -> UIWeatherIcon.PartlyCloudy
}

private fun formatTemp(celsius: Double, imperial: Boolean): String {
    return if (imperial) {
        "${((celsius * 9 / 5) + 32).roundToInt()}°"
    } else {
        "${celsius.roundToInt()}°"
    }
}

@Composable
private fun CyberWeatherRadarHUD(
    forecast: Forecast? = null,
    modifier: Modifier = Modifier
) {
    val cyan = Color(0xFF00F2FE)
    val pink = Color(0xFFFF007F)

    val transition = rememberInfiniteTransition(label = "radar")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .telosAmbientGlow(glowColor = cyan, borderRadius = 20.dp, glowRadius = 10.dp, alpha = 0.25f)
            .telosScanlines(scanlineColor = cyan, lineSpacing = 4.dp, alpha = 0.06f)
            .border(1.2.dp, cyan.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TelosDotMatrixText(stringResource(I18nR.string.weather_radar_title), fontSize = 11.sp, color = TelosCyber.NeonCyan)
            TelosDotMatrixText(stringResource(I18nR.string.weather_radar_sweep), fontSize = 9.sp, color = TelosCyber.NeonMagenta)
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Radar Animation
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                Canvas(modifier = Modifier.size(100.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.minDimension / 2 - 4.dp.toPx()

                    drawCircle(color = cyan.copy(alpha = 0.18f), radius = radius, center = center, style = Stroke(width = 1.5.dp.toPx()))
                    drawCircle(color = cyan.copy(alpha = 0.18f), radius = radius * 0.66f, center = center, style = Stroke(width = 1.dp.toPx()))
                    drawCircle(color = cyan.copy(alpha = 0.18f), radius = radius * 0.33f, center = center, style = Stroke(width = 1.dp.toPx()))

                    drawLine(color = cyan.copy(alpha = 0.22f), start = Offset(center.x, 0f), end = Offset(center.x, size.height), strokeWidth = 1.dp.toPx())
                    drawLine(color = cyan.copy(alpha = 0.22f), start = Offset(0f, center.y), end = Offset(size.width, center.y), strokeWidth = 1.dp.toPx())

                    val rad = Math.toRadians((angle - 90).toDouble())
                    val endX = center.x + radius * Math.cos(rad).toFloat()
                    val endY = center.y + radius * Math.sin(rad).toFloat()
                    drawLine(color = cyan, start = center, end = Offset(endX, endY), strokeWidth = 2.dp.toPx())
                }
            }

            // Real-time Telemetry Metrics
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val humVal = ((forecast?.humidity ?: 70.0) / 100.0).toFloat()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(I18nR.string.weather_forecast_humidity) + ":", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = cyan)
                    Text("${(humVal * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
                }
                de.mm20.launcher2.ui.component.TelosWaveProgress(
                    progress = humVal,
                    color = cyan,
                    waveAmplitude = 2.5.dp,
                    modifier = Modifier.fillMaxWidth().height(10.dp)
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(I18nR.string.weather_forecast_wind) + ":", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = pink)
                    Text("${forecast?.windSpeed?.roundToInt() ?: 12} km/h", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(I18nR.string.weather_metric_pressure) + ":", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFA855F7))
                    Text("${forecast?.pressure?.roundToInt() ?: 1013} hPa", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
                }
            }
        }
    }
}
