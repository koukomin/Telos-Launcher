package com.telos.desktop.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.i18n.TelosI18n.t
import com.telos.desktop.panel.ConnectivityManager
import com.telos.desktop.ui.TelosColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// === TELOS_PENDING_REVIEW_START: telos_window_drag_and_ui_fix ===
@Composable
fun TelosNotesApp() {
    var text by remember { mutableStateOf("") }
    de.mm20.launcher2.uikmp.SharedNotesScreen(t("notes_title"), t("notes_widget_placeholder"), text) { text = it }
}

@Composable
fun TelosCalendarApp() {
    val month = remember { YearMonth.now() }
    val today = LocalDate.now().dayOfMonth
    de.mm20.launcher2.uikmp.SharedCalendarScreen("${month.month} ${month.year}", month.lengthOfMonth(), today)
}

@Composable
fun TelosCalculatorApp() {
    val engine = remember { de.mm20.launcher2.uikmp.CalculatorEngine() }
    var display by remember { mutableStateOf(engine.display) }
    de.mm20.launcher2.uikmp.SharedCalculatorScreen(t("calculator_title"), display) { key ->
        engine.key(key)
        display = engine.display
    }
}

@Composable
fun TelosClockApp() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(500)
        }
    }
    de.mm20.launcher2.uikmp.SharedClockScreen(
        t("preference_screen_clockwidget"),
        now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
        now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")),
    )
}

@Composable
fun TelosNetworkApp() {
    var net by remember { mutableStateOf(ConnectivityManager.snapshot()) }
    LaunchedEffect(Unit) {
        while (true) {
            net = ConnectivityManager.snapshot()
            delay(2000)
        }
    }
    Column(Modifier.fillMaxSize().background(TelosColors.surface).padding(16.dp)) {
        Text(t("telos_system_network"), color = TelosColors.secondary, fontSize = 20.sp)
        Text("${t("network_widget_wifi")} ${if (net.wifiOn) t("network_widget_connected") else t("network_widget_disconnected")}  ${net.activeSsid ?: "—"}", color = TelosColors.onSurface)
        Text("VPN ${net.vpn ?: "—"}", color = TelosColors.onSurface)
        net.aps.take(12).forEach {
            Text("${it.ssid}  ${it.signal}%", modifier = Modifier.clickable { ConnectivityManager.wifiConnect(it.ssid) }.padding(6.dp), color = TelosColors.onSurface)
        }
    }
}

@Composable
fun TelosStoreApp() {
    var installed by remember { mutableStateOf<List<FlatpakRef>>(emptyList()) }
    var remote by remember { mutableStateOf<List<FlatpakRef>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        installed = withContext(Dispatchers.IO) { FlatpakStore.listInstalled() }
    }
    LaunchedEffect(q) {
        remote = if (q.length < 2) emptyList()
        else withContext(Dispatchers.IO) { FlatpakStore.search(q) }
    }
    val shown = remember(installed, q) {
        installed.filter { q.isBlank() || it.name.contains(q, true) || it.id.contains(q, true) }
    }
    Column(Modifier.fillMaxSize().background(TelosColors.surface).padding(12.dp)) {
        Text(t("hf_store_title"), color = TelosColors.secondary, fontSize = 20.sp)
        if (!FlatpakStore.available()) {
            Text(t("hc_add_an_app"), color = TelosColors.secondary)
        }
        OutlinedTextField(q, { q = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t("hc_filter")) }, singleLine = true)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { FlatpakStore.updateAll() }) { Text(t("hc_check_for_updates")) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            item {
                Text(t("hc_add_installed_apps"), color = TelosColors.secondary, fontSize = 12.sp)
            }
            items(shown.take(80), key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        e.name + "  " + e.id,
                        color = TelosColors.onSurface,
                        modifier = Modifier.weight(1f).clickable {
                            com.telos.desktop.linux.Sys.runBg("flatpak", "run", e.id)
                        },
                    )
                    Text(
                        t("hc_delete"),
                        color = TelosColors.primary,
                        modifier = Modifier.clickable {
                            FlatpakStore.uninstall(e.id)
                            installed = installed.filter { it.id != e.id }
                        }.padding(start = 8.dp),
                    )
                }
            }
            if (remote.isNotEmpty()) {
                item { Text(t("action_install"), color = TelosColors.secondary, fontSize = 12.sp) }
                items(remote.take(40), key = { "r-" + it.id }) { e ->
                    Text(
                        e.name + "  " + e.id,
                        color = TelosColors.onSurface,
                        modifier = Modifier.clickable { FlatpakStore.install(e.id) }.padding(8.dp),
                    )
                }
            }
        }
    }
}
// === TELOS_PENDING_REVIEW_END: telos_window_drag_and_ui_fix ===
