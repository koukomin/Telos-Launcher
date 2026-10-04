package de.mm20.launcher2.ui.comms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.intent.MessengerIntentUtils
import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ktx.tryStartActivity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
data class ContactDetailsRoute(val contactId: Long) : NavKey

class ContactDetailsViewModel : ViewModel(), KoinComponent {
    private val contactDir: ContactDirectoryRepository by inject()
    
    fun getContact(id: Long): StateFlow<DialerContact?> {
        return contactDir.observeContacts()
            .map { list -> list.find { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    fun dial(context: android.content.Context, number: String) {
        if (number.isEmpty()) return
        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${android.net.Uri.encode(number)}"))
        context.startActivity(intent)
    }
}

@Composable
fun ContactDetailsScreen(contactId: Long) {
    val viewModel: ContactDetailsViewModel = viewModel()
    val contact by viewModel.getContact(contactId).collectAsStateWithLifecycle()
    val context = LocalContext.current

    PreferenceScreen(title = { Text(stringResource(R.string.contact_details_title)) }) {
        val c = contact
        if (c != null) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = c.displayName.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = c.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            items(c.phoneNumbers) { number ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = number,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.dial(context, number) }) {
                                Icon(painterResource(R.drawable.call_24px), contentDescription = stringResource(R.string.action_call), tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { context.tryStartActivity(MessengerIntentUtils.sms(number)) }) {
                                Icon(painterResource(R.drawable.sms_24px), contentDescription = stringResource(R.string.action_sms), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ActionChip(stringResource(R.string.action_whatsapp)) { context.tryStartActivity(MessengerIntentUtils.whatsApp(number)) }
                            ActionChip(stringResource(R.string.action_telegram)) { context.tryStartActivity(MessengerIntentUtils.telegram(number)) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ActionChip(stringResource(R.string.action_signal)) { context.tryStartActivity(MessengerIntentUtils.signal(number)) }
                            ActionChip(stringResource(R.string.action_viber)) { context.tryStartActivity(MessengerIntentUtils.viber(number)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ActionChip(label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.weight(1f).clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
