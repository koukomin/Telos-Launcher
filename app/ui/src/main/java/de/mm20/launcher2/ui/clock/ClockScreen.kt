package de.mm20.launcher2.ui.clock

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var soundUri: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity

    var selectedTab by remember { mutableIntStateOf(0) }
    var isNightModeOpen by remember { mutableStateOf(false) }

    if (isNightModeOpen) {
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
                    fontSize = 64.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Text(text = currentDateText, fontSize = 18.sp, color = Color.Gray)
                Spacer(Modifier.height(24.dp))
                Text("Tap anywhere to exit Night Clock", fontSize = 12.sp, color = Color.DarkGray)
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
    var searchQuery by remember { mutableStateOf("") }

    val allZoneIds = remember { ZoneId.getAvailableZoneIds().sorted() }

    val filteredZoneIds = remember(searchQuery, allZoneIds) {
        if (searchQuery.isBlank()) {
            listOf("Europe/Athens", "Europe/London", "America/New_York", "Asia/Tokyo")
        } else {
            allZoneIds.filter { it.contains(searchQuery, ignoreCase = true) }.take(30)
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            currentTime = Instant.now()
            delay(1000L)
        }
    }

    val localTime = ZonedDateTime.ofInstant(currentTime, ZoneId.systemDefault())

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Main Single-Line Responsive Clock
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Τοπική Ώρα", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = localTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        fontSize = 44.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Search Bar for World Cities / Timezones
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Αναζήτηση Πόλης / Ζώνης Ώρας") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        items(filteredZoneIds) { zoneIdStr ->
            val zoneTime = ZonedDateTime.ofInstant(currentTime, ZoneId.of(zoneIdStr))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(zoneIdStr.substringAfterLast('/'), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alarms = remember { mutableStateListOf(TelosAlarm(hour = 7, minute = 30), TelosAlarm(hour = 8, minute = 0, enabled = false)) }
    var editingAlarm by remember { mutableStateOf<TelosAlarm?>(null) }

    fun triggerAlarmSound(alarm: TelosAlarm) {
        scope.launch(Dispatchers.IO) {
            try {
                if (alarm.soundUri.isNotBlank() && alarm.soundType != AlarmSoundType.Ringtone) {
                    val mp = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setDataSource(context, Uri.parse(alarm.soundUri))
                        prepare()
                        start()
                    }
                } else {
                    playDefaultRingtone(context)
                }
            } catch (_: Exception) {
                // FALLBACK to default system alarm ringtone sound!
                playDefaultRingtone(context)
            }
        }
    }

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
                    Switch(checked = alarm.enabled, onCheckedChange = { alarm.enabled = it })
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
                            RadioButton(selected = alarm.soundType == soundType, onClick = { alarm.soundType = soundType })
                            Text(soundType.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Text(
                        text = "Αν το stream (Radio/TV/YT) αποτύχει λόγω δικτύου, θα αναπαραχθεί αυτόματα ο προεπιλεγμένος ήχος ξυπνητηριού της συσκευής.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(onClick = { editingAlarm = null }) { Text("Αποθήκευση") }
            }
        )
    }
}

private fun playDefaultRingtone(context: Context) {
    runCatching {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val ringtone = RingtoneManager.getRingtone(context, uri)
        ringtone.play()
    }
}

@Composable
private fun TimerTab() {
    var minutesInput by remember { mutableStateOf("5") }
    var totalSeconds by remember { mutableIntStateOf(300) }
    var remainingMs by remember { mutableLongStateOf(300000L) }
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, remainingMs) {
        if (isRunning && remainingMs > 0L) {
            delay(100L)
            remainingMs -= 100L
        } else if (isRunning && remainingMs <= 0L) {
            isRunning = false
            remainingMs = 0L
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val totalSec = remainingMs / 1000
        val mins = totalSec / 60
        val secs = totalSec % 60
        val tenths = (remainingMs % 1000) / 100

        Text(
            text = "%02d:%02d.%d".format(mins, secs, tenths),
            fontSize = 56.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(24.dp))

        if (!isRunning) {
            OutlinedTextField(
                value = minutesInput,
                onValueChange = {
                    minutesInput = it
                    val m = it.toIntOrNull() ?: 0
                    totalSeconds = m * 60
                    remainingMs = totalSeconds * 1000L
                },
                label = { Text("Λεπτά (Custom Preset)") },
                singleLine = true,
                modifier = Modifier.width(200.dp)
            )
            Spacer(Modifier.height(16.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    if (isRunning) {
                        isRunning = false
                    } else {
                        if (remainingMs <= 0L) remainingMs = totalSeconds * 1000L
                        isRunning = true
                    }
                }
            ) {
                Text(if (isRunning) "Παύση" else "Έναρξη")
            }

            Button(
                onClick = {
                    isRunning = false
                    remainingMs = totalSeconds * 1000L
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
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { isRunning = !isRunning }) {
                Text(if (isRunning) "Παύση" else "Έναρξη")
            }

            Button(onClick = { if (isRunning) laps.add(0, elapsedMs) }, enabled = isRunning) {
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
