package de.mm20.launcher2.ui.clock

import android.app.Activity
import android.content.Context
import android.media.RingtoneManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Serializable
data object ClockRoute : NavKey

enum class AlarmSoundType(val label: String) {
    Ringtone("Default Ringtone"),
    Mp3("Custom MP3 File"),
    Radio("Online Radio Station"),
    Tv("Live TV Channel"),
    YouTube("YouTube / YT Music")
}

data class TelosAlarm(
    val id: String = System.currentTimeMillis().toString(),
    var hour: Int = 7,
    var minute: Int = 0,
    var enabled: Boolean = true,
    var label: String = "Alarm",
    var soundType: AlarmSoundType = AlarmSoundType.Ringtone,
    var soundUri: String = "",
    var days: Set<Int> = setOf(1, 2, 3, 4, 5) // Mon-Fri
)

private val WORLD_CITIES = listOf(
    "Αθήνα (Athens)" to "Europe/Athens",
    "Λονδίνο (London)" to "Europe/London",
    "Παρίσι (Paris)" to "Europe/Paris",
    "Νέα Υόρκη (New York)" to "America/New_York",
    "Λος Άντζελες (Los Angeles)" to "America/Los_Angeles",
    "Τόκιο (Tokyo)" to "Asia/Tokyo",
    "Πεκίνο (Beijing)" to "Asia/Shanghai",
    "Ντουμπάι (Dubai)" to "Asia/Dubai",
    "Σίδνεϊ (Sydney)" to "Australia/Sydney"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var selectedTab by remember { mutableIntStateOf(0) } // 0: World, 1: Alarm, 2: Timer, 3: Stopwatch, 4: Night
    var isNightModeOpen by remember { mutableStateOf(false) }

    val activity = context as? Activity

    if (isNightModeOpen) {
        // NightDream / Bedside Desk Clock Mode
        DisposableEffect(Unit) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = 0.1f }
            onDispose {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }

        var currentTimeText by remember { mutableStateOf("") }
        var currentDateText by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
            val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")
            while (isActive) {
                val now = ZonedDateTime.now()
                currentTimeText = now.format(timeFmt)
                currentDateText = now.format(dateFmt)
                delay(1000L)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { isNightModeOpen = false },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTimeText,
                    fontSize = 72.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = currentDateText,
                    fontSize = 18.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Tap anywhere to exit Night Clock",
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ρολόι & Ξυπνητήρι", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isNightModeOpen = true }) {
                        Icon(painterResource(de.mm20.launcher2.base.R.drawable.light_mode_24px), contentDescription = "Night Clock")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Navigation Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Κόσμος", fontSize = 12.sp) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Ξυπνητήρι", fontSize = 12.sp) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Αντίστροφη", fontSize = 12.sp) })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Χρονόμετρο", fontSize = 12.sp) })
            }

            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                when (selectedTab) {
                    0 -> WorldClockTab()
                    1 -> AlarmTab()
                    2 -> TimerTab()
                    3 -> StopwatchTab()
                }
            }
        }
    }
}

@Composable
private fun WorldClockTab() {
    var currentTime by remember { mutableStateOf(Instant.now()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            currentTime = Instant.now()
            delay(1000L)
        }
    }

    val localTime = ZonedDateTime.ofInstant(currentTime, ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Main Local Time Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Τοπική Ώρα", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = localTime.format(formatter),
                        fontSize = 48.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // World Cities List
        items(WORLD_CITIES) { (cityName, zoneIdStr) ->
            val zoneTime = ZonedDateTime.ofInstant(currentTime, ZoneId.of(zoneIdStr))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cityName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(zoneIdStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = zoneTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmTab() {
    val alarms = remember { mutableStateListOf(TelosAlarm(hour = 7, minute = 30), TelosAlarm(hour = 8, minute = 0, enabled = false)) }
    var editingAlarm by remember { mutableStateOf<TelosAlarm?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(alarms) { alarm ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).clickable { editingAlarm = alarm }) {
                        Text(
                            text = "%02d:%02d".format(alarm.hour, alarm.minute),
                            fontSize = 36.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${alarm.label} • ${alarm.soundType.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = alarm.enabled,
                        onCheckedChange = { alarm.enabled = it }
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    val newAlarm = TelosAlarm(hour = 8, minute = 0)
                    alarms.add(newAlarm)
                    editingAlarm = newAlarm
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+ Προσθήκη Αφύπνισης")
            }
        }
    }

    // Alarm Sound & Time Configuration Dialog
    editingAlarm?.let { alarm ->
        AlertDialog(
            onDismissRequest = { editingAlarm = null },
            title = { Text("Ρύθμιση Αφύπνισης") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = alarm.label,
                        onValueChange = { alarm.label = it },
                        label = { Text("Ετικέτα") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Πηγή Ήχου / Media:", fontWeight = FontWeight.Bold)

                    AlarmSoundType.entries.forEach { soundType ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { alarm.soundType = soundType }
                        ) {
                            RadioButton(
                                selected = alarm.soundType == soundType,
                                onClick = { alarm.soundType = soundType }
                            )
                            Text(soundType.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Text(
                        text = "Σημείωση: Αν το stream (Radio/TV/YT) αποτύχει λόγω δικτύου, θα αναπαραχθεί αυτόματα ο προεπιλεγμένος ήχος ξυπνητηριού.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(onClick = { editingAlarm = null }) {
                    Text("Αποθήκευση")
                }
            }
        )
    }
}

@Composable
private fun TimerTab() {
    var totalSeconds by remember { mutableIntStateOf(60) }
    var remainingSeconds by remember { mutableIntStateOf(60) }
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, remainingSeconds) {
        if (isRunning && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
        } else if (isRunning && remainingSeconds == 0) {
            isRunning = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "%02d:%02d".format(remainingSeconds / 60, remainingSeconds % 60),
            fontSize = 64.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    if (isRunning) {
                        isRunning = false
                    } else {
                        if (remainingSeconds == 0) remainingSeconds = totalSeconds
                        isRunning = true
                    }
                }
            ) {
                Text(if (isRunning) "Παύση" else "Έναρξη")
            }

            Button(
                onClick = {
                    isRunning = false
                    remainingSeconds = totalSeconds
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("Επαναφορά", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StopwatchTab() {
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }
    val laps = remember { mutableStateListOf<Long>() }

    LaunchedEffect(isRunning) {
        val startTime = SystemClock.elapsedRealtime() - elapsedMs
        while (isRunning) {
            elapsedMs = SystemClock.elapsedRealtime() - startTime
            delay(10L)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        val minutes = (elapsedMs / 1000) / 60
        val seconds = (elapsedMs / 1000) % 60
        val millis = (elapsedMs % 1000) / 10

        Text(
            text = "%02d:%02d.%02d".format(minutes, seconds, millis),
            fontSize = 56.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { isRunning = !isRunning }
            ) {
                Text(if (isRunning) "Παύση" else "Έναρξη")
            }

            Button(
                onClick = { if (isRunning) laps.add(0, elapsedMs) },
                enabled = isRunning
            ) {
                Text("Γύρος")
            }

            Button(
                onClick = {
                    isRunning = false
                    elapsedMs = 0L
                    laps.clear()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("Επαναφορά", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(laps) { lapMs ->
                val m = (lapMs / 1000) / 60
                val s = (lapMs / 1000) % 60
                val ms = (lapMs % 1000) / 10
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Γύρος", fontWeight = FontWeight.Bold)
                        Text("%02d:%02d.%02d".format(m, s, ms), fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
