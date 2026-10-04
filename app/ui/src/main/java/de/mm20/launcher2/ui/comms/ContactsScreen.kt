package de.mm20.launcher2.ui.comms

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.ExperimentalFoundationApi
import de.mm20.launcher2.ui.comms.ContactDetailsRoute
import de.mm20.launcher2.ui.locals.LocalBackStack
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.comms.intent.MessengerIntentUtils
import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.ui.R
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen() {
    val viewModel: ContactsViewModel = viewModel()
    val permissionsManager: PermissionsManager = koinInject()
    val context = LocalContext.current

    val hasPermission by permissionsManager.hasPermission(PermissionGroup.Contacts).collectAsStateWithLifecycle(false)
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()

    if (!hasPermission) {
        ContactsPermissionRequest(onRequest = {
            (context as? AppCompatActivity)?.let { permissionsManager.requestPermission(it, PermissionGroup.Contacts) }
        })
        return
    }

    if (contacts.isEmpty()) {
        EmptyCommsTab(title = androidx.compose.ui.res.stringResource(R.string.contacts_empty_title), message = androidx.compose.ui.res.stringResource(R.string.contacts_empty_msg))
        return
    }

    val backStack = LocalBackStack.current
    val grouped = remember(contacts) {
        contacts.groupBy { it.displayName.firstOrNull()?.uppercaseChar() ?: '#' }.toSortedMap()
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        grouped.forEach { (initial, contactsForInitial) ->
            stickyHeader {
                Text(
                    text = initial.toString(),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(contactsForInitial, key = { it.id }) { contact ->
                ContactRow(contact = contact, onClick = { backStack.add(ContactDetailsRoute(contact.id)) })
            }
        }
    }
}

@Composable
private fun ContactsPermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Telos needs access to your contacts to show them here.")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRequest) { Text("Grant access") }
    }
}

@Composable
private fun ContactRow(contact: DialerContact, onClick: () -> Unit) {
    val context = LocalContext.current
    val primaryNumber = contact.phoneNumbers.firstOrNull()

    ListItem(
        headlineContent = { Text(contact.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = primaryNumber?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = primaryNumber?.let {
            {
                Row {
                    SocialActionButton(R.drawable.call_24px, "Call") {
                        safeStartActivity(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(it)}")))
                    }
                    SocialActionButton(R.drawable.sms_24px, "WhatsApp") {
                        safeStartActivity(context, MessengerIntentUtils.whatsApp(it))
                    }
                }
            }
        },
        modifier = primaryNumber?.let { number ->
            Modifier.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
        } ?: Modifier,
    )
}

@Composable
private fun SocialActionButton(icon: Int, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(icon), contentDescription = contentDescription)
    }
}

private fun safeStartActivity(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // The target app (dialer, WhatsApp, ...) isn't installed - nothing to recover into.
    }
}
