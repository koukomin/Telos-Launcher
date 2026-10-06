package de.mm20.launcher2.ui.comms

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.comms.PhoneNumbers
import de.mm20.launcher2.comms.intent.MessengerIntentUtils
import de.mm20.launcher2.comms.model.CallLogEntry
import de.mm20.launcher2.comms.search.ContactSearch
import de.mm20.launcher2.comms.search.GreekText
import de.mm20.launcher2.comms.model.CallType
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class RecentsFilter {
    All, Today, Missed, Incoming, Outgoing, Rejected, TalkTime
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsScreen(searchQuery: String = "") {
    val viewModel: RecentsViewModel = viewModel()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val hasCallLogPermission by viewModel.hasCallLogPermission.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    var filter by remember { mutableStateOf(RecentsFilter.All) }
    var cabCall by remember { mutableStateOf<CallLogEntry?>(null) }
    var pendingCall by remember { mutableStateOf<String?>(null) }
    val tapToCall by viewModel.tapToCall.collectAsStateWithLifecycle()
    val confirmBeforeCall by viewModel.confirmBeforeCall.collectAsStateWithLifecycle()
    val showNumbers by viewModel.showNumbers.collectAsStateWithLifecycle()

    if (!hasCallLogPermission) {
        Box(Modifier.fillMaxSize()) {
            EmptyCommsTab(
                title = stringResource(R.string.permission_call_log_req),
                message = stringResource(R.string.permission_call_log_msg),
            )
            Button(
                onClick = {
                    (context as? AppCompatActivity)?.let { viewModel.requestCallLogPermission(it) }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp),
            ) {
                Text(stringResource(R.string.permission_grant))
            }
        }
        return
    }

    val filtered = remember(recents, filter, searchQuery) {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val byType = when (filter) {
            RecentsFilter.All -> recents
            RecentsFilter.Today -> recents.filter { it.timestamp >= startOfDay }
            RecentsFilter.Missed -> recents.filter { it.type == CallType.Missed }
            RecentsFilter.Incoming -> recents.filter { it.type == CallType.Incoming }
            RecentsFilter.Outgoing -> recents.filter { it.type == CallType.Outgoing }
            RecentsFilter.Rejected -> recents.filter { it.type == CallType.Rejected }
            RecentsFilter.TalkTime -> recents.filter { it.durationSeconds > 0 }
        }
        if (searchQuery.isBlank()) byType
        else byType.filter { call ->
            val dummy = de.mm20.launcher2.comms.model.DialerContact(
                id = call.id,
                displayName = call.displayName ?: call.phoneNumber,
                phoneNumbers = listOf(call.phoneNumber),
            )
            ContactSearch.search(searchQuery, listOf(dummy)).isNotEmpty() ||
                GreekText.fold(call.displayName.orEmpty()).contains(GreekText.fold(searchQuery))
        }
    }
    val collapsed = remember(filtered) { collapseRecents(filtered) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = { viewModel.export(context) }) {
                Icon(painterResource(R.drawable.share_24px), contentDescription = "Export")
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = filter == RecentsFilter.All,
                    onClick = { filter = RecentsFilter.All },
                    label = { Text(stringResource(R.string.filter_all)) },
                )
            }
            item {
                FilterChip(
                    selected = filter == RecentsFilter.Today,
                    onClick = { filter = RecentsFilter.Today },
                    label = { Text("Today") },
                )
            }
            item {
                FilterChip(
                    selected = filter == RecentsFilter.Missed,
                    onClick = { filter = RecentsFilter.Missed },
                    label = { Text(stringResource(R.string.filter_missed)) },
                )
            }
            item {
                FilterChip(
                    selected = filter == RecentsFilter.Incoming,
                    onClick = { filter = RecentsFilter.Incoming },
                    label = { Text(stringResource(R.string.filter_incoming)) },
                )
            }
            item {
                FilterChip(
                    selected = filter == RecentsFilter.Outgoing,
                    onClick = { filter = RecentsFilter.Outgoing },
                    label = { Text(stringResource(R.string.filter_outgoing)) },
                )
            }
            item {
                FilterChip(
                    selected = filter == RecentsFilter.Rejected,
                    onClick = { filter = RecentsFilter.Rejected },
                    label = { Text(stringResource(R.string.filter_rejected)) },
                )
            }
            item {
                val talk = recents.sumOf { it.durationSeconds }
                FilterChip(
                    selected = filter == RecentsFilter.TalkTime,
                    onClick = { filter = RecentsFilter.TalkTime },
                    label = { Text("Talk ${formatCallDuration(talk)}") },
                )
            }
        }

        if (filtered.isEmpty()) {
            val isDefaultDialer = remember(context) {
                val pkg = context.packageName
                val tm = context.getSystemService(android.telecom.TelecomManager::class.java)
                tm?.defaultDialerPackage == pkg
            }
            EmptyCommsTab(
                title = stringResource(R.string.recents_empty_title),
                message = if (isDefaultDialer) {
                    stringResource(R.string.recents_empty_msg)
                } else {
                    "Call history stays empty until Telos is the default Phone app, or until call-log permission is granted."
                },
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(collapsed, key = { it.call.id }) { group ->
                    RecentCallRow(
                        call = group.call,
                        count = group.count,
                        showNumber = showNumbers,
                        onCall = {
                            if (!tapToCall) {
                                backStack.add(ContactDetailsRoute(phoneNumber = group.call.phoneNumber))
                            } else if (confirmBeforeCall) {
                                pendingCall = group.call.phoneNumber
                            } else {
                                viewModel.dial(context, group.call.phoneNumber)
                            }
                        },
                        onSms = { context.tryStartActivity(MessengerIntentUtils.sms(group.call.phoneNumber)) },
                        onDelete = { viewModel.delete(group.call) },
                        onDetails = {
                            backStack.add(ContactDetailsRoute(phoneNumber = group.call.phoneNumber))
                        },
                        onLongPress = { cabCall = group.call },
                    )
                }
            }
        }
        pendingCall?.let { number ->
            AlertDialog(
                onDismissRequest = { pendingCall = null },
                title = { Text("Place call") },
                text = { Text(number) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.dial(context, number)
                        pendingCall = null
                    }) { Text(stringResource(R.string.search_action_call)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCall = null }) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
        cabCall?.let { selected ->
            CommsCabSheet(
                title = selected.displayName ?: selected.phoneNumber,
                actions = listOf(
                    CommsCabAction(R.drawable.rd_ic_phone_green_vector, stringResource(R.string.search_action_call)) {
                        viewModel.dial(context, selected.phoneNumber)
                    },
                    CommsCabAction(R.drawable.rd_ic_messages, stringResource(R.string.search_action_message)) {
                        context.tryStartActivity(MessengerIntentUtils.sms(selected.phoneNumber))
                    },
                    CommsCabAction(R.drawable.info_24px, stringResource(R.string.contact_details_title)) {
                        backStack.add(ContactDetailsRoute(phoneNumber = selected.phoneNumber))
                    },
                    CommsCabAction(R.drawable.delete_24px, stringResource(R.string.comms_clear_history_confirm), destructive = true) {
                        viewModel.delete(selected)
                    },
                ),
                onDismiss = { cabCall = null },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun RecentCallRow(
    call: CallLogEntry,
    count: Int = 1,
    showNumber: Boolean = true,
    onCall: () -> Unit,
    onSms: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
    onLongPress: () -> Unit = {},
) {
    val missed = call.type == CallType.Missed || call.type == CallType.Rejected
    val nameColor = if (missed) RdRedCall else MaterialTheme.colorScheme.onSurface
    val typeIcon = when (call.type) {
        CallType.Outgoing -> R.drawable.rd_ic_call_made_vector
        CallType.Missed, CallType.Rejected -> R.drawable.rd_ic_call_missed_vector
        else -> R.drawable.rd_ic_call_received_vector
    }
    val typeTint = if (missed) RdRedCall else RdGreenCall

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onSms()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                else -> false
            }
        },
        positionalThreshold = { it * 0.4f },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> RdSwipePurple.copy(alpha = 0.35f)
                    SwipeToDismissBoxValue.EndToStart -> RdRedCall.copy(alpha = 0.35f)
                    else -> Color.Transparent
                },
                label = "recents-swipe",
            )
            val alignment = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> R.drawable.rd_ic_messages
                else -> R.drawable.delete_24px
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment,
            ) {
                if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                    Icon(painterResource(icon), contentDescription = null)
                }
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .combinedClickable(onClick = onCall, onLongClick = onLongPress)
                .heightIn(min = 64.dp)
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommsAvatar(
                name = call.displayName ?: call.phoneNumber,
                photoUri = call.photoUri,
                size = 48.dp,
                modifier = Modifier.clickable(onClick = onDetails),
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                ) {
                    Text(
                        text = buildString {
                            append(call.displayName?.ifBlank { null } ?: call.phoneNumber)
                            if (count > 1) append(" ($count)")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = nameColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(typeIcon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                        val secondary = listOfNotNull(
                            call.simLabel?.let { "SIM $it" },
                            call.durationSeconds.takeIf { it > 0 }?.let { formatCallDuration(it) },
                            call.phoneNumber.takeIf { showNumber && call.displayName != null },
                        ).joinToString("  ")
                        if (secondary.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = secondary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                )
            }
            Text(
                text = formatRowDate(call.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
            IconButton(onClick = onDetails, modifier = Modifier.padding(end = 8.dp).size(42.dp)) {
                Icon(
                    painterResource(R.drawable.info_24px),
                    contentDescription = stringResource(R.string.contact_details_title),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private data class GroupedRecent(val call: CallLogEntry, val count: Int)

private fun collapseRecents(recents: List<CallLogEntry>): List<GroupedRecent> {
    val out = mutableListOf<GroupedRecent>()
    for (call in recents) {
        val last = out.lastOrNull()
        if (last != null && PhoneNumbers.match(last.call.phoneNumber, call.phoneNumber)) {
            out[out.lastIndex] = last.copy(count = last.count + 1)
        } else {
            out += GroupedRecent(call, 1)
        }
    }
    return out
}

private fun dayKey(timestamp: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@Composable
private fun formatDayHeader(day: Long): String {
    val today = dayKey(System.currentTimeMillis())
    val yesterday = today - 24L * 60 * 60 * 1000
    return when (day) {
        today -> stringResource(R.string.comms_today)
        yesterday -> stringResource(R.string.comms_yesterday)
        else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(day))
    }
}

private fun formatRowDate(timestamp: Long): String {
    val pattern = if (dayKey(timestamp) == dayKey(System.currentTimeMillis())) "HH:mm" else "dd.MM"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}
