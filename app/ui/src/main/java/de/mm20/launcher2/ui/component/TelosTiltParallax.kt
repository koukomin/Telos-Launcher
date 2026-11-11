package de.mm20.launcher2.ui.component

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * Optimized Low-Pass Filtered 3D Gyroscope Parallax Tilt Reaction Modifier.
 * Smoothes out sensor noise and eliminates jitter for 120 FPS performance.
 */
@Composable
fun Modifier.telosTiltParallax(
    maxTiltAngle: Float = 6f
): Modifier {
    val context = LocalContext.current
    var pitch by remember { mutableFloatStateOf(0f) }
    var roll by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var lastAx = 0f
        var lastAy = 0f
        val filterAlpha = 0.75f // Low-pass filter coefficient

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val rawAx = event.values[0]
                    val rawAy = event.values[1]

                    // Apply low-pass filter
                    val filteredAx = filterAlpha * lastAx + (1f - filterAlpha) * rawAx
                    val filteredAy = filterAlpha * lastAy + (1f - filterAlpha) * rawAy
                    lastAx = filteredAx
                    lastAy = filteredAy

                    roll = (filteredAx * 1.2f).coerceIn(-maxTiltAngle, maxTiltAngle)
                    pitch = (filteredAy * 1.2f).coerceIn(-maxTiltAngle, maxTiltAngle)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    val animatedRotationX by animateFloatAsState(targetValue = -pitch, animationSpec = tween(150), label = "rotX")
    val animatedRotationY by animateFloatAsState(targetValue = roll, animationSpec = tween(150), label = "rotY")

    return this.graphicsLayer {
        rotationX = animatedRotationX
        rotationY = animatedRotationY
        cameraDistance = 16f * density
    }
}
