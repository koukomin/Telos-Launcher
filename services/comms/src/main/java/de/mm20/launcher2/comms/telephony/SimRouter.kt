package de.mm20.launcher2.comms.telephony

import android.content.Context
import android.telecom.PhoneAccountHandle
import de.mm20.launcher2.base.containedScope
import de.mm20.launcher2.comms.PhoneNumbers
import de.mm20.launcher2.comms.repository.CallLogRepository
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object SimRouter : KoinComponent {
    private val commsSettings: CommsSettings by inject()
    private val callLog: CallLogRepository by inject()

    private val scope = containedScope(Dispatchers.Main.immediate)

    fun place(context: Context, number: String, handle: PhoneAccountHandle? = null) {
        if (handle != null) {
            placeNow(context, number, handle)
            return
        }
        val app = context.applicationContext
        scope.launch { placeAwait(app, number) }
    }

    /** Resolves the SIM off the main thread, then places the call. Call from the main thread. */
    suspend fun placeAwait(context: Context, number: String) {
        placeNow(context, number, withContext(Dispatchers.IO) { resolve(context, number) })
    }

    private fun placeNow(context: Context, number: String, handle: PhoneAccountHandle?) {
        if (handle != null) commsSettings.setLastUsedSim(handle.id)
        TelosDialer.placeCall(context, number, handle)
    }

    fun defaultNumberFor(contactId: Long, numbers: List<String>): String {
        if (numbers.isEmpty()) return ""
        val mapped = runBlocking { commsSettings.contactDefaultNumbers.first() }[contactId.toString()]
        return numbers.find { PhoneNumbers.match(it, mapped.orEmpty()) } ?: numbers.first()
    }

    suspend fun resolve(context: Context, number: String): PhoneAccountHandle? {
        val sims = TelosDialer.callCapableSims(context)
        if (sims.size < 2) return sims.firstOrNull()?.handle
        val snap = commsSettings.snapshot.first()
        val bound = snap.numberDefaultSim.entries.firstOrNull { PhoneNumbers.match(it.key, number) }?.value
        match(sims, bound)?.let { return it }
        return when (snap.defaultSim) {
            "ask" -> null
            "last" -> match(sims, snap.lastUsedSim) ?: sims.first().handle
            "log" -> {
                val recents = callLog.observeRecents().first()
                val account = recents.firstOrNull { PhoneNumbers.match(it.phoneNumber, number) }?.simAccountId
                match(sims, account) ?: match(sims, snap.lastUsedSim)
            }
            "sim1" -> sims.getOrNull(0)?.handle
            "sim2" -> sims.getOrNull(1)?.handle
            else -> match(sims, snap.defaultSim) ?: match(sims, snap.lastUsedSim)
        }
    }

    private fun match(sims: List<TelosDialer.SimLine>, key: String?): PhoneAccountHandle? {
        if (key.isNullOrBlank()) return null
        return sims.find { it.handle.id == key || it.label.equals(key, ignoreCase = true) }?.handle
            ?: sims.find { it.handle.id.endsWith(key) }?.handle
    }
}
