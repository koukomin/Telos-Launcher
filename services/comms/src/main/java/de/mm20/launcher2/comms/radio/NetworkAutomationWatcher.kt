package de.mm20.launcher2.comms.radio

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager

class NetworkAutomationWatcher(context: Context) {
    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        context.applicationContext.registerReceiver(NetworkAutomationReceiver(), filter)
    }
}
