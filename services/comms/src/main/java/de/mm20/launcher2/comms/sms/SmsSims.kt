package de.mm20.launcher2.comms.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * The active SIM cards that can send a text message, and which one was used for which
 * conversation. Without the READ_PHONE_STATE permission no SIM is listed and everything
 * behaves as on a phone with one SIM (the system default is used).
 */
object SmsSims {
    data class Sim(val subId: Int, val slot: Int, val label: String)

    private const val PREFS = "telos_sms_sim"
    const val NAME_PREFS = "telos_sim_names"

    fun customName(context: Context, subId: Int): String? =
        runCatching {
            context.getSharedPreferences(NAME_PREFS, Context.MODE_PRIVATE).getString(subId.toString(), null)
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    fun setCustomName(context: Context, subId: Int, name: String?) {
        runCatching {
            val ed = context.getSharedPreferences(NAME_PREFS, Context.MODE_PRIVATE).edit()
            if (name.isNullOrBlank()) ed.remove(subId.toString()) else ed.putString(subId.toString(), name.trim())
            ed.apply()
        }
    }

    fun labelFor(context: Context, subId: Int, slot: Int, displayName: String?, carrierName: String?): String {
        customName(context, subId)?.let { return it }
        val slotLabel = "SIM ${slot + 1}"
        val androidName = displayName?.trim().orEmpty()
        if (androidName.isNotBlank() &&
            !androidName.equals(slotLabel, ignoreCase = true) &&
            !androidName.equals("CARD ${slot + 1}", ignoreCase = true)
        ) {
            return androidName
        }
        val carrier = carrierName?.trim()?.takeIf { it.isNotBlank() }
        return if (carrier != null) "$slotLabel · $carrier" else slotLabel
    }

    /** The active SIMs, ordered by slot. Fewer than two means there is nothing to choose. */
    fun active(context: Context): List<Sim> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        return try {
            val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
            manager.activeSubscriptionInfoList.orEmpty().sortedBy { it.simSlotIndex }.map {
                Sim(
                    it.subscriptionId,
                    it.simSlotIndex,
                    labelFor(
                        context,
                        it.subscriptionId,
                        it.simSlotIndex,
                        it.displayName?.toString(),
                        it.carrierName?.toString(),
                    ),
                )
            }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    private fun key(address: String): String =
        if (',' in address) address.filterNot { it.isWhitespace() } else address.filter { it.isDigit() }.takeLast(9).ifEmpty { address }

    /** The subscription last used for [address], or -1 */
    fun remembered(context: Context, address: String): Int =
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(key(address), -1) }.getOrDefault(-1)

    fun remember(context: Context, address: String, subId: Int) {
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(key(address), subId).apply() }
    }

    /**
     * The SIM to preselect for [address] among [sims]: the one used last for it, else the system's
     * default SIM for text messages, else the first. Null when there is nothing to choose.
     */
    fun preselected(context: Context, address: String, sims: List<Sim>): Sim? {
        if (sims.size < 2) return null
        val last = remembered(context, address)
        sims.firstOrNull { it.subId == last }?.let { return it }
        val default = runCatching { SubscriptionManager.getDefaultSmsSubscriptionId() }.getOrDefault(-1)
        return sims.firstOrNull { it.subId == default } ?: sims.first()
    }
}
