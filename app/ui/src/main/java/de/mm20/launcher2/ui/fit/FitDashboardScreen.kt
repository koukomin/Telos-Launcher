package de.mm20.launcher2.ui.fit

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.ui.component.TelosDotMatrixText
import de.mm20.launcher2.ui.component.TelosRadialGauge
import de.mm20.launcher2.ui.component.telosTechCard
import de.mm20.launcher2.ui.component.VirtualAppWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit

@Serializable
data object FitDashboardRoute : NavKey

class FitViewModel : ViewModel() {
    private val _steps = MutableStateFlow(8430)
    val steps: StateFlow<Int> = _steps.asStateFlow()

    private val _heartRate = MutableStateFlow(74)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    private val _sleepHours = MutableStateFlow(7.75f)
    val sleepHours: StateFlow<Float> = _sleepHours.asStateFlow()

    private val _waterIntake = MutableStateFlow(2250) // ml
    val waterIntake: StateFlow<Int> = _waterIntake.asStateFlow()

    private val _weight = MutableStateFlow(72.4f)
    val weight: StateFlow<Float> = _weight.asStateFlow()

    private val _isHealthConnectAvailable = MutableStateFlow(false)
    val isHealthConnectAvailable: StateFlow<Boolean> = _isHealthConnectAvailable.asStateFlow()

    fun addWater() {
        _waterIntake.value += 250
    }

    fun loadHealthConnectData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sdkStatus = HealthConnectClient.getSdkStatus(context)
                if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
                    _isHealthConnectAvailable.value = true
                    val client = HealthConnectClient.getOrCreate(context)

                    // Read today's steps
                    val startTime = Instant.now().truncatedTo(ChronoUnit.DAYS)
                    val endTime = Instant.now()

                    val stepsResponse = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                        )
                    )
                    val totalSteps = stepsResponse[StepsRecord.COUNT_TOTAL] ?: _steps.value

                    withContext(Dispatchers.Main) {
                        _steps.value = totalSteps.toInt()
                    }
                }
            } catch (_: Exception) {
                // Fallback to mock/cached state if Health Connect unavailable
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitDashboardScreen() {
    val context = LocalContext.current
    val viewModel: FitViewModel = viewModel()

    LaunchedEffect(Unit) {
        viewModel.loadHealthConnectData(context)
    }

    val steps by viewModel.steps.collectAsStateWithLifecycle()
    val heartRate by viewModel.heartRate.collectAsStateWithLifecycle()
    val sleepHours by viewModel.sleepHours.collectAsStateWithLifecycle()
    val waterIntake by viewModel.waterIntake.collectAsStateWithLifecycle()
    val weight by viewModel.weight.collectAsStateWithLifecycle()
    val isHealthConnectAvailable by viewModel.isHealthConnectAvailable.collectAsStateWithLifecycle()

    val neonGreen = Color(0xFF00E676)
    val neonCyan = Color(0xFF00F0FF)
    val neonOrange = Color(0xFFFF9100)
    val neonMagenta = Color(0xFFFF007F)

    VirtualAppWindow {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Telos Fit", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            TelosDotMatrixText(
                                if (isHealthConnectAvailable) "HEALTH CONNECT // SYNCED" else "LOCAL HUD",
                                fontSize = 9.sp,
                                color = if (isHealthConnectAvailable) neonCyan else neonGreen
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.loadHealthConnectData(context) }) {
                            Icon(painterResource(BaseR.drawable.schedule_24px), contentDescription = "Sync Health Connect")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Steps Ring Card
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .telosTechCard(borderColor = neonGreen),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Daily Steps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Goal: 10,000", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                TelosRadialGauge(
                                    progress = steps / 10000f,
                                    valueText = "$steps",
                                    labelText = "STEPS",
                                    accentColor = neonGreen,
                                    size = 110.dp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MetricRow(icon = "🔥", label = "Calories", value = "482 kcal")
                                    MetricRow(icon = "📍", label = "Distance", value = "5.8 km")
                                    MetricRow(icon = "⏱️", label = "Active Time", value = "54 min")
                                }
                            }
                        }
                    }
                }

                // Heart Rate & Sleep Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .telosTechCard(borderColor = neonMagenta),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Heart Rate", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("$heartRate", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = neonMagenta)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("bpm", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Normal • Just now", fontSize = 10.sp, color = neonGreen)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .telosTechCard(borderColor = neonCyan),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Sleep Score", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("${sleepHours}h", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = neonCyan)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("45m", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Quality: 88% (Good)", fontSize = 10.sp, color = neonCyan)
                            }
                        }
                    }
                }

                // Water Intake & Weight Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .telosTechCard(borderColor = Color(0xFF00B0FF))
                                .clickable { viewModel.addWater() },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Water Intake", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("${waterIntake} ml", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B0FF))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Tap to add +250ml", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .telosTechCard(borderColor = neonOrange),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Body Weight", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("$weight kg", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = neonOrange)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Target: 70.0 kg", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(icon: String, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(icon, fontSize = 16.sp)
        Column {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
