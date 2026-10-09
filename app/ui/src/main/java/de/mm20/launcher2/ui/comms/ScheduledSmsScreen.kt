package de.mm20.launcher2.ui.comms

import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.sms.ScheduledSmsStore
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Serializable
data object ScheduledSmsRoute : NavKey

@Composable
fun ScheduledSmsScreen() {
    val context = LocalContext.current
    var items by remember { mutableStateOf(ScheduledSmsStore.list(context)) }
    var number by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }
    var exact by remember { mutableStateOf(ScheduledSmsStore.canScheduleExact(context)) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        exact = ScheduledSmsStore.canScheduleExact(context)
    }
    var smsGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { smsGranted = it }
    val canSave = number.isNotBlank() && body.isNotBlank()
    PreferenceScreen(title = { Text(stringResource(R.string.hc_scheduled_sms)) }) {
        if (!smsGranted) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.au_messages_scheduled_permission)) },
                    trailingContent = {
                        TextButton(onClick = { smsLauncher.launch(Manifest.permission.SEND_SMS) }) { Text(stringResource(R.string.hc_allow)) }
                    },
                )
            }
        }
        if (!exact) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.hc_exact_time)) },
                    supportingContent = { Text(stringResource(R.string.hf_sms_exact_alarm_hint)) },
                    trailingContent = {
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        android.net.Uri.parse("package:${context.packageName}"),
                                    )
                                )
                            }
                        }) { Text(stringResource(R.string.hc_allow)) }
                    },
                )
            }
        }
        item {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text(stringResource(R.string.hc_number)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.hc_message)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!canSave) Text(
                stringResource(R.string.au_messages_scheduled_fill_fields),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(enabled = canSave, onClick = {
                val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 5) }
                DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        cal.set(Calendar.YEAR, y)
                        cal.set(Calendar.MONTH, m)
                        cal.set(Calendar.DAY_OF_MONTH, d)
                        TimePickerDialog(
                            context,
                            { _, h, min ->
                                cal.set(Calendar.HOUR_OF_DAY, h)
                                cal.set(Calendar.MINUTE, min)
                                cal.set(Calendar.SECOND, 0)
                                cal.set(Calendar.MILLISECOND, 0)
                                if (cal.timeInMillis <= System.currentTimeMillis()) {
                                    Toast.makeText(context, R.string.au_messages_scheduled_past, Toast.LENGTH_SHORT).show()
                                } else if (canSave) {
                                    ScheduledSmsStore.add(context, number.trim(), body, cal.timeInMillis)
                                    items = ScheduledSmsStore.list(context)
                                    number = ""
                                    body = ""
                                    Toast.makeText(context, R.string.au_messages_scheduled_saved, Toast.LENGTH_SHORT).show()
                                }
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true,
                        ).show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH),
                ).show()
            }) { Text(stringResource(R.string.hc_pick_time_and_save)) }
            }
        }
        if (items.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.au_messages_scheduled_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.au_messages_scheduled_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items.forEach { sms ->
            item {
                ListItem(
                    headlineContent = { Text(sms.number) },
                    supportingContent = { Text("${fmt.format(Date(sms.atEpochMs))} · ${sms.body}") },
                    trailingContent = {
                        TextButton(onClick = {
                            ScheduledSmsStore.remove(context, sms.id)
                            items = ScheduledSmsStore.list(context)
                        }) { Text(stringResource(R.string.hc_cancel)) }
                    },
                )
            }
        }
    }
}
