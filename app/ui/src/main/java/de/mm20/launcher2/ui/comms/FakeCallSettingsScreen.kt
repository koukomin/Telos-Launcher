package de.mm20.launcher2.ui.comms

import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.fakecall.FakeCallScheduler
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable

@Serializable
data object FakeCallSettingsRoute : NavKey

@Composable
fun FakeCallSettingsScreen() {
    val context = LocalContext.current
    val boss = stringResource(R.string.au_phoneb_preset_boss)
    val presets = listOf(
        boss to "+30 210 555 0100",
        stringResource(R.string.au_phoneb_preset_mom) to "+30 697 555 0101",
        stringResource(R.string.au_phoneb_preset_doctor) to "+30 210 555 0199",
    )
    // kept over rotation
    var name by rememberSaveable { mutableStateOf(boss) }
    var number by rememberSaveable { mutableStateOf("+30") }
    var delaySec by rememberSaveable { mutableStateOf("10") }
    val incomingLabel = stringResource(R.string.comms_incoming_call)
    PreferenceScreen(title = { Text(stringResource(R.string.hc_fake_call)) }) {
        item {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.hc_caller_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (n, num) ->
                        FilterChip(
                            selected = name == n,
                            onClick = { name = n; number = num },
                            label = { Text(n) },
                        )
                    }
                }
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text(stringResource(R.string.hc_number)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = delaySec,
                    onValueChange = { delaySec = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.hc_delay_seconds)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("5", "10", "30", "60").forEach { d ->
                        FilterChip(
                            selected = delaySec == d,
                            onClick = { delaySec = d },
                            label = { Text("${d}s") },
                        )
                    }
                }
                Button(
                    onClick = {
                        val delay = (delaySec.toIntOrNull() ?: 10).coerceIn(1, 3600)
                        FakeCallScheduler.schedule(context, name.ifBlank { incomingLabel }, number, delay)
                        Toast.makeText(context, context.getString(R.string.hc_fake_call_in_seconds, delay), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = number.isNotBlank(),
                ) {
                    Text(stringResource(R.string.hc_schedule_fake_call))
                }
                TextButton(onClick = { FakeCallScheduler.cancel(context) }) {
                    Text(stringResource(R.string.hc_cancel_scheduled))
                }
            }
        }
    }
}
