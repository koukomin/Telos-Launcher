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
 * 3D Gyroscope Parallax Tilt Reaction Modifier.
 * Reacts dynamically to physical phone tilt angle for HUD cards and dialogs.
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

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val ax = event.values[0]
                    val ay = event.values[1]

                    roll = (ax * 1.2f).coerceIn(-maxTiltAngle, maxTiltAngle)
                    pitch = (ay * 1.2f).coerceIn(-maxTiltAngle, maxTiltAngle)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    val animatedRotationX by animateFloatAsState(targetValue = -pitch, animationSpec = tween(200), label = "rotX")
    val animatedRotationY by animateFloatAsState(targetValue = roll, animationSpec = tween(200), label = "rotY")

    return this.graphicsLayer {
        rotationX = animatedRotationX
        rotationY = animatedRotationY
        cameraDistance = 16f * density
    }
}
