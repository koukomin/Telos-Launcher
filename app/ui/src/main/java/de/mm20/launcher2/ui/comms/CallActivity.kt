package de.mm20.launcher2.ui.comms

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.mm20.launcher2.comms.gestures.CallGestureTracker
import de.mm20.launcher2.comms.telephony.CallNotification
import de.mm20.launcher2.comms.telephony.TelosCallSession
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.theme.LauncherTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class CallActivity : BaseActivity(), SensorEventListener {
    private val commsSettings: CommsSettings by inject()
    private var pocketMode = false
    private var proximitySpeaker = false
    private var proximityNear = false
    private var sensorManager: SensorManager? = null
    private var gestures: CallGestureTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        val snap = runBlocking { commsSettings.snapshot.first() }
        if (snap.secureCallScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        pocketMode = snap.pocketMode
        proximitySpeaker = snap.proximitySpeaker
        if (snap.raiseToAnswer || snap.flipToDecline || snap.rainMode) {
            gestures = CallGestureTracker(
                raiseToAnswer = snap.raiseToAnswer,
                flipToDecline = snap.flipToDecline,
                rainMode = snap.rainMode,
                onAnswer = { TelosCallSession.answer() },
                onDecline = { TelosCallSession.reject() },
            )
        }
        enableEdgeToEdge()
        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    CallScreen(onFinished = {
                        CallNotification.cancel(this)
                        finish()
                    })
                }
            }
        }
        if (!TelosCallSession.ui.value.hasCall) {
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        val needSensors = pocketMode || proximitySpeaker || gestures != null
        if (needSensors) {
            sensorManager = getSystemService(SensorManager::class.java)
            sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            if (gestures != null) {
                sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                    sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                }
            }
        }
    }

    override fun onStop() {
        sensorManager?.unregisterListener(this)
        val notesOn = runBlocking { commsSettings.inCallNotes.first() }
        if (notesOn && TelosCallSession.ui.value.hasCall && Settings.canDrawOverlays(this)) {
            startService(
                Intent(this, FloatingNotesService::class.java)
                    .putExtra("number", TelosCallSession.ui.value.number)
            )
        }
        super.onStop()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (pocketMode && proximityNear && TelosCallSession.ui.value.incoming) {
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        when (e.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                proximityNear = gestures?.onProximity(e, e.sensor.maximumRange)
                    ?: (e.values.firstOrNull()?.let { it < (e.sensor.maximumRange) } == true)
                if (proximitySpeaker && TelosCallSession.ui.value.active) {
                    val speaker = TelosCallSession.ui.value.speaker
                    if (!proximityNear && !speaker) TelosCallSession.toggleSpeaker()
                    if (proximityNear && speaker) TelosCallSession.toggleSpeaker()
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (TelosCallSession.ui.value.incoming) gestures?.onAccelerometer(e)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
