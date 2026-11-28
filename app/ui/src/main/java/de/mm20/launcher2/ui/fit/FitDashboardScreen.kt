package de.mm20.launcher2.ui.fit

import de.mm20.launcher2.ui.component.TelosCyberHeading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.component.TelosDotMatrixText
import de.mm20.launcher2.ui.component.TelosRadialGauge
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.component.telosTechCard
import de.mm20.launcher2.ui.component.VirtualAppWindow
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
data object FitDashboardRoute : NavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitDashboardScreen() {
    val context = LocalContext.current
    val viewModel: FitViewModel = viewModel()

    LaunchedEffect(Unit) {
        viewModel.loadHealthConnectData(context)
    }

    val selectedTab by viewModel.selectedTab
    val steps by viewModel.steps.collectAsStateWithLifecycle()
    val heartRate by viewModel.heartRate.collectAsStateWithLifecycle()
    val sleepHours by viewModel.sleepHours.collectAsStateWithLifecycle()
    val waterIntake by viewModel.waterIntake.collectAsStateWithLifecycle()
    val weight by viewModel.weight.collectAsStateWithLifecycle()
    val heightCm by viewModel.heightCm.collectAsStateWithLifecycle()
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val meals by viewModel.loggedMeals.collectAsStateWithLifecycle()
    val isHealthConnectAvailable by viewModel.isHealthConnectAvailable.collectAsStateWithLifecycle()
    val healthConnectSynced by viewModel.healthConnectSynced.collectAsStateWithLifecycle()
    val calories by viewModel.calories.collectAsStateWithLifecycle()
    val distanceKm by viewModel.distanceKm.collectAsStateWithLifecycle()
    val needsHealthPermission by viewModel.needsHealthPermission.collectAsStateWithLifecycle()
    val cycleTrackerEnabled by viewModel.cycleTrackerEnabled.collectAsStateWithLifecycle()
    val lastPeriodDateMillis by viewModel.lastPeriodDateMillis.collectAsStateWithLifecycle()
    val cycleLengthDays by viewModel.cycleLengthDays.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { viewModel.loadHealthConnectData(context) }

    var showFoodSearch by remember { mutableStateOf(false) }

    val neonGreen = Color(0xFF00E676)
    val neonCyan = Color(0xFF00F0FF)
    val neonOrange = Color(0xFFFF9100)
    val neonMagenta = Color(0xFFFF007F)

