package de.mm20.launcher2.comms.radio

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.util.Log
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import rikka.shizuku.Shizuku
import java.io.DataOutputStream

object CellularRadio : KoinComponent {
    private const val TAG = "CellularRadio"
    private val commsSettings: CommsSettings by inject()

    suspend fun applyPreferred(context: Context, overrideMode: String? = null): Boolean {
        val snap = commsSettings.snapshot.first()
        val mode = overrideMode ?: snap.preferredNetworkMode
        if (mode == "auto") return true
        val ril = rilConstant(mode)
        val subId = runCatching {
            SubscriptionManager.getDefaultDataSubscriptionId()
        }.getOrDefault(-1)
        val cmd = if (subId > 0) {
            "cmd phone set-allowed-network-types-for-sub $subId $ril"
        } else {
            "cmd phone set-allowed-network-types-for-sub 1 $ril"
        }
        val order = when (snap.networkBackend) {
            "root" -> listOf("root", "shizuku")
            "shizuku" -> listOf("shizuku", "root")
            else -> listOf("shizuku", "root")
        }
        for (backend in order) {
            val ok = when (backend) {
                "shizuku" -> shizuku(cmd)
                else -> root(cmd)
            }
            if (ok) {
                Log.i(TAG, "Set network $mode via $backend")
                return true
            }
        }
        return false
    }

    private fun rilConstant(mode: String): Int = when (mode) {
        "lte" -> 11
        "nr" -> 23
        "nr_lte" -> 24
        else -> 26
    }

    private fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    private suspend fun shizuku(cmd: String): Boolean = withContext(Dispatchers.IO) {
        if (!shizukuReady()) return@withContext false
        try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) ?: return@withContext false
            (process::class.java.getMethod("waitFor").invoke(process) as Int) == 0
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku network cmd failed", e)
            false
        }
    }

    private suspend fun root(cmd: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { stdin ->
                stdin.writeBytes("$cmd\nexit\n")
                stdin.flush()
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.w(TAG, "Root network cmd failed", e)
            false
        }
    }
}
