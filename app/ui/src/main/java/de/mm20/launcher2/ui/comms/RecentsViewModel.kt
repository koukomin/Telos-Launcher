package de.mm20.launcher2.ui.comms
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.appcompat.app.AppCompatActivity
import de.mm20.launcher2.comms.model.CallLogEntry
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.comms.repository.CallLogRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.comms.privacy.CallGuard
import de.mm20.launcher2.comms.privacy.HiddenContacts
import de.mm20.launcher2.comms.privacy.PrivacySession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RecentsViewModel : ViewModel(), KoinComponent {

    private val callLogRepository: CallLogRepository by inject()
    private val commsSettings: de.mm20.launcher2.preferences.comms.CommsSettings by inject()
    private val permissionsManager: PermissionsManager by inject()

    private val contactDirectory: de.mm20.launcher2.comms.repository.ContactDirectoryRepository by inject()

    private val visibleRecents = combine(
        callLogRepository.observeRecents(),
        commsSettings.hiddenNumbers,
        commsSettings.hideFromRecents,
        PrivacySession.hiderUnlocked,
        contactDirectory.observeContacts(),
    ) { list, storedHidden, hide, unlocked, directory ->
        val hidden = HiddenContacts.expanded(storedHidden, directory)
        val visible = if (!hide || unlocked) list
        else list.filter { !HiddenContacts.matches(it.phoneNumber, hidden) }
        visible.map { call ->
            if (call.displayName.isNullOrBlank()) {
                val remote = de.mm20.launcher2.comms.remote.RemotePhonebook.lookup(call.phoneNumber)
                if (remote != null) call.copy(displayName = remote) else call
            } else call
        }
    }

    private val identifiedRecents = combine(
        visibleRecents, contactDirectory.observeContacts(),
    ) { calls, contacts ->
        calls.map { call ->
            val contact = contacts.firstOrNull { person ->
                person.phoneNumbers.any { de.mm20.launcher2.comms.PhoneNumbers.match(it, call.phoneNumber) }
            }
            if (contact == null) call else call.copy(
                displayName = contact.displayName.takeIf { it.isNotBlank() } ?: call.displayName,
                photoUri = contact.photoUri ?: call.photoUri,
            )
        }
    }
    private val callerLookup: de.mm20.launcher2.comms.callerid.CallerLookup by inject()
    val recents: StateFlow<List<CallLogEntry>> = combine(identifiedRecents, callerLookup.results, commsSettings.onlineCallerLookup) { calls, results, enabled ->
        calls.map { call ->
            val result = if (enabled && call.displayName.isNullOrBlank())
                results[de.mm20.launcher2.comms.callerid.CallerLookupParser.greekNumber(call.phoneNumber)] else null
            if (result?.suggestedName.isNullOrBlank()) call else call.copy(displayName = result!!.suggestedName,
                callerNameSuggested = true, callerSourceUrl = result.sourceUrl)
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasCallLogPermission: StateFlow<Boolean> = permissionsManager.hasPermission(PermissionGroup.CallLog)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun requestCallLogPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.CallLog)
    }

    val clirPrefix = commsSettings.clirPrefix.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val clirEnabled = commsSettings.clirEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val tapToCall = commsSettings.tapToCall.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val confirmBeforeCall = commsSettings.confirmBeforeCall.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showNumbers = commsSettings.showNumbersInRecents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun dial(context: Context, phoneNumber: String) {
        if (phoneNumber.isEmpty()) return
        viewModelScope.launch { CallGuard.place(context, phoneNumber) }
    }

    fun delete(call: CallLogEntry) {
        viewModelScope.launch { callLogRepository.deleteById(call.id) }
    }

    fun export(context: Context, calls: List<CallLogEntry>) {
        if (calls.isEmpty()) return
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val text = calls.joinToString("\n") { call ->
            val name = call.displayName ?: call.phoneNumber
            val whenStr = format.format(java.util.Date(call.timestamp))
            "$whenStr\t${call.type}\t$name\t${call.phoneNumber}"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(I18nR.string.au_phonea_export_subject))
        }
        context.tryStartActivity(Intent.createChooser(intent, context.getString(I18nR.string.hc_export)))
    }
}
