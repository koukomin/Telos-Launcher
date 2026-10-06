package de.mm20.launcher2.ui.comms

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.appcompat.app.AppCompatActivity
import de.mm20.launcher2.comms.model.CallLogEntry
import de.mm20.launcher2.comms.repository.CallLogRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.comms.privacy.CallGuard
import de.mm20.launcher2.comms.privacy.HiddenContacts
import de.mm20.launcher2.comms.privacy.PrivacySession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RecentsViewModel : ViewModel(), KoinComponent {

    private val callLogRepository: CallLogRepository by inject()
    private val commsSettings: de.mm20.launcher2.preferences.comms.CommsSettings by inject()
    private val permissionsManager: PermissionsManager by inject()

    val recents: StateFlow<List<CallLogEntry>> = combine(
        callLogRepository.observeRecents(),
        commsSettings.hiddenNumbers,
        commsSettings.hideFromRecents,
        PrivacySession.hiderUnlocked,
    ) { list, hidden, hide, unlocked ->
        if (!hide || unlocked) list
        else list.filter { !HiddenContacts.matches(it.phoneNumber, hidden) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasCallLogPermission: StateFlow<Boolean> = permissionsManager.hasPermission(PermissionGroup.CallLog)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun requestCallLogPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.CallLog)
    }

    val clirPrefix = commsSettings.clirPrefix.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val clirEnabled = commsSettings.clirEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val tapToCall = commsSettings.tapToCall.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    val confirmBeforeCall = commsSettings.confirmBeforeCall.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val showNumbers = commsSettings.showNumbersInRecents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    fun dial(context: Context, phoneNumber: String) {
        if (phoneNumber.isEmpty()) return
        viewModelScope.launch { CallGuard.place(context, phoneNumber) }
    }

    fun delete(call: CallLogEntry) {
        viewModelScope.launch { callLogRepository.deleteById(call.id) }
    }

    fun export(context: Context) {
        val text = recents.value.joinToString("\n") { call ->
            val name = call.displayName ?: call.phoneNumber
            val whenStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(call.timestamp))
            "$whenStr\t${call.type}\t$name\t${call.phoneNumber}"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "Call history")
        }
        context.startActivity(Intent.createChooser(intent, "Export"))
    }
}
