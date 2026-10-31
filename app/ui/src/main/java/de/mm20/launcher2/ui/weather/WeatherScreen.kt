package de.mm20.launcher2.ui.weather
import de.mm20.launcher2.base.R as BaseR

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.preferences.weather.WeatherLocation
import de.mm20.launcher2.ui.common.WeatherLocationSearchDialog
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.component.VirtualAppWindow
import de.mm20.launcher2.ui.component.weather.AnimatedWeatherIcon
import de.mm20.launcher2.ui.component.weather.WeatherIcon as UIWeatherIcon
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
            appName = "Telos Weather",
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
                        Text(
                            text = locationName,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { showLocationSearch = true }
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
                            Icon(painterResource(BaseR.drawable.settings_24px), contentDescription = "Font Settings")
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
                // Today Weather Hero Card
                TodayHeroCard(currentForecast, unitSystem)

                // Hourly Forecast Scrollable Row
                if (forecasts.isNotEmpty()) {
                    HourlyForecastCard(forecasts.take(24), unitSystem)
                }

                // 14-Day Daily Forecast List
                if (dailyForecasts.isNotEmpty()) {
                    DailyForecastCard(dailyForecasts.take(14), unitSystem)
                }

                // Weather Details Grid (AQI, UV, Wind, Humidity, Pressure, Visibility)
                if (currentForecast != null) {
                    WeatherMetricsGrid(currentForecast, unitSystem)
                }
            }
        }
    }
}

@Composable
private fun TodayHeroCard(
    forecast: Forecast?,
    unitSystem: MeasurementSystem
) {
    val tempC = forecast?.temperature?.let { it - 273.15 } ?: 22.0
    val feelsC = forecast?.temperature?.let { it - 273.15 } ?: 22.0
    val temp = formatTemp(tempC, unitSystem)
    val feelsLike = formatTemp(feelsC, unitSystem)
    val icon = forecast?.toUIWeatherIcon() ?: UIWeatherIcon.PartlyCloudy
    val condition = forecast?.condition ?: "Partly Cloudy"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedWeatherIcon(
                icon = icon,
                modifier = Modifier.size(96.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = temp,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = condition,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Feels like $feelsLike",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun HourlyForecastCard(
    forecasts: List<Forecast>,
    unitSystem: MeasurementSystem
) {
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Hourly Forecast",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(56.dp)
                    ) {
                        Text(hourText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        AnimatedWeatherIcon(icon = item.toUIWeatherIcon(), modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = formatTemp(itemC, unitSystem),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val precip = item.precipProbability ?: 0
                        if (precip > 0) {
                            Text(
                                text = "$precip%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp
                            )
                        }
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
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "14-Day Forecast",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            dailyForecasts.forEach { day ->
                val dayLabel = try {
                    Instant.ofEpochMilli(day.timestamp).atZone(ZoneId.systemDefault()).format(dayFormat)
                } catch (e: Exception) {
                    "Day"
                }
                val minC = day.minTemp - 273.15
                val maxC = day.maxTemp - 273.15

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
                        modifier = Modifier.width(110.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AnimatedWeatherIcon(icon = dayToUIWeatherIcon(day.icon), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = formatTemp(minC, unitSystem),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    // Temp Bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.colorScheme.primary
                                    )
                                )
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatTemp(maxC, unitSystem),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherMetricsGrid(
    forecast: Forecast,
    unitSystem: MeasurementSystem
) {
    val windMs = forecast.windSpeed ?: 5.0
    val windKmh = (windMs * 3.6).roundToInt()
    val humidity = forecast.humidity?.roundToInt() ?: 60
    val pressure = forecast.pressure?.roundToInt() ?: 1013
    val uv = forecast.uvIndex?.roundToInt() ?: 4

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                title = "Wind",
                value = "$windKmh km/h",
                subtitle = "Gusts up to ${(windKmh * 1.25).roundToInt()} km/h",
                iconRes = BaseR.drawable.air_24px,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "Humidity",
                value = "$humidity%",
                subtitle = "Dew point: ${formatTemp((forecast.temperature - 273.15) - 4, unitSystem)}",
                iconRes = BaseR.drawable.humidity_percentage_20px,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                title = "UV Index",
                value = "$uv",
                subtitle = if (uv > 6) "High - Wear Sunscreen" else "Moderate",
                iconRes = BaseR.drawable.light_mode_24px,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "Pressure",
                value = "$pressure hPa",
                subtitle = "Barometer",
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
    subtitle: String,
    iconRes: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

private fun formatTemp(celsius: Double, system: MeasurementSystem): String {
    return if (system == MeasurementSystem.UnitedStates) {
        "${((celsius * 9 / 5) + 32).roundToInt()}°F"
    } else {
        "${celsius.roundToInt()}°C"
    }
}
