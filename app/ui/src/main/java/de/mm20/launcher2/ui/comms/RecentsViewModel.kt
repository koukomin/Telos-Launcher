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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RecentsViewModel : ViewModel(), KoinComponent {

    private val callLogRepository: CallLogRepository by inject()
    private val commsSettings: de.mm20.launcher2.preferences.comms.CommsSettings by inject()
    private val permissionsManager: PermissionsManager by inject()

    val recents: StateFlow<List<CallLogEntry>> = callLogRepository.observeRecents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasCallLogPermission: StateFlow<Boolean> = permissionsManager.hasPermission(PermissionGroup.CallLog)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun requestCallLogPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.CallLog)
    }

    val clirPrefix = commsSettings.clirPrefix.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

    fun dial(context: Context, phoneNumber: String) {
        if (phoneNumber.isEmpty()) return
        val prefix = clirPrefix.value
        val finalNumber = if (prefix.isNotEmpty() && !android.telephony.PhoneNumberUtils.isEmergencyNumber(phoneNumber)) {
            "$prefix$phoneNumber"
        } else phoneNumber
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(finalNumber)}"))
        context.startActivity(intent)
    }
}
