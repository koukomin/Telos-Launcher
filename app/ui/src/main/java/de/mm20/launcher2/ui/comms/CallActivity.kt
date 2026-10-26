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
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.comms.telephony.TelosCallSession
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.theme.LauncherTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class CallActivity : BaseActivity(), SensorEventListener {
    private val commsSettings: CommsSettings by inject()
    private val visibilityOwner = Any()
    private var inCallNotes = true
    private var pocketMode = false
    private var proximitySpeaker = false
    private var proximityNear = false
    private var terminationBounds: androidx.compose.ui.geometry.Rect? = null
    private var terminationGesture = false
    private var sensorManager: SensorManager? = null
    private var gestures: CallGestureTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        de.mm20.launcher2.search.VirtualAppAppearance.applyRecents(this, "telos_phone_app")
        applyVirtualAppOrientation(SettingsDeepLinkContract.ROUTE_COMMS, null, allowDialerLandscape = false)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        enableEdgeToEdge()
        if (!TelosCallSession.ui.value.hasCall) {
            finish()
            return
        }
        // The settings are read without blocking the main thread. Nothing is drawn before they are
        // applied, so that the secure flag is already set when the first frame shows.
        lifecycleScope.launch {
            val snap = commsSettings.snapshot.first()
            applyVirtualAppOrientation(
                SettingsDeepLinkContract.ROUTE_COMMS,
                null,
                snap.allowDialerLandscape,
            )
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
            inCallNotes = snap.inCallNotes
            setContent {
                ProvideCompositionLocals {
                    LauncherTheme {
                        CallScreen(onTerminationBounds = { terminationBounds = it }, onFinished = {
                            CallNotification.cancel(this@CallActivity)
                            finish()
                        })
                    }
                }
            }
            // the sensors may have been due already in onStart
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) registerSensors()
            // keeps the value up to date, so that onStop does not have to wait for the datastore
            commsSettings.inCallNotes.collect { inCallNotes = it }
        }
    }

    override fun onResume() {
        super.onResume()
        de.mm20.launcher2.comms.telephony.CallUiVisibility.setVisible(visibilityOwner, true)
        // Returning from another app must not leave a notes overlay above the full call controls.
        stopService(Intent(this, FloatingNotesService::class.java))
    }

    override fun onPause() {
        de.mm20.launcher2.comms.telephony.CallUiVisibility.setVisible(visibilityOwner, false)
        super.onPause()
    }

    override fun onDestroy() {
        de.mm20.launcher2.comms.telephony.CallUiVisibility.setVisible(visibilityOwner, false)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        registerSensors()
    }

    private fun registerSensors() {
        sensorManager?.unregisterListener(this)
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
        proximityNear = false
        terminationGesture = false
        sensorManager?.unregisterListener(this)
        val notesOn = inCallNotes
        // not while the screen is only rotating
        if (notesOn && !isChangingConfigurations && TelosCallSession.ui.value.hasCall &&
            Settings.canDrawOverlays(this)
        ) {
            try {
                startService(
                    Intent(this, FloatingNotesService::class.java)
                        .putExtra("number", TelosCallSession.ui.value.number)
                )
            } catch (_: IllegalStateException) {
                // background start not allowed
            }
        }
        super.onStop()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            terminationGesture = terminationBounds?.contains(androidx.compose.ui.geometry.Offset(ev.x, ev.y)) == true
        }
        val blocked = blockPocketTouch(pocketMode, proximityNear, TelosCallSession.ui.value.incoming, terminationGesture)
        val result = if (blocked) true else super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) terminationGesture = false
        return result
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
