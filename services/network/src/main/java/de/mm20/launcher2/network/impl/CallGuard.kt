package de.mm20.launcher2.network.impl

import android.content.Context
import android.media.AudioManager
import android.telephony.TelephonyManager

/** Best-effort "a call is in progress" for [NetworkSettingsValues.disableOnCall]. */
internal object CallGuard {
    @Volatile
    var inCall: Boolean = false
        private set

    fun refresh(context: Context) {
        inCall = runCatching {
            val tm = context.getSystemService(TelephonyManager::class.java)
            @Suppress("DEPRECATION")
            val call = tm?.callState == TelephonyManager.CALL_STATE_OFFHOOK ||
                tm?.callState == TelephonyManager.CALL_STATE_RINGING
            val audio = context.getSystemService(AudioManager::class.java)
            val mode = audio?.mode == AudioManager.MODE_IN_CALL ||
                audio?.mode == AudioManager.MODE_IN_COMMUNICATION
            call || mode
        }.getOrDefault(false)
    }
}
