package de.mm20.launcher2.freeze

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/** Explicit, non-exported alarm target; pending deadlines survive launcher process recreation. */
class ScreenOffFreezeReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try { get<AutoFreezeController>().dispatchScreenOffAlarm() }
            catch (error: Exception) { android.util.Log.w("ScreenOffFreeze", "Alarm failed", error) }
            finally { pending.finish() }
        }
    }
}
