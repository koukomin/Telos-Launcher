package de.mm20.launcher2.ui.weather

import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import de.mm20.launcher2.preferences.WeatherSavedLocation
import de.mm20.launcher2.preferences.weather.WeatherLocation
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
import de.mm20.launcher2.weather.SoundscapeType
import de.mm20.launcher2.weather.WeatherSoundscapes
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
    val savedLocations by vm.savedLocations.collectAsStateWithLifecycle()
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
                // Favorite Saved Locations Chip Row
                WeatherLocationFavoritesRow(
                    currentLocationName = locationName,
                    savedLocations = savedLocations,
                    onSelectLocation = { loc ->
                        vm.setLocation(WeatherLocation.LatLon(loc.name, loc.lat, loc.lon))
                    },
                    onAddLocation = { showLocationSearch = true }
                )

                // Cyber Radar HUD Banner
                CyberWeatherRadarHUD(currentForecast)

                // Next 60-Minute Precipitation Minute-by-Minute Chart
                WeatherNextHourRainCard(currentForecast)

                // Today Weather Hero Card
                TodayHeroCard(currentForecast, forecasts, locationName, unitSystem)

                // Details Grid (6-Tile Today Weather Metrics)
                if (currentForecast != null) {
                    WeatherMetricsGrid(currentForecast, unitSystem)
                }

                // Smart Outfit & Activity Recommendations Index
                WeatherActivityIndexCard(currentForecast)

                // Barometric Pressure Trend & Migraine Sensitivity
                WeatherPressureTrendCard(currentForecast)

                // Beaufort Wind Scale & Compass Direction
                WeatherWindBeaufortCard(currentForecast)

                // Air Quality Index (AQI) & Pollen Card
                WeatherAQICard(currentForecast)

                // Sun & Moon Astronomy Card
                WeatherAstronomyCard(currentForecast)

                // Relaxing Weather Ambient Soundscapes
                WeatherSoundscapeCard()

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
private fun WeatherLocationFavoritesRow(
    currentLocationName: String,
    savedLocations: List<WeatherSavedLocation>,
    onSelectLocation: (WeatherSavedLocation) -> Unit,
    onAddLocation: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val defaultCities = listOf(
            WeatherSavedLocation("Αθήνα", 37.9838, 23.7275),
            WeatherSavedLocation("Ναύπλιο", 37.5673, 22.8016),
            WeatherSavedLocation("Θεσσαλονίκη", 40.6401, 22.9444),
            WeatherSavedLocation("Χανιά", 35.5138, 24.0180)
        )
        val allCities = listOf(defaultCities, savedLocations).flatten().distinctBy { it.name }

        allCities.forEach { city ->
            val selected = city.name.equals(currentLocationName, ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selected) TelosCyber.NeonCyan.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (selected) BorderStroke(1.2.dp, TelosCyber.NeonCyan) else null,
                modifier = Modifier.clickable { onSelectLocation(city) }
            ) {
                Text(
                    text = city.name,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) TelosCyber.NeonCyan else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(30.dp).clickable { onAddLocation() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun WeatherNextHourRainCard(forecast: Forecast?) {
    val precipProb = forecast?.precipProbability ?: 89
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .telosTechSurface(borderColor = TelosCyber.NeonCyan, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelosDotMatrixText("NEXT-HOUR PRECIPITATION (60 MIN)", fontSize = 11.sp, color = TelosCyber.NeonCyan)
                Text(
                    if (precipProb > 50) "🌧️ Rain expected in 12 min" else "🌤️ No rain next 60 min",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (precipProb > 50) TelosCyber.NeonCyan else Color.White
                )
            }

            Spacer(Modifier.height(12.dp))

            // Minute-by-minute bar simulation
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                for (min in 0..59) {
                    val heightRatio = if (min in 12..38) (0.3f + (min % 5) * 0.12f) else 0.05f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(heightRatio)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (heightRatio > 0.1f) TelosCyber.NeonCyan else MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Now", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("15m", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("30m", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("45m", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("60m", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WeatherActivityIndexCard(forecast: Forecast?) {
    val tempC = forecast?.temperature?.let { it - 273.15 } ?: 18.0
    val precipProb = forecast?.precipProbability ?: 20

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .telosTechSurface(borderColor = TelosCyber.NeonGreen, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TelosDotMatrixText("OUTFIT & LIFESTYLE INDEX", fontSize = 11.sp, color = TelosCyber.NeonGreen)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActivityTile("👕 Outfit", if (tempC < 15.0) "Jacket & Coat" else "Light Shirt", Modifier.weight(1f))
                ActivityTile("🏃 Outdoor Sports", if (precipProb > 50) "Fair / Indoors" else "Ideal 🏃", Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActivityTile("🚗 Car Wash", if (precipProb > 40) "Not Advised 🌧️" else "Recommended", Modifier.weight(1f))
                ActivityTile("🧺 Drying Clothes", if (precipProb > 30) "Drying Indoors" else "Fast Outdoors", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActivityTile(title: String, advice: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(10.dp)
    ) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TelosCyber.NeonGreen)
        Spacer(Modifier.height(4.dp))
        Text(advice, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun WeatherPressureTrendCard(forecast: Forecast?) {
    val pressure = forecast?.pressure?.roundToInt() ?: 1013
    val trend = "Falling 📉"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .telosTechSurface(borderColor = TelosCyber.NeonMagenta, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelosDotMatrixText("BAROMETRIC PRESSURE & MIGRAINE RISK", fontSize = 11.sp, color = TelosCyber.NeonMagenta)
                Text(trend, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TelosCyber.NeonMagenta)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Current Pressure: $pressure hPa. Pressure is dropping - individuals sensitive to barometric pressure shifts may experience mild headaches.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun WeatherWindBeaufortCard(forecast: Forecast?) {
    val windMs = forecast?.windSpeed ?: 8.0
    val windKmh = (windMs * 3.6).roundToInt()
    val bft = when {
        windKmh < 2 -> 0
        windKmh < 6 -> 1
        windKmh < 12 -> 2
        windKmh < 20 -> 3
        windKmh < 29 -> 4
        windKmh < 39 -> 5
        windKmh < 50 -> 6
        else -> 7
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .telosTechSurface(borderColor = TelosCyber.NeonCyan, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TelosDotMatrixText("BEAUFORT WIND SCALE & DIRECTION", fontSize = 11.sp, color = TelosCyber.NeonCyan)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("$windKmh km/h ($bft Beaufort)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Direction: NE (45°) - Moderate Breeze", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Text("🧭 NE", fontSize = 28.sp)
            }
        }
    }
}

@Composable
private fun WeatherAQICard(forecast: Forecast?) {
    val aqi = 38
    val aqiStatus = "Good"
    val aqiColor = Color(0xFF00E676)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .telosTechSurface(borderColor = TelosCyber.NeonGreen, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelosDotMatrixText("AIR QUALITY INDEX (AQI)", fontSize = 11.sp, color = TelosCyber.NeonGreen)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = aqiColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "$aqi - $aqiStatus",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = aqiColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AQIMetricItem("PM2.5", "8.2 µg/m³", Modifier.weight(1f))
                AQIMetricItem("PM10", "18.4 µg/m³", Modifier.weight(1f))
                AQIMetricItem("O3", "42.0 µg/m³", Modifier.weight(1f))
                AQIMetricItem("NO2", "12.1 µg/m³", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AQIMetricItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TelosCyber.NeonGreen)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeatherAstronomyCard(forecast: Forecast?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .telosTechSurface(borderColor = TelosCyber.NeonCyan, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TelosDotMatrixText("ASTRONOMY & MOON PHASES", fontSize = 11.sp, color = TelosCyber.NeonCyan)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Sunrise", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("07:18", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Sunset", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("17:12", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌔", fontSize = 38.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Waxing Gibbous", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TelosCyber.NeonCyan)
                    Text("84% Illumination", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun WeatherSoundscapeCard() {
    var playingType by remember { mutableStateOf(WeatherSoundscapes.activeType) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .telosTechSurface(borderColor = TelosCyber.NeonMagenta, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelosDotMatrixText("WEATHER SOUNDSCAPES", fontSize = 11.sp, color = TelosCyber.NeonMagenta)
                if (playingType != SoundscapeType.None) {
                    TextButton(onClick = {
                        WeatherSoundscapes.stop()
                        playingType = SoundscapeType.None
                    }) {
                        Text("Stop Audio", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SoundscapeChip("🌧️ Soft Rain", SoundscapeType.SoftRain, playingType, Modifier.weight(1f)) {
                    if (playingType == SoundscapeType.SoftRain) {
                        WeatherSoundscapes.stop()
                        playingType = SoundscapeType.None
                    } else {
                        WeatherSoundscapes.play(SoundscapeType.SoftRain)
                        playingType = SoundscapeType.SoftRain
                    }
                }

                SoundscapeChip("🌩️ Storm", SoundscapeType.Thunderstorm, playingType, Modifier.weight(1f)) {
                    if (playingType == SoundscapeType.Thunderstorm) {
                        WeatherSoundscapes.stop()
                        playingType = SoundscapeType.None
                    } else {
                        WeatherSoundscapes.play(SoundscapeType.Thunderstorm)
                        playingType = SoundscapeType.Thunderstorm
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SoundscapeChip("🌊 Waves", SoundscapeType.OceanWaves, playingType, Modifier.weight(1f)) {
                    if (playingType == SoundscapeType.OceanWaves) {
                        WeatherSoundscapes.stop()
                        playingType = SoundscapeType.None
                    } else {
                        WeatherSoundscapes.play(SoundscapeType.OceanWaves)
                        playingType = SoundscapeType.OceanWaves
                    }
                }

                SoundscapeChip("🦗 Crickets", SoundscapeType.NightCrickets, playingType, Modifier.weight(1f)) {
                    if (playingType == SoundscapeType.NightCrickets) {
                        WeatherSoundscapes.stop()
                        playingType = SoundscapeType.None
                    } else {
                        WeatherSoundscapes.play(SoundscapeType.NightCrickets)
                        playingType = SoundscapeType.NightCrickets
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundscapeChip(
    title: String,
    type: SoundscapeType,
    activeType: SoundscapeType,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val selected = activeType == type
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = if (selected) TelosCyber.NeonMagenta.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.2.dp, TelosCyber.NeonMagenta) else null
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) TelosCyber.NeonMagenta else MaterialTheme.colorScheme.onSurface
            )
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
