package de.mm20.launcher2.ui.comms

import androidx.compose.ui.res.pluralStringResource
import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
data object DuplicateContactsRoute : NavKey

class DuplicateContactsViewModel : ViewModel(), KoinComponent {
    private val repo: ContactDirectoryRepository by inject()
    suspend fun load(): List<List<DialerContact>> = repo.findDuplicateGroups()
    suspend fun keepFirst(group: List<DialerContact>) {
        group.drop(1).forEach { repo.deleteContact(it.id) }
    }
}

@Composable
fun DuplicateContactsScreen() {
    val viewModel: DuplicateContactsViewModel = viewModel()
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<List<DialerContact>>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    // what is waiting for the user's confirmation: the groups whose extra contacts would be deleted
    var pending by remember { mutableStateOf<List<List<DialerContact>>?>(null) }
    LaunchedEffect(Unit) {
        groups = viewModel.load()
        loaded = true
    }
    pending?.let { toClean ->
        val count = toClean.sumOf { it.size - 1 }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(pluralStringResource(R.plurals.hc_delete_contacts_question, count, count)) },
            text = { Text(stringResource(R.string.hc_delete_duplicates_message, count)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        toClean.forEach { viewModel.keepFirst(it) }
                        groups = viewModel.load()
                    }
                }) { Text(stringResource(R.string.hc_delete)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.hc_cancel)) } },
        )
    }
    PreferenceScreen(title = { Text(stringResource(R.string.hc_duplicate_contacts)) }) {
        if (loaded && groups.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.hc_no_duplicate_numbers_found),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        groups.forEachIndexed { index, group ->
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.hc_group_number_phone, index + 1, group.first().phoneNumbers.firstOrNull().orEmpty()))
                    group.forEach { contact ->
                        Text("${contact.displayName} · ${contact.phoneNumbers.joinToString()}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            backStack.add(ContactDetailsRoute(contactId = group.first().id))
                        }) { Text(stringResource(R.string.hc_open)) }
                        Button(onClick = {
                            pending = listOf(group)
                        }) { Text(stringResource(R.string.hc_keep_first_delete_extras)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        if (groups.isNotEmpty()) {
            item {
                TextButton(
                    onClick = {
                        pending = groups
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.hc_keep_first_in_every_group_delete_extras)) }
            }
        }
    }
}
