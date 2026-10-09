package de.mm20.launcher2.comms.telephony

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import de.mm20.launcher2.base.containedScope
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Short vibration pulses when an outgoing call is answered or a call ends (settings "Vibrate on answer" / "Vibrate on hangup"). */
object CallHaptics : KoinComponent {
    private val commsSettings: CommsSettings by inject()
    private val scope = containedScope(Dispatchers.Main.immediate)

    fun onAnswered(context: Context) = pulse(context, answered = true)

    fun onHangup(context: Context) = pulse(context, answered = false)

    private fun pulse(context: Context, answered: Boolean) {
        val appContext = context.applicationContext
        scope.launch {
            val snap = commsSettings.snapshot.first()
            if (answered && !snap.vibrateOnAnswer) return@launch
            if (!answered && !snap.vibrateOnHangup) return@launch
            runCatching {
                val vibrator = appContext.getSystemService(Vibrator::class.java)
                vibrator?.vibrate(VibrationEffect.createOneShot(if (answered) 120L else 80L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }
}
