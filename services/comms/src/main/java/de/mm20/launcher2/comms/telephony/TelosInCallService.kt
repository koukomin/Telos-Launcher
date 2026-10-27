package de.mm20.launcher2.comms.telephony

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import de.mm20.launcher2.comms.overlay.CallOverlayIntents
import de.mm20.launcher2.comms.recording.RecordingCoordinator
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.base.containedScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TelosInCallService : InCallService(), KoinComponent {
    private val commsSettings: CommsSettings by inject()
    private val contacts: de.mm20.launcher2.comms.repository.ContactDirectoryRepository by inject()
    private val callerLookup: de.mm20.launcher2.comms.callerid.CallerLookup by inject()

    private val callScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // A process crash can leave the old non-foreground notification behind.
        if (!TelosCallSession.ui.value.hasCall) CallNotification.cancel(this)
        // Resolve in the service so notifications work even when the call activity cannot open.
        callScope.launch {
            TelosCallSession.ui.map { setOf(it.number, it.secondNumber).filter(String::isNotBlank).toSet() }
                .distinctUntilChanged().collectLatest { numbers ->
                    for (number in numbers) {
                        val identity = withContext(Dispatchers.IO) { lookupCaller(number) }
                        TelosCallSession.setIdentity(number, identity)
                        // A saved number is never sent to the optional community lookup.
                        val settings = commsSettings.snapshot.first()
                        val hidden = de.mm20.launcher2.comms.privacy.HiddenContacts.matches(number, settings.hiddenNumbers)
                        if (shouldLookupCaller(identity, de.mm20.launcher2.comms.remote.RemotePhonebook.lookup(number), hidden)) {
                            callerLookup.lookup(number)?.let { result ->
                                TelosCallSession.setIdentity(number, identity.copy(suggestedName = result.suggestedName,
                                    likelySpam = result.likelySpam, sourceUrl = result.sourceUrl))
                            }
                        }
                    }
                }
        }
        callScope.launch {
            combine(TelosCallSession.ui, commsSettings.snapshot, CallUiVisibility.visible, contacts.observeContacts().catch { emit(emptyList()) }) { state, settings, visible, directory ->
                val hidden = de.mm20.launcher2.comms.privacy.HiddenContacts.expanded(settings.hiddenNumbers, directory)
                val masked = settings.maskHiddenIncoming &&
                    de.mm20.launcher2.comms.privacy.HiddenContacts.matches(state.number, hidden)
                val publicState = if (masked) state.copy(name = getString(de.mm20.launcher2.i18n.R.string.au_phoneb_private_caller),
                    number = "", photoUri = null, callerNameSuggested = false, likelySpam = false, callerSourceUrl = null) else state
                publicState to visible
            }.collect { (state, visible) -> CallNotification.show(this@TelosInCallService, state, visible) }
        }
    }

    private fun lookupCaller(number: String): CallerIdentity = try {
        val uri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(number))
        contentResolver.query(uri, arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME,
            android.provider.ContactsContract.PhoneLookup.PHOTO_URI,
            android.provider.ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) CallerIdentity(cursor.getString(0), cursor.getString(1)?.takeIf(String::isNotBlank) ?: cursor.getString(2)?.takeIf(String::isNotBlank), localContactFound = true, localLookupComplete = true) else CallerIdentity(localLookupComplete = true)
        } ?: CallerIdentity()
    } catch (_: SecurityException) { CallerIdentity() }
      catch (_: IllegalArgumentException) { CallerIdentity() }
      catch (_: android.database.sqlite.SQLiteException) { CallerIdentity() }

    private fun releaseCallState() {
        val ownsSession = TelosCallSession.inCallService === this || !TelosCallSession.ui.value.hasCall
        TelosCallSession.onServiceDestroyed(this)
        if (ownsSession) {
            RecordingCoordinator.stop()
            CallNotification.cancel(this)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        releaseCallState()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        callScope.cancel() // Stop producers before final notification cleanup.
        releaseCallState()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        TelosCallSession.inCallService = this
        popupScope.launch {
            CallBlockEngine.rejectIfNeeded(applicationContext, call)
        }
        TelosCallSession.onCallAdded(call)
        startCallUi()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        TelosCallSession.onCallRemoved(call)
        if (!TelosCallSession.ui.value.hasCall) {
            RecordingCoordinator.stop()
            CallNotification.cancel(this)
            maybeShowPopup()
            TelosCallSession.inCallService = null
        } else {
            startCallUi()
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        TelosCallSession.onAudioStateChanged(audioState)
    }

    private fun startCallUi() {
        val intent = Intent().setClassName(packageName, TelosDialer.CALL_ACTIVITY)
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun maybeShowPopup() {
        // read the data of the ended call now, a new call may replace it while the settings load
        val missed = TelosCallSession.lastEndedWasMissed
        val name = TelosCallSession.lastEndedName
        val number = TelosCallSession.lastEndedNumber
        val ringMs = TelosCallSession.lastEndedRingMs
        val context = applicationContext
        // not tied to the service: it may be unbound as soon as the last call is gone
        popupScope.launch {
            val snap = commsSettings.snapshot.first()
            // a missed call needs the missed call popup, any other call the post-call popup
            if (missed && !snap.missedCallPopup) return@launch
            if (!missed && !snap.postCallPopup) return@launch
            val resolvedName = withContext(Dispatchers.IO) {
                de.mm20.launcher2.comms.remote.DeviceContactsResolver.lookup(context, number).first
            }?.takeIf { it.isNotBlank() } ?: name
            val hidden = de.mm20.launcher2.comms.privacy.HiddenContacts.expanded(snap.hiddenNumbers,
                contacts.observeContacts().first())
            if (snap.maskHiddenIncoming && de.mm20.launcher2.comms.privacy.HiddenContacts.matches(number, hidden)) return@launch
            val popup = Intent().setClassName(context.packageName, "de.mm20.launcher2.ui.comms.MissedCallPopupActivity")
            popup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            popup.putExtra(CallOverlayIntents.EXTRA_NAME, resolvedName)
            popup.putExtra(CallOverlayIntents.EXTRA_NUMBER, number)
            popup.putExtra(CallOverlayIntents.EXTRA_RING_MS, ringMs)
            try {
                context.startActivity(popup)
            } catch (_: Exception) {
            }
        }
    }

    private companion object {
        val popupScope = containedScope(Dispatchers.Main.immediate)
    }
}
