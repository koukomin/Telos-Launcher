package de.mm20.launcher2.ui.clock

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.clock.ClockAlarm
import de.mm20.launcher2.comms.clock.ClockAlarms
import de.mm20.launcher2.comms.clock.WorldClockSearch
import de.mm20.launcher2.comms.clock.WorldClockCity
import de.mm20.launcher2.comms.repository.RadioRepository
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data object ClockRoute : NavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val backStack = LocalBackStack.current
    var selectedTab by de.mm20.launcher2.ui.component.rememberPersistentUiState("clock.tab", 0)
    var night by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (isActive) { now = ZonedDateTime.now(); delay(1000) } }
    if (night) {
        DisposableEffect(Unit) {
            val previous = activity?.window?.attributes?.screenBrightness
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = .1f }
            onDispose {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = previous ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
        Column(Modifier.fillMaxSize().background(Color.Black).clickable { night = false }
            .verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            ClockDigits(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")), Color.Red)
            Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")), color = Color.Gray)
            Text(stringResource(R.string.clock_night_exit), color = Color.Gray)
        }
        return
    }
    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.preference_screen_clockwidget)) }, navigationIcon = {
            IconButton(onClick = { backStack.removeLastOrNull() }) { Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.hc_back)) }
        }, actions = { IconButton(onClick = { night = true }) {
            Icon(painterResource(de.mm20.launcher2.base.R.drawable.light_mode_24px), stringResource(R.string.clock_night))
        } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val titles = listOf(R.string.clock_world, R.string.clock_alarm, R.string.clock_timer, R.string.clock_stopwatch)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                titles.forEachIndexed { index, title -> FilterChip(selectedTab == index, { selectedTab = index }, { Text(stringResource(title)) }) }
            }
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                when (selectedTab) { 0 -> WorldClockTab(); 1 -> AlarmTab(); 2 -> TimerTab(); else -> StopwatchTab() }
            }
        }
    }
}

@Composable
private fun ClockDigits(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    // Important time values remain full-size and can be panned on narrow windows.
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.displayMedium,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = color,
            maxLines = 1, softWrap = false)
    }
}

@Composable
private fun WorldClockTab() {
    var now by remember { mutableStateOf(Instant.now()) }
    var query by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: Locale.getDefault()
    val resources = LocalContext.current.resources
    val packagedLocales = remember(resources) { resources.assets.locales.toList() }
    var cities by remember(locale) { mutableStateOf(emptyList<WorldClockCity>()) }
    LaunchedEffect(locale, packagedLocales) { cities = withContext(Dispatchers.Default) { WorldClockSearch.index(locale, packagedLocales) } }
    val countrySettings: de.mm20.launcher2.preferences.comms.CommsSettings = org.koin.compose.koinInject()
    val homeCountry by countrySettings.homeCountry.collectAsState(initial = "")
    val results = remember(query, cities, searchFocused, locale, homeCountry) {
        if (query.isNotBlank()) WorldClockSearch.search(cities, query)
        else if (searchFocused) {
            val zones = listOf("Europe/Athens", "Europe/London", "Europe/Paris", "Europe/Berlin", "Europe/Rome", "Europe/Madrid", "Asia/Tokyo")
            zones.filterNot { it == "Europe/Athens" && (homeCountry.equals("GR", true) || locale.country == "GR" || locale.language == "el") }
                .mapNotNull { zone -> cities.firstOrNull { it.zoneId == zone } }
        } else emptyList()
    }
    LaunchedEffect(Unit) { while (isActive) { now = Instant.now(); delay(1000) } }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.clock_local), style = MaterialTheme.typography.titleMedium)
                ClockDigits(ZonedDateTime.ofInstant(now, ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss")))
            } }
        }
        item {
            de.mm20.launcher2.ui.component.TelosSearchBar(
                value = query, onValueChange = { query = it },
                placeholder = stringResource(R.string.clock_world),
                onFocusChanged = { searchFocused = it },
            )
        }
        items(results, key = { it.zoneId }) { city ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(city.city, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(listOf(city.country, city.zoneId).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                Text(ZonedDateTime.ofInstant(now, ZoneId.of(city.zoneId)).format(DateTimeFormatter.ofPattern("HH:mm · EEE, d MMM", locale)), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            } }
        }
    }
}