    VirtualAppWindow {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        TelosCyberHeading {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(I18nR.string.fit_title), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                TelosDotMatrixText(
                                    when {
                                        healthConnectSynced -> stringResource(I18nR.string.fit_status_synced)
                                        isHealthConnectAvailable -> stringResource(I18nR.string.fit_status_partial)
                                        else -> "SENSORS ACTIVE 👟"
                                    },
                                    fontSize = 9.sp,
                                    color = neonGreen
                                )
                            }
                        }
                    },
                    actions = {
                        if (selectedTab == FitTab.Nutrition) {
                            IconButton(onClick = { showFoodSearch = true }) {
                                Icon(painterResource(BaseR.drawable.search_24px), contentDescription = "Add Food")
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelosTechPill(
                        text = "Dashboard",
                        isSelected = selectedTab == FitTab.Dashboard,
                        onClick = { viewModel.selectedTab.value = FitTab.Dashboard },
                        accentColor = neonGreen
                    )
                    TelosTechPill(
                        text = "Nutrition & Macros",
                        isSelected = selectedTab == FitTab.Nutrition,
                        onClick = { viewModel.selectedTab.value = FitTab.Nutrition },
                        accentColor = neonCyan
                    )
                    TelosTechPill(
                        text = stringResource(I18nR.string.fit_sources_tab),
                        isSelected = selectedTab == FitTab.Sources,
                        onClick = { viewModel.selectedTab.value = FitTab.Sources },
                        accentColor = neonOrange
                    )
                }

                if (selectedTab == FitTab.Sources) {
                    FitSourcesPane(
                        healthConnectAvailable = isHealthConnectAvailable,
                        healthConnectSynced = healthConnectSynced,
                        needsPermission = needsHealthPermission,
                        cycleTrackerEnabled = cycleTrackerEnabled,
                        onToggleCycleTracker = { viewModel.setCycleTrackerEnabled(it) },
                        onRequestHealthConnect = {
                            permissionLauncher.launch(FitHealthHub.readPermissions)
                        },
                        onOpenHealthConnect = { FitHealthHub.openHealthConnectSettings(context) },
                        onOpenVendor = { pkg -> FitHealthHub.openApp(context, pkg) },
                    )
                } else if (selectedTab == FitTab.Dashboard) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (cycleTrackerEnabled) {
                            item {
                                CycleTrackerCard(
                                    lastPeriodDateMillis = lastPeriodDateMillis,
                                    cycleLengthDays = cycleLengthDays,
                                    onLogPeriodStart = { viewModel.logPeriodStart() }
                                )
                            }
                        }
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
                                        Text("Daily Steps & Motion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("Hardware Sensor 👟", style = MaterialTheme.typography.bodySmall, color = neonGreen)
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
                                            MetricRow(
                                                icon = "🔥",
                                                label = "Calories",
                                                value = calories?.let { "$it kcal" } ?: "420 kcal",
                                            )
                                            MetricRow(
                                                icon = "📍",
                                                label = "Distance",
                                                value = distanceKm?.let { "%.1f km".format(it) } ?: "4.8 km",
                                            )
                                            MetricRow(icon = "⏱️", label = "Active Time", value = "42 min")
                                        }
                                    }
                                }
                            }
                        }

                        // Water Intake Card
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .telosTechCard(borderColor = Color(0xFF00B0FF)),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Hydration Tracker 💧", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("$waterIntake / 2500 ml", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B0FF))
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = (waterIntake / 2500f).coerceIn(0f, 1f),
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                        color = Color(0xFF00B0FF),
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(onClick = { viewModel.addWater(250) }, modifier = Modifier.weight(1f)) { Text("+250ml", fontSize = 10.sp) }
                                        Button(onClick = { viewModel.addWater(500) }, modifier = Modifier.weight(1f)) { Text("+500ml", fontSize = 10.sp) }
                                        OutlinedButton(onClick = { viewModel.resetWater() }, modifier = Modifier.weight(1f)) { Text("Reset", fontSize = 10.sp) }
                                    }
                                }
                            }
                        }

                        // BMI & Weight Card
                        item {
                            val w = weight ?: 74.5f
                            val hM = heightCm / 100f
                            val bmi = w / (hM * hM)
                            val bmiCategory = if (bmi < 18.5f) "Underweight" else if (bmi < 25f) "Normal 🟢" else if (bmi < 30f) "Overweight 🟡" else "Obese 🔴"

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .telosTechCard(borderColor = neonOrange),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Body Mass Index (BMI)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("BMI: %.1f".format(bmi), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = neonOrange)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text("Status: $bmiCategory (Weight: $w kg, Height: ${heightCm} cm)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // Medication & Vitamin Reminders
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .telosTechCard(borderColor = neonMagenta),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Medications & Supplements 💊", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    medications.forEach { item: MedicationItem ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleMedication(item.id) },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Checkbox(checked = item.taken, onCheckedChange = { viewModel.toggleMedication(item.id) })
                                                Column {
                                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text("${item.dosage} • ${item.time}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            if (item.taken) {
                                                Text("Taken ✔️", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = neonGreen)
                                            }
                                        }
                                    }
                                }
                            }
                        }

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
                                            Text(heartRate?.toString() ?: "72", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = neonMagenta)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("bpm", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                                        }
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
                                        val sleep = sleepHours
                                        val sleepMinutes = sleep?.let { kotlin.math.round(it * 60f).toInt() }
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(
                                                sleepMinutes?.let { "${it / 60}h" } ?: "7h",
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = neonCyan,
                                            )
                                            if (sleepMinutes != null && sleepMinutes % 60 != 0) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "${sleepMinutes % 60}m",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(bottom = 4.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Nutrition Tab
                    val totalCalories = meals.sumOf { it.calories }
                    val totalProtein = meals.sumOf { it.protein.toDouble() }.toFloat()
                    val totalCarbs = meals.sumOf { it.carbs.toDouble() }.toFloat()
                    val totalFat = meals.sumOf { it.fat.toDouble() }.toFloat()

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .telosTechCard(borderColor = neonCyan),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Daily Nutrition HUD", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("$totalCalories / 2200 kcal", style = MaterialTheme.typography.bodySmall, color = neonCyan)
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        MacroBadge(label = "Protein", value = "${totalProtein.toInt()}g", color = neonGreen)
                                        MacroBadge(label = "Carbs", value = "${totalCarbs.toInt()}g", color = neonCyan)
                                        MacroBadge(label = "Fat", value = "${totalFat.toInt()}g", color = neonOrange)
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Today's Meals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                FilledTonalButton(onClick = { showFoodSearch = true }) {
                                    Text("+ Add Food")
                                }
                            }
                        }

                        items(items = meals, key = { it.name + it.category }) { meal ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .telosTechCard(borderColor = MaterialTheme.colorScheme.outlineVariant),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(meal.name, fontWeight = FontWeight.Bold)
                                        Text("${meal.category} • P: ${meal.protein}g | C: ${meal.carbs}g | F: ${meal.fat}g", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("${meal.calories} kcal", fontWeight = FontWeight.Bold, color = neonCyan)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFoodSearch) {
        FoodSearchDialog(
            onDismiss = { showFoodSearch = false },
            onSelectMeal = { meal ->
                viewModel.addMeal(meal)
                showFoodSearch = false
            }
        )
    }
}

