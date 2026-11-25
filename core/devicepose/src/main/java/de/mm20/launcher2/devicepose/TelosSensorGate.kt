package de.mm20.launcher2.devicepose

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Lifecycle-aware Hardware Sensor Gate for Telos Launcher.
 * Automatically unbinds hardware sensors (proximity, accelerometer, gyro) when the app/screen
 * is stopped or backgrounded, eliminating background battery drain.
 */
class TelosSensorGate(
    private val sensorManager: SensorManager,
    private val sensorType: Int,
    private val samplingRate: Int = SensorManager.SENSOR_DELAY_NORMAL,
    private val onSensorChangedAction: (SensorEvent) -> Unit,
) : DefaultLifecycleObserver, SensorEventListener {

    private val sensor: Sensor? = sensorManager.getDefaultSensor(sensorType)
    private var isListening = false

    override fun onStart(owner: LifecycleOwner) {
        if (!isListening && sensor != null) {
            isListening = sensorManager.registerListener(this, sensor, samplingRate)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            onSensorChangedAction(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
