package de.mm20.launcher2.ui.islandoverlay

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Whether a call is currently ringing or active. Requires READ_PHONE_STATE - opt-in, and the
 * Dynamic Island simply never shows a call if it isn't granted.
 */
class CallStateProvider(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
) {
    val isInCall: Flow<Boolean> = callbackFlow {
        if (!permissionsManager.checkPermissionOnce(PermissionGroup.PhoneState)) {
            trySend(false)
            awaitClose {}
            return@callbackFlow
        }
        val telephonyManager = context.getSystemService<TelephonyManager>()
        if (telephonyManager == null) {
            trySend(false)
            awaitClose {}
            return@callbackFlow
        }

        fun isActiveState(state: Int) =
            state == TelephonyManager.CALL_STATE_OFFHOOK || state == TelephonyManager.CALL_STATE_RINGING

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    trySend(isActiveState(state))
                }
            }
            try {
                telephonyManager.registerTelephonyCallback(
                    ContextCompat.getMainExecutor(context),
                    callback,
                )
            } catch (e: SecurityException) {
                trySend(false)
                awaitClose {}
                return@callbackFlow
            }
            awaitClose { telephonyManager.unregisterTelephonyCallback(callback) }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    trySend(isActiveState(state))
                }
            }
            try {
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            } catch (e: SecurityException) {
                trySend(false)
                awaitClose {}
                return@callbackFlow
            }
            awaitClose {
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)
            }
        }
    }
}