@Composable
private fun AlarmTab() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val operations = remember { Mutex() }
    var alarms by remember { mutableStateOf(emptyList<ClockAlarm>()) }
    var exact by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ClockAlarm?>(null) }
    fun refresh() { scope.launch {
        operations.withLock {
            loading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val allowed = ClockAlarms.canSchedule(appContext)
                    val loaded = if (allowed) ClockAlarms.update(appContext) { it } else ClockAlarms.load(appContext)
                    loaded to allowed
                }
                alarms = result.first; exact = result.second; failed = false
            } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
            finally { loading = false }
        }
    } }
    fun save(transform: (List<ClockAlarm>) -> List<ClockAlarm>) { scope.launch {
        operations.withLock {
            loading = true
            try {
                alarms = withContext(Dispatchers.IO) { ClockAlarms.update(appContext, transform) }
                failed = false
            } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
            finally { loading = false }
        }
    } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) refresh()
        onDispose { lifecycle.removeObserver(observer) }
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (failed) item { TextButton(onClick = { refresh() }, enabled = !loading) { Text(stringResource(R.string.hc_failed_try_again)) } }
        if (!exact && !loading) item { OutlinedButton(onClick = {
            if (android.os.Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.clock_exact)) } }
        items(alarms, key = { it.id }) { alarm ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { editing = alarm }) {
                        Text("%02d:%02d".format(alarm.hour, alarm.minute), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                        Text(alarm.label.ifBlank { context.getString(R.string.clock_alarm) })
                        Text(alarm.soundName.ifBlank { context.getString(R.string.clock_default_sound) }, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(alarm.enabled, { enabled -> save { current -> current.map { if (it.id == alarm.id) it.copy(enabled = enabled) else it } } }, enabled = exact && !loading)
                }
                TextButton(onClick = { save { current -> current.filterNot { it.id == alarm.id } } }, enabled = !loading) { Text(stringResource(R.string.hc_delete)) }
            } }
        }
        item { Button(onClick = { editing = ClockAlarm(enabled = exact) }, Modifier.fillMaxWidth(), enabled = !loading) { Text(stringResource(R.string.hc_add)) } }
    }
    editing?.let { alarm -> AlarmEditor(alarm, onDismiss = { editing = null }, onSave = { updated ->
        save { current -> current.filterNot { it.id == updated.id } + updated }; editing = null
    }) }
}

@Composable
private fun AlarmEditor(initial: ClockAlarm, onDismiss: () -> Unit, onSave: (ClockAlarm) -> Unit) {
    val context = LocalContext.current
    val repository: RadioRepository = koinInject()
    val stations by repository.observeFavorites().collectAsState(emptyList())
    var alarm by remember(initial.id) { mutableStateOf(initial) }
    var hour by remember { mutableStateOf(initial.hour.toString()) }
    var minute by remember { mutableStateOf(initial.minute.toString()) }
    var radio by remember { mutableStateOf(false) }
    var stream by remember { mutableStateOf(initial.soundUri.takeIf { it.startsWith("http") }.orEmpty()) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            alarm = alarm.copy(soundUri = uri.toString(), soundName = context.getString(R.string.clock_audio_file))
        }
    }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION") val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) alarm = alarm.copy(soundUri = uri.toString(), soundName = runCatching { RingtoneManager.getRingtone(context, uri).getTitle(context) }.getOrDefault(context.getString(R.string.clock_default_sound)))
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * .85f).dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.clock_alarm), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("HH") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("mm") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
                OutlinedTextField(alarm.label, { alarm = alarm.copy(label = it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.au3_appearance_typo_label)) })
                Text(alarm.soundName.ifBlank { context.getString(R.string.clock_default_sound) })
                OutlinedButton(onClick = { ringtonePicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.clock_default_sound)) }
                OutlinedButton(onClick = { filePicker.launch(arrayOf("audio/*")) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.clock_audio_file)) }
                OutlinedButton(onClick = { radio = !radio }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.hc_radio)) }
                if (radio) {
                    stations.forEach { station -> TextButton(onClick = { alarm = alarm.copy(soundUri = station.streamUrl, soundName = station.name); radio = false }, Modifier.fillMaxWidth()) { Text(station.name) } }
                    OutlinedTextField(stream, { stream = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.clock_stream_url)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    TextButton(onClick = { alarm = alarm.copy(soundUri = stream.trim(), soundName = context.getString(R.string.hc_radio)); radio = false }, enabled = Uri.parse(stream).scheme in listOf("http", "https")) { Text(stringResource(R.string.hc_save)) }
                }
                Text(stringResource(R.string.clock_sound_fallback), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { onSave(alarm.copy(hour = hour.toInt(), minute = minute.toInt(), enabled = alarm.enabled && ClockAlarms.canSchedule(context))) }, enabled = hour.toIntOrNull() in 0..23 && minute.toIntOrNull() in 0..59, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.hc_save)) }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.hc_cancel)) }
            }
        }
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val totalSec = remainingMs / 1000
        val mins = totalSec / 60
        val secs = totalSec % 60
        val tenths = (remainingMs % 1000) / 100

        ClockDigits("%02d:%02d.%d".format(mins, secs, tenths))

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
                label = { Text(stringResource(R.string.clock_minutes)) },
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
                Text(if (isRunning) stringResource(R.string.au_music_pause) else stringResource(R.string.clock_start))
            }

            Button(
                onClick = {
                    isRunning = false
                    remainingMs = totalSeconds * 1000L
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(stringResource(R.string.clock_reset), color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        ClockDigits("%02d:%02d.%02d".format(minutes, seconds, millis))

        Spacer(Modifier.height(24.dp))

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { isRunning = !isRunning }) {
                Text(if (isRunning) stringResource(R.string.au_music_pause) else stringResource(R.string.clock_start))
            }

            Button(onClick = { if (isRunning) laps.add(0, elapsedMs) }, enabled = isRunning) {
                Text(stringResource(R.string.clock_lap))
            }

            Button(
                onClick = {
                    isRunning = false
                    elapsedMs = 0L
                    laps.clear()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(stringResource(R.string.clock_reset), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
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
                        Text(stringResource(R.string.clock_lap), fontWeight = FontWeight.Bold)
                        Text("%02d:%02d.%02d".format(m, s, ms), fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
