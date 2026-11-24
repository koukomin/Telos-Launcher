package de.mm20.launcher2.ui.fit

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FitTab { Dashboard, Nutrition, Sources }

data class LoggedMeal(
    val name: String,
    val calories: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val category: String
)

class FitViewModel : ViewModel() {
    val selectedTab = mutableStateOf(FitTab.Dashboard)

    private val _steps = MutableStateFlow(0)
    val steps: StateFlow<Int> = _steps.asStateFlow()

    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate: StateFlow<Int?> = _heartRate.asStateFlow()

    private val _sleepHours = MutableStateFlow<Float?>(null)
    val sleepHours: StateFlow<Float?> = _sleepHours.asStateFlow()

    private val _waterIntake = MutableStateFlow(0)
    val waterIntake: StateFlow<Int> = _waterIntake.asStateFlow()

    private val _weight = MutableStateFlow<Float?>(null)
    val weight: StateFlow<Float?> = _weight.asStateFlow()

    private val _loggedMeals = MutableStateFlow<List<LoggedMeal>>(emptyList())
    val loggedMeals: StateFlow<List<LoggedMeal>> = _loggedMeals.asStateFlow()

    private val _isHealthConnectAvailable = MutableStateFlow(false)
    val isHealthConnectAvailable: StateFlow<Boolean> = _isHealthConnectAvailable.asStateFlow()

    private val _healthConnectSynced = MutableStateFlow(false)
    val healthConnectSynced: StateFlow<Boolean> = _healthConnectSynced.asStateFlow()

    private val _calories = MutableStateFlow<Long?>(null)
    val calories: StateFlow<Long?> = _calories.asStateFlow()
    private val _distanceKm = MutableStateFlow<Double?>(null)
    val distanceKm: StateFlow<Double?> = _distanceKm.asStateFlow()
    private val _needsHealthPermission = MutableStateFlow(false)
    val needsHealthPermission: StateFlow<Boolean> = _needsHealthPermission.asStateFlow()

    fun addWater() {
        _waterIntake.value += 250
    }

    fun addMeal(meal: LoggedMeal) {
        _loggedMeals.value = _loggedMeals.value + meal
    }

    fun loadHealthConnectData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val available = FitHealthHub.sdkAvailable(context)
                _isHealthConnectAvailable.value = available
                if (!available) {
                    _healthConnectSynced.value = false
                    _needsHealthPermission.value = false
                    return@launch
                }
                val granted = FitHealthHub.grantedPermissions(context)
                val missing = FitHealthHub.readPermissions.any { it !in granted }
                _needsHealthPermission.value = missing
                if (missing) {
                    _healthConnectSynced.value = false
                    return@launch
                }
                val day = FitHealthHub.readToday(context)
                withContext(Dispatchers.Main) {
                    day.steps?.let { _steps.value = it.toInt() }
                    day.heartRateBpm?.let { _heartRate.value = it.toInt() }
                    day.sleepHours?.let { _sleepHours.value = it }
                    day.weightKg?.let { _weight.value = it.toFloat() }
                    _calories.value = day.caloriesKcal
                    _distanceKm.value = day.distanceMeters?.div(1000.0)
                    _healthConnectSynced.value = day.steps != null || day.caloriesKcal != null
                }
            } catch (_: Exception) {
                _healthConnectSynced.value = false
            }
        }
    }
}

