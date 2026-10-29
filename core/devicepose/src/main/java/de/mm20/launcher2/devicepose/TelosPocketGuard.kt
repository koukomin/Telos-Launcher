package de.mm20.launcher2.devicepose

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sensor-backed Pocket Anti-Touch Guard & Device Gesture Listener.
 * - Detects when the device is inside a pocket/bag via Proximity sensor.
 * - Detects Flip Face-Down gesture via Accelerometer to trigger Mute or Smart Freeze.
 */
class TelosPocketGuard private constructor(context: Context) : SensorEventListener {

    private val appContext: Context = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val isListening = AtomicBoolean(false)

    private val _isInPocket = MutableStateFlow(false)
    val isInPocket: StateFlow<Boolean> = _isInPocket.asStateFlow()

    private val _isFaceDown = MutableStateFlow(false)
    val isFaceDown: StateFlow<Boolean> = _isFaceDown.asStateFlow()

    var onFlipFaceDownAction: (() -> Unit)? = null

    fun register() {
        if (!isListening.compareAndSet(false, true)) return
        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometerSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        Log.i(TAG, "TelosPocketGuard sensors registered")
    }

    fun unregister() {
        if (!isListening.compareAndSet(true, false)) return
        sensorManager?.unregisterListener(this)
        _isInPocket.value = false
        _isFaceDown.value = false
        Log.i(TAG, "TelosPocketGuard sensors unregistered")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values.getOrNull(0) ?: return
                val maxRange = proximitySensor?.maximumRange ?: 5.0f
                val inPocket = distance < maxRange && distance < 1.0f
                if (_isInPocket.value != inPocket) {
                    _isInPocket.value = inPocket
                    Log.d(TAG, "Pocket state changed: isInPocket=$inPocket")
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val zAxis = event.values.getOrNull(2) ?: return
                val faceDown = zAxis < -8.5f // Device screen facing down towards ground
                if (!_isFaceDown.value && faceDown) {
                    _isFaceDown.value = true
                    Log.d(TAG, "Device flipped face-down!")
                    onFlipFaceDownAction?.invoke()
                } else if (!faceDown) {
                    _isFaceDown.value = false
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val TAG = "TelosPocketGuard"

        @Volatile
        private var instance: TelosPocketGuard? = null

        fun getInstance(context: Context): TelosPocketGuard {
            return instance ?: synchronized(this) {
                instance ?: TelosPocketGuard(context.applicationContext).also { instance = it }
            }
        }
    }
}
