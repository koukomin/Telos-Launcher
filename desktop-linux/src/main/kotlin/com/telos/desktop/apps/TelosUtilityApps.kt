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
    Column(Modifier.fillMaxSize().background(TelosColors.surface).padding(12.dp)) {
        Text(t("notes_title"), color = TelosColors.secondary, fontSize = 20.sp)
        OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxSize().padding(top = 8.dp), label = { Text(t("notes_widget_placeholder")) })
    }
}

@Composable
fun TelosCalendarApp() {
    val month = remember { YearMonth.now() }
    val today = LocalDate.now().dayOfMonth
    val days = month.lengthOfMonth()
    Column(Modifier.fillMaxSize().background(TelosColors.surface).padding(12.dp)) {
        Text("${month.month} ${month.year}", color = TelosColors.secondary, fontSize = 20.sp)
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.padding(top = 8.dp)) {
            items((1..days).toList()) { d ->
                Text(
                    "$d",
                    color = if (d == today) TelosColors.primary else TelosColors.onSurface,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
fun TelosCalculatorApp() {
    var expr by remember { mutableStateOf("0") }
    var acc by remember { mutableStateOf(0.0) }
    var op by remember { mutableStateOf<Char?>(null) }
    fun digit(d: String) {
        expr = if (expr == "0") d else expr + d
    }
    fun apply(next: Char?) {
        val v = expr.toDoubleOrNull() ?: 0.0
        acc = when (op) {
            '+' -> acc + v
            '-' -> acc - v
            '*' -> acc * v
            '/' -> if (v == 0.0) acc else acc / v
            else -> v
        }
        expr = if (next == '=') acc.toString() else "0"
        op = if (next == '=') null else next
        if (next == '=') acc = expr.toDoubleOrNull() ?: 0.0
    }
    Column(Modifier.fillMaxSize().background(TelosColors.surface).padding(12.dp)) {
        Text(t("calculator_title"), color = TelosColors.secondary, fontSize = 20.sp)
        Text(expr, color = TelosColors.onSurface, fontSize = 28.sp, modifier = Modifier.padding(vertical = 8.dp))
        listOf(
            listOf("7", "8", "9", "/"),
            listOf("4", "5", "6", "*"),
            listOf("1", "2", "3", "-"),
            listOf("0", "C", "=", "+"),
        ).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Button(onClick = {
                        when (key) {
                            "C" -> { expr = "0"; acc = 0.0; op = null }
                            in listOf("+", "-", "*", "/") -> apply(key[0])
                            "=" -> apply('=')
                            else -> digit(key)
                        }
                    }, modifier = Modifier.weight(1f)) { Text(key) }
                }
            }
        }
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
    Column(Modifier.fillMaxSize().background(TelosColors.surface).padding(16.dp)) {
        Text(t("preference_screen_clockwidget"), color = TelosColors.secondary, fontSize = 20.sp)
        Text(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")), color = TelosColors.onSurface, fontSize = 42.sp)
        Text(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")), color = TelosColors.primary)
    }
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
