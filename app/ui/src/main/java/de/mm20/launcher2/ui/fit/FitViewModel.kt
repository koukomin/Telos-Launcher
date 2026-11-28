package de.mm20.launcher2.ui.fit

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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

data class MedicationItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val dosage: String,
    val time: String,
    val taken: Boolean = false
)

class FitViewModel : ViewModel(), SensorEventListener {
    val selectedTab = mutableStateOf(FitTab.Dashboard)

    private val _steps = MutableStateFlow(0)
    val steps: StateFlow<Int> = _steps.asStateFlow()

    private val _heartRate = MutableStateFlow<Int?>(72)
    val heartRate: StateFlow<Int?> = _heartRate.asStateFlow()

    private val _sleepHours = MutableStateFlow<Float?>(7.5f)
    val sleepHours: StateFlow<Float?> = _sleepHours.asStateFlow()

    private val _waterIntake = MutableStateFlow(1250)
    val waterIntake: StateFlow<Int> = _waterIntake.asStateFlow()

    private val _weight = MutableStateFlow<Float?>(74.5f)
    val weight: StateFlow<Float?> = _weight.asStateFlow()

    private val _heightCm = MutableStateFlow(178f)
    val heightCm: StateFlow<Float> = _heightCm.asStateFlow()

    private val _loggedMeals = MutableStateFlow<List<LoggedMeal>>(emptyList())
    val loggedMeals: StateFlow<List<LoggedMeal>> = _loggedMeals.asStateFlow()

    private val _medications = MutableStateFlow<List<MedicationItem>>(
        kotlin.collections.listOf(
            MedicationItem(name = "Vitamin D3", dosage = "2000 IU", time = "09:00", taken = true),
            MedicationItem(name = "Omega-3", dosage = "1000 mg", time = "13:00", taken = false),
            MedicationItem(name = "Magnesium", dosage = "400 mg", time = "21:00", taken = false)
        )
    )
    val medications: StateFlow<List<MedicationItem>> = _medications.asStateFlow()

    private val _cycleTrackerEnabled = MutableStateFlow(false)
    val cycleTrackerEnabled: StateFlow<Boolean> = _cycleTrackerEnabled.asStateFlow()

    private val _lastPeriodDateMillis = MutableStateFlow(System.currentTimeMillis() - 14 * 86400000L)
    val lastPeriodDateMillis: StateFlow<Long> = _lastPeriodDateMillis.asStateFlow()

    private val _cycleLengthDays = MutableStateFlow(28)
    val cycleLengthDays: StateFlow<Int> = _cycleLengthDays.asStateFlow()

    private val _isHealthConnectAvailable = MutableStateFlow(false)
    val isHealthConnectAvailable: StateFlow<Boolean> = _isHealthConnectAvailable.asStateFlow()

    private val _healthConnectSynced = MutableStateFlow(false)
    val healthConnectSynced: StateFlow<Boolean> = _healthConnectSynced.asStateFlow()

    private val _calories = MutableStateFlow<Long?>(420L)
    val calories: StateFlow<Long?> = _calories.asStateFlow()
    private val _distanceKm = MutableStateFlow<Double?>(4.8)
    val distanceKm: StateFlow<Double?> = _distanceKm.asStateFlow()
    private val _needsHealthPermission = MutableStateFlow(false)
    val needsHealthPermission: StateFlow<Boolean> = _needsHealthPermission.asStateFlow()

    private var sensorManager: SensorManager? = null
    private var initialSensorSteps = -1f

    fun addWater(amount: Int = 250) {
        _waterIntake.value += amount
    }

    fun resetWater() {
        _waterIntake.value = 0
    }

    fun toggleMedication(id: String) {
        _medications.value = _medications.value.map {
            if (it.id == id) it.copy(taken = !it.taken) else it
        }
    }

    fun setHeightAndWeight(heightCm: Float, weightKg: Float) {
        _heightCm.value = heightCm
        _weight.value = weightKg
    }

    fun setCycleTrackerEnabled(enabled: Boolean) {
        _cycleTrackerEnabled.value = enabled
    }

    fun logPeriodStart(timeMillis: Long = System.currentTimeMillis()) {
        _lastPeriodDateMillis.value = timeMillis
    }

    fun addMeal(meal: LoggedMeal) {
        _loggedMeals.value = _loggedMeals.value + meal
    }

    fun startDeviceSensorStepTracking(context: Context) {
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
            if (stepSensor != null) {
                sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val ev = event ?: return
        if (ev.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = ev.values[0]
            if (initialSensorSteps < 0) {
                initialSensorSteps = totalSteps - _steps.value
            }
            val currentDailySteps = Math.max(0f, totalSteps - initialSensorSteps).toInt()
            if (currentDailySteps > _steps.value) {
                _steps.value = currentDailySteps
            }
        } else if (ev.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            _steps.value += 1
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun loadHealthConnectData(context: Context) {
        startDeviceSensorStepTracking(context)
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
                    day.steps?.let { if (it.toInt() > _steps.value) _steps.value = it.toInt() }
                    day.heartRateBpm?.let { _heartRate.value = it.toInt() }
                    day.sleepHours?.let { _sleepHours.value = it }
                    day.weightKg?.let { _weight.value = it.toFloat() }
                    if (day.caloriesKcal != null) _calories.value = day.caloriesKcal
                    if (day.distanceMeters != null) _distanceKm.value = day.distanceMeters.div(1000.0)
                    _healthConnectSynced.value = day.steps != null || day.caloriesKcal != null
                }
            } catch (_: Exception) {
                _healthConnectSynced.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager?.unregisterListener(this)
    }
}
