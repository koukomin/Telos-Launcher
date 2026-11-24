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
    val meals by viewModel.loggedMeals.collectAsStateWithLifecycle()
    val isHealthConnectAvailable by viewModel.isHealthConnectAvailable.collectAsStateWithLifecycle()
    val healthConnectSynced by viewModel.healthConnectSynced.collectAsStateWithLifecycle()
    val calories by viewModel.calories.collectAsStateWithLifecycle()
    val distanceKm by viewModel.distanceKm.collectAsStateWithLifecycle()
    val needsHealthPermission by viewModel.needsHealthPermission.collectAsStateWithLifecycle()
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
                                        else -> stringResource(I18nR.string.fit_status_local_demo)
                                    },
                                    fontSize = 9.sp,
                                    color = when {
                                        healthConnectSynced -> neonCyan
                                        isHealthConnectAvailable -> neonOrange
                                        else -> neonGreen
                                    },
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
                                            MetricRow(
                                                icon = "🔥",
                                                label = "Calories",
                                                value = calories?.let { "$it kcal" } ?: "—",
                                            )
                                            MetricRow(
                                                icon = "📍",
                                                label = "Distance",
                                                value = distanceKm?.let { "%.1f km".format(it) } ?: "—",
                                            )
                                            MetricRow(icon = "⏱️", label = "Active Time", value = "—")
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
                                            Text(heartRate?.toString() ?: "—", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = neonMagenta)
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
                                                sleepMinutes?.let { "${it / 60}h" } ?: "—",
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
                                        Text(
                                            weight?.let { "$it kg" } ?: "—",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = neonOrange,
                                        )
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