@Composable
private fun CycleTrackerCard(
    lastPeriodDateMillis: Long,
    cycleLengthDays: Int,
    onLogPeriodStart: () -> Unit
) {
    val millisPerDay = 86400000L
    val now = System.currentTimeMillis()
    val daysSinceStart = Math.max(1, ((now - lastPeriodDateMillis) / millisPerDay).toInt() + 1)
    val currentDayInCycle = ((daysSinceStart - 1) % cycleLengthDays) + 1

    val phase = when {
        currentDayInCycle in 1..5 -> "Menstrual Phase 🩸"
        currentDayInCycle in 6..12 -> "Follicular Phase 🌿"
        currentDayInCycle in 13..16 -> "Ovulation Window 🌸"
        else -> "Luteal Phase 🍂"
    }

    val daysUntilNext = cycleLengthDays - currentDayInCycle

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .telosTechCard(borderColor = Color(0xFFFF007F)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cycle Tracker 🌸", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Day $currentDayInCycle of $cycleLengthDays", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF007F))
            }

            Spacer(Modifier.height(8.dp))

            Text("Phase: $phase", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text("Predicted Next Period: in $daysUntilNext days", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onLogPeriodStart,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF007F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log Period Start Today", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MacroBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodSearchDialog(
    onDismiss: () -> Unit,
    onSelectMeal: (LoggedMeal) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FoodProduct>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            results = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        delay(350)
        results = OpenFoodFactsApi.search(trimmed)
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.fit_food_search_title))  } },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(I18nR.string.fit_food_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results) { product ->
                            val name = product.product_name ?: "Unknown Food"
                            val brand = product.brands ?: ""
                            val nutriments = product.nutriments
                            val kcal = nutriments?.energyKcal100g?.toInt() ?: 250
                            val protein = nutriments?.proteins100g ?: 10f
                            val carbs = nutriments?.carbohydrates100g ?: 20f
                            val fat = nutriments?.fat100g ?: 5f

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectMeal(
                                            LoggedMeal(
                                                name = if (brand.isNotEmpty()) "$name ($brand)" else name,
                                                calories = kcal,
                                                protein = protein,
                                                carbs = carbs,
                                                fat = fat,
                                                category = "Snacks"
                                            )
                                        )
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                    if (brand.isNotEmpty()) {
                                        Text(brand, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("$kcal kcal/100g • P: ${protein}g | C: ${carbs}g | F: ${fat}g", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
