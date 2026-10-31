package de.mm20.launcher2.ui.network
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.appmanagement.ShizukuManager
import de.mm20.launcher2.network.api.NetworkSettings
import de.mm20.launcher2.network.privatedns.PrivateDnsError
import de.mm20.launcher2.network.privatedns.PrivateDnsHost
import de.mm20.launcher2.network.api.PrivateDnsMode
import de.mm20.launcher2.network.api.WifiDnsRule
import de.mm20.launcher2.network.api.WifiDnsRules
import de.mm20.launcher2.network.privatedns.WifiDnsAutomationService
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Serializable
data object PrivateDnsRoute : NavKey

@Composable
fun PrivateDnsScreen() {
    val vm: PrivateDnsVM = viewModel()
    val state by vm.state.collectAsState()
    val networkSettings: NetworkSettings = koinInject()
    val config by networkSettings.values.collectAsState()
    val shizuku: ShizukuManager = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedModeName by rememberSaveable { mutableStateOf(PrivateDnsMode.AUTOMATIC.name) }
    var hostname by rememberSaveable { mutableStateOf("") }
    var ruleSsid by rememberSaveable { mutableStateOf("") }
    var ruleHostname by rememberSaveable { mutableStateOf("") }
    var ruleModeName by rememberSaveable { mutableStateOf(PrivateDnsMode.AUTOMATIC.name) }
    var automationPermissionError by remember { mutableStateOf(false) }
    var ruleError by remember { mutableStateOf(false) }
    val selectedMode = rememberSaveable(selectedModeName) {
        PrivateDnsMode.valueOf(selectedModeName)
    }
    val selectedRuleMode = rememberSaveable(ruleModeName) {
        PrivateDnsMode.valueOf(ruleModeName)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results.values.all { it }
        if (granted) startWifiAutomation(
            scope, shizuku, networkSettings, context,
            onError = { automationPermissionError = true },
        ) else automationPermissionError = true
    }

    fun requestWifiAutomation() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            startWifiAutomation(
                scope, shizuku, networkSettings, context,
                onError = { automationPermissionError = true },
            )
        }
    }

    LaunchedEffect(state.current) {
        state.current?.let {
            selectedModeName = it.mode.name
            hostname = it.hostname.orEmpty()
        }
    }
    LaunchedEffect(state.error) {
        if (state.error != null && state.error != PrivateDnsError.INVALID_HOSTNAME) {
            selectedModeName = state.current?.mode?.name ?: PrivateDnsMode.AUTOMATIC.name
        }
    }

    PreferenceScreen(
        title = { Text(stringResource(I18nR.string.net_dns_system)) },
        topBarActions = {
            androidx.compose.material3.IconButton(
                enabled = !state.loading,
                onClick = vm::refresh,
            ) {
                androidx.compose.material3.Icon(
                    painterResource(BaseR.drawable.restart_alt_24px),
                    contentDescription = stringResource(I18nR.string.hc_refresh),
                )
            }
        },
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(I18nR.string.net_dns_system), style = MaterialTheme.typography.titleMedium)
                state.current?.let { current ->
                    Text(
                        "${stringResource(I18nR.string.net_dns_detail_provider)}: ${current.hostname ?: modeLabel(current.mode)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PrivateDnsMode.AUTOMATIC, PrivateDnsMode.OFF, PrivateDnsMode.PROVIDER).forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = {
                                selectedModeName = mode.name
                                if (mode != PrivateDnsMode.PROVIDER) vm.setMode(mode)
                            },
                            enabled = !state.loading,
                            label = { Text(modeLabel(mode)) },
                        )
                    }
                }
                if (selectedMode == PrivateDnsMode.PROVIDER) {
                    OutlinedTextField(
                        value = hostname,
                        onValueChange = { hostname = it.take(253) },
                        label = { Text(stringResource(I18nR.string.net_dns_field_dot)) },
                        singleLine = true,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        enabled = !state.loading && hostname.isNotBlank(),
                        onClick = { vm.setProvider(hostname) },
                    ) {
                        Text(stringResource(I18nR.string.net_dns_save))
                    }
                }
                if (state.error == PrivateDnsError.PERMISSION_REQUIRED ||
                    state.error == PrivateDnsError.SHIZUKU_UNAVAILABLE
                ) {
                    Text(
                        stringResource(I18nR.string.net_hub_state_permission),
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(enabled = !state.loading, onClick = vm::requestPermission) {
                        Text(stringResource(I18nR.string.freeze_permission_not_granted))
                    }
                } else if (state.permissionDenied) {
                    Text(
                        stringResource(I18nR.string.freeze_permission_not_granted),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    state.error?.let {
                        Text(
                            stringResource(I18nR.string.net_dns_test_fail, it.name),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (state.loading) {
                    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(I18nR.string.net_wifi_dns_automation), style = MaterialTheme.typography.titleLarge)
                SwitchPreference(
                    title = stringResource(I18nR.string.net_wifi_dns_automation),
                    summary = stringResource(I18nR.string.net_wifi_dns_automation_summary),
                    value = config.wifiDnsAutomationEnabled,
                    enabled = !state.loading &&
                        (config.wifiDnsAutomationEnabled || config.wifiDnsRules.isNotEmpty()),
                    onValueChanged = { enabled ->
                        automationPermissionError = false
                        if (enabled) {
                            requestWifiAutomation()
                        } else {
                            networkSettings.update { it.copy(wifiDnsAutomationEnabled = false) }
                            scope.launch {
                                withContext(Dispatchers.IO) { networkSettings.flush() }
                                WifiDnsAutomationService.stop(context)
                            }
                        }
                    },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedRuleMode == PrivateDnsMode.AUTOMATIC,
                        onClick = { ruleModeName = PrivateDnsMode.AUTOMATIC.name },
                        label = { Text(modeLabel(PrivateDnsMode.AUTOMATIC)) },
                    )
                    FilterChip(
                        selected = selectedRuleMode == PrivateDnsMode.OFF,
                        onClick = { ruleModeName = PrivateDnsMode.OFF.name },
                        label = { Text(modeLabel(PrivateDnsMode.OFF)) },
                    )
                    FilterChip(
                        selected = selectedRuleMode == PrivateDnsMode.PROVIDER,
                        onClick = { ruleModeName = PrivateDnsMode.PROVIDER.name },
                        label = { Text(modeLabel(PrivateDnsMode.PROVIDER)) },
                    )
                }
                OutlinedTextField(
                    value = ruleSsid,
                    onValueChange = { ruleSsid = it.take(32); ruleError = false },
                    label = { Text(stringResource(I18nR.string.context_profile_trigger_wifi_ssid)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (selectedRuleMode == PrivateDnsMode.PROVIDER) {
                    OutlinedTextField(
                        value = ruleHostname,
                        onValueChange = { ruleHostname = it.take(253); ruleError = false },
                        label = { Text(stringResource(I18nR.string.net_dns_field_dot)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    enabled = !state.loading && ruleSsid.isNotBlank() &&
                        (selectedRuleMode != PrivateDnsMode.PROVIDER ||
                            PrivateDnsHost.normalize(ruleHostname) != null),
                    onClick = {
                        val ssid = WifiDnsRules.normalizeSsid(ruleSsid)
                        val normalizedHost = if (selectedRuleMode == PrivateDnsMode.PROVIDER) {
                            PrivateDnsHost.normalize(ruleHostname)
                        } else ""
                        if (ssid == null || normalizedHost == null) {
                            ruleError = true
                        } else {
                            val rule = WifiDnsRule(ssid, selectedRuleMode, normalizedHost)
                            networkSettings.update { current ->
                                current.copy(
                                    wifiDnsRules = (current.wifiDnsRules.filterNot {
                                        WifiDnsRules.normalizeSsid(it.ssid) == ssid
                                    } + rule).takeLast(MAX_WIFI_RULES),
                                )
                            }
                            scope.launch {
                                withContext(Dispatchers.IO) { networkSettings.flush() }
                            }
                            ruleSsid = ""
                            ruleHostname = ""
                            ruleError = false
                        }
                    },
                ) {
                    Text(stringResource(I18nR.string.hc_add))
                }
                if (ruleError) {
                    Text(
                        stringResource(I18nR.string.net_wifi_dns_invalid_rule),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (automationPermissionError) {
                    Text(
                        stringResource(I18nR.string.net_wifi_dns_permission_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                config.wifiDnsRules.forEach { rule ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(rule.ssid, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (rule.mode == PrivateDnsMode.PROVIDER) {
                                "${modeLabel(rule.mode)}: ${rule.hostname}"
                            } else modeLabel(rule.mode),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            onClick = {
                                networkSettings.update {
                                    it.copy(wifiDnsRules = it.wifiDnsRules.filterNot { saved ->
                                        WifiDnsRules.normalizeSsid(saved.ssid) == rule.ssid
                                    })
                                }
                                scope.launch {
                                    withContext(Dispatchers.IO) { networkSettings.flush() }
                                }
                            },
                        ) {
                            Text(stringResource(I18nR.string.hc_remove))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun modeLabel(mode: PrivateDnsMode): String = stringResource(
    when (mode) {
        PrivateDnsMode.AUTOMATIC -> I18nR.string.desktop_mode_orientation_auto
        PrivateDnsMode.OFF -> I18nR.string.hc_off
        PrivateDnsMode.PROVIDER -> I18nR.string.net_dns_detail_provider
    },
)

private const val MAX_WIFI_RULES = 50

private fun startWifiAutomation(
    scope: kotlinx.coroutines.CoroutineScope,
    shizuku: ShizukuManager,
    settings: NetworkSettings,
    context: android.content.Context,
    onError: () -> Unit,
) {
    scope.launch {
        if (!shizuku.requestPermission()) {
            onError()
            return@launch
        }
        settings.update { it.copy(wifiDnsAutomationEnabled = true) }
        withContext(Dispatchers.IO) { settings.flush() }
        try {
            WifiDnsAutomationService.start(context)
        } catch (_: Exception) {
            settings.update { it.copy(wifiDnsAutomationEnabled = false) }
            withContext(Dispatchers.IO) { settings.flush() }
            onError()
        }
    }
}
