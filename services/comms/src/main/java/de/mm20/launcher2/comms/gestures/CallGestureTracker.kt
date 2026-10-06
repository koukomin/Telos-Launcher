package de.mm20.launcher2.comms.gestures

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class CallGestureTracker(
    private val raiseToAnswer: Boolean,
    private val flipToDecline: Boolean,
    private val rainMode: Boolean,
    private val onAnswer: () -> Unit,
    private val onDecline: () -> Unit,
) {
    private var proximityNear = false
    private var ax = 0f
    private var ay = 0f
    private var az = 0f
    private var raiseTicks = 0
    private var flipTicks = 0
    private var gravityX = 0f
    private var hasGravity = false
    private var lastDir = 0
    private var strokes = 0
    private var lastStroke = 0L
    private var fired = false

    fun onProximity(event: SensorEvent, maxRange: Float): Boolean {
        proximityNear = event.values.firstOrNull()?.let { it < maxRange } == true
        return proximityNear
    }

    fun onAccelerometer(event: SensorEvent) {
        if (fired) return
        ax = event.values[0]
        ay = event.values.getOrElse(1) { 0f }
        az = event.values.getOrElse(2) { 0f }
        val g = sqrt(ax * ax + ay * ay + az * az).coerceAtLeast(0.01f)
        val inclination = Math.toDegrees(atan2(ay.toDouble(), az.toDouble())).toInt()
        if (raiseToAnswer && proximityNear) {
            val earPose = abs(inclination) in 40..140 || abs(ay / g) > 0.55f
            if (earPose) {
                raiseTicks++
                if (raiseTicks >= 3) {
                    fired = true
                    onAnswer()
                    return
                }
            } else raiseTicks = 0
        }
        if (flipToDecline && az < -7f && abs(ax) < 4f) {
            flipTicks++
            if (flipTicks >= 3) {
                fired = true
                onDecline()
                return
            }
        } else flipTicks = 0
        if (rainMode) processRain(ax)
    }

    private fun processRain(rawAx: Float) {
        if (!hasGravity) {
            gravityX = rawAx.coerceIn(-SensorManager.GRAVITY_EARTH, SensorManager.GRAVITY_EARTH)
            hasGravity = true
            return
        }
        gravityX = 0.95f * gravityX + 0.05f * rawAx
        val dyn = rawAx - gravityX
        val now = System.currentTimeMillis()
        val thresh = 3.2f * SensorManager.GRAVITY_EARTH
        val dir = when {
            dyn > thresh -> 1
            dyn < -thresh -> -1
            else -> 0
        }
        if (dir == 0) return
        if (strokes == 0 || (dir != lastDir && now - lastStroke in 80..700)) {
            strokes++
            lastDir = dir
            lastStroke = now
            if (strokes >= 4) {
                fired = true
                onAnswer()
            }
        } else if (now - lastStroke > 700) {
            strokes = 1
            lastDir = dir
            lastStroke = now
        }
    }

    fun reset() {
        fired = false
        raiseTicks = 0
        flipTicks = 0
        strokes = 0
    }
}
