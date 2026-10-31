package de.mm20.launcher2.ui.tools
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable

@Serializable
data object FlashlightRoute : NavKey

enum class FlashlightTarget(val label: String) {
    Flash("Camera Flash"),
    Screen("Screen Light"),
    Both("Flash + Screen")
}

enum class FlashlightOutputMode(val label: String) {
    LightOnly("Light Only"),
    SoundOnly("Sound Only"),
    LightAndSound("Light + Sound")
}

val SCREEN_COLORS = listOf(
    "White" to Color.White,
    "Red" to Color(0xFFFF0000),
    "Blue" to Color(0xFF0000FF),
    "Amber" to Color(0xFFFFBF00),
    "Green" to Color(0xFF00FF00)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashlightScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val cameraId = remember {
        try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    var isTorchOn by remember { mutableStateOf(false) }
    var targetMode by rememberSaveable { mutableStateOf(if (cameraId == null) FlashlightTarget.Screen else FlashlightTarget.Flash) }
    var allowLandscape by rememberSaveable { mutableStateOf(false) }

    var screenBrightness by remember { mutableFloatStateOf(1.0f) }
    var selectedScreenColor by remember { mutableStateOf(Color.White) }
    var isScreenFlashOn by remember { mutableStateOf(false) }

    var torchStrength by remember { mutableFloatStateOf(1f) }
    var maxStrength by remember { mutableIntStateOf(1) }

    var outputMode by rememberSaveable { mutableStateOf(FlashlightOutputMode.LightAndSound) }
    var strobeFreqHz by remember { mutableFloatStateOf(0f) }
    var morseText by rememberSaveable { mutableStateOf("SOS") }
    var isMorsePlaying by remember { mutableStateOf(false) }

    var isSirenPlaying by remember { mutableStateOf(false) }
    var sirenAlternatingColors by remember { mutableStateOf(true) }

    var morseJob by remember { mutableStateOf<Job?>(null) }
    var strobeJob by remember { mutableStateOf<Job?>(null) }
    var sirenJob by remember { mutableStateOf<Job?>(null) }

    val toneGenerator = remember {
        try { ToneGenerator(AudioManager.STREAM_MUSIC, 100) } catch (_: Exception) { null }
    }

    LaunchedEffect(toneGenerator) { if (toneGenerator == null) outputMode = FlashlightOutputMode.LightOnly }

    DisposableEffect(isMorsePlaying) {
        val active = isMorsePlaying
        val alreadyKept = activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (active) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (active && !alreadyKept) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    DisposableEffect(activity) {
        val previousStream = activity?.volumeControlStream ?: AudioManager.USE_DEFAULT_STREAM_TYPE
        activity?.volumeControlStream = AudioManager.STREAM_MUSIC
        onDispose { activity?.volumeControlStream = previousStream }
    }

    // Lock Orientation to Portrait by Default
    DisposableEffect(allowLandscape) {
        val orig = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = if (allowLandscape) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity?.requestedOrientation = orig }
    }

    // Hardware Torch Strength (Dimming) API 33+
    LaunchedEffect(cameraId) {
        if (cameraId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val chars = cameraManager.getCameraCharacteristics(cameraId)
                maxStrength = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
            } catch (_: Exception) {}
        }
    }

    fun setHardwareTorch(on: Boolean, strengthLevel: Int = 1) {
        if (cameraId == null) return
        try {
            if (on) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxStrength > 1) {
                    cameraManager.turnOnTorchWithStrengthLevel(cameraId, strengthLevel.coerceIn(1, maxStrength))
                } else {
                    cameraManager.setTorchMode(cameraId, true)
                }
            } else {
                cameraManager.setTorchMode(cameraId, false)
            }
        } catch (_: Exception) {
            if (targetMode == FlashlightTarget.Flash) isTorchOn = false
        }
    }

    val originalBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
    fun applyScreenBrightness(brightness: Float) {
        activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = if (brightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) originalBrightness else brightness.coerceIn(0.01f, 1.0f)
        }
    }

    var signalGeneration by remember { mutableIntStateOf(0) }
    fun stopSignals() {
        signalGeneration++
        morseJob?.cancel(); strobeJob?.cancel(); sirenJob?.cancel()
        toneGenerator?.stopTone()
        setHardwareTorch(false)
        isTorchOn = false; isScreenFlashOn = false
        isMorsePlaying = false; isSirenPlaying = false; strobeFreqHz = 0f
        applyScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    fun startMorse(code: String, repeat: Boolean) {
        stopSignals()
        if (code.isBlank()) return
        val generation = signalGeneration
        val mode = outputMode
        val target = targetMode
        val screen = mode != FlashlightOutputMode.SoundOnly && target != FlashlightTarget.Flash
        isMorsePlaying = true
        val previous = morseJob
        morseJob = scope.launch {
            previous?.join()
            isMorsePlaying = true
            try {
                if (screen) applyScreenBrightness(screenBrightness)
                val pulses = withContext(Dispatchers.Default) { MorseCodec.pulses(code) }
                do {
                    for (pulse in pulses) {
                        ensureActive()
                        if (mode != FlashlightOutputMode.SoundOnly) {
                            if (target != FlashlightTarget.Screen) setHardwareTorch(pulse.on)
                            if (screen) isScreenFlashOn = pulse.on
                        }
                        if (mode != FlashlightOutputMode.LightOnly) {
                            if (pulse.on) toneGenerator?.startTone(ToneGenerator.TONE_DTMF_0)
                            else toneGenerator?.stopTone()
                        }
                        delay(pulse.units * 100L)
                    }
                    toneGenerator?.stopTone(); setHardwareTorch(false); isScreenFlashOn = false
                    if (repeat) delay(700L)
                } while (repeat && isActive)
            } finally {
                if (generation == signalGeneration) {
                    toneGenerator?.stopTone(); setHardwareTorch(false)
                    isScreenFlashOn = false; isMorsePlaying = false
                    applyScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                }
            }
        }
    }

    // Auto-switch Light Target Sources
    fun switchLightTarget(newTarget: FlashlightTarget) {
        targetMode = newTarget
        if (isTorchOn) {
            // Turn off camera flash if not needed
            if (newTarget != FlashlightTarget.Flash && newTarget != FlashlightTarget.Both) {
                setHardwareTorch(false)
            } else {
                setHardwareTorch(true, (torchStrength * maxStrength).toInt())
            }

            // Turn off screen light if not needed
            if (newTarget != FlashlightTarget.Screen && newTarget != FlashlightTarget.Both) {
                applyScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                isScreenFlashOn = false
            } else {
                applyScreenBrightness(screenBrightness)
                isScreenFlashOn = true
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        fun stopOutputs() {
            signalGeneration++
            strobeJob?.cancel()
            morseJob?.cancel()
            sirenJob?.cancel()
            toneGenerator?.stopTone()
            setHardwareTorch(false)
            applyScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            isTorchOn = false
            isScreenFlashOn = false
            isMorsePlaying = false
            isSirenPlaying = false
            strobeFreqHz = 0f
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stopOutputs()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopOutputs()
            strobeJob?.cancel()
            morseJob?.cancel()
            sirenJob?.cancel()
            toneGenerator?.release()
            setHardwareTorch(false)
            applyScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        }
    }

    // Fullscreen Screen Light Overlay
    if (isScreenFlashOn || (isMorsePlaying && outputMode != FlashlightOutputMode.SoundOnly && targetMode != FlashlightTarget.Flash)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isScreenFlashOn) selectedScreenColor else Color.Black)
                .clickable { stopSignals() },
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(I18nR.string.app_polish_exit_light), color = if (isScreenFlashOn && selectedScreenColor == Color.White) Color.Black else Color.White, style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(I18nR.string.telos_flashlight_app_label), color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(painterResource(BaseR.drawable.arrow_back_24px), contentDescription = stringResource(I18nR.string.hc_back), tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var showFontDialog by remember { mutableStateOf(false) }
                        if (showFontDialog) {
                            de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
                                appKey = "flashlight",
                                appName = "Telos Flashlight",
                                onDismiss = { showFontDialog = false }
                            )
                        }
                        IconButton(onClick = { showFontDialog = true }) {
                            Icon(painterResource(BaseR.drawable.settings_24px), contentDescription = "Font Settings", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(stringResource(I18nR.string.desktop_mode_orientation_landscape), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Switch(
                            checked = allowLandscape,
                            onCheckedChange = { allowLandscape = it },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier.testTag("flashlight-controls")
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // OxygenOS 16 Interactive Flashlight Beam Control
            item {
                val primaryColor = MaterialTheme.colorScheme.primary
                val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest
                val isActive = isTorchOn || isScreenFlashOn

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    // OxygenOS 16 Visual Light Beam Cone Canvas
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .height(130.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            if (isActive) {
                                val beamAlpha = torchStrength.coerceIn(0.2f, 1.0f)
                                val beamWidth = size.width * (0.35f + (torchStrength * 0.6f))
                                val path = Path().apply {
                                    moveTo(size.width / 2f - 24f, size.height)
                                    lineTo(size.width / 2f - beamWidth / 2f, 0f)
                                    lineTo(size.width / 2f + beamWidth / 2f, 0f)
                                    lineTo(size.width / 2f + 24f, size.height)
                                    close()
                                }
                                drawPath(
                                    path = path,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            primaryColor.copy(alpha = beamAlpha * 0.85f),
                                            primaryColor.copy(alpha = 0.05f)
                                        )
                                    )
                                )
                            }
                        }
                    }

                    // OxygenOS 16 Drag-to-Adjust Flashlight Button
                    IconButton(
                        onClick = {
                            val on = !isTorchOn
                            stopSignals()
                            isTorchOn = on

                            if (targetMode == FlashlightTarget.Flash || targetMode == FlashlightTarget.Both) {
                                setHardwareTorch(isTorchOn, (torchStrength * maxStrength).roundToInt().coerceAtLeast(1))
                            }
                            if (targetMode == FlashlightTarget.Screen || targetMode == FlashlightTarget.Both) {
                                if (isTorchOn) {
                                    applyScreenBrightness(screenBrightness)
                                    isScreenFlashOn = true
                                } else {
                                    applyScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                                    isScreenFlashOn = false
                                }
                            }
                        },
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(if (isActive) primaryColor else surfaceColor)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    change.consume()
                                    // Dragging UP increases strength, DOWN decreases
                                    val delta = -dragAmount / 400f
                                    torchStrength = (torchStrength + delta).coerceIn(0.1f, 1.0f)
                                    if (isTorchOn) {
                                        setHardwareTorch(true, (torchStrength * maxStrength).roundToInt().coerceAtLeast(1))
                                    }
                                }
                            }
                    ) {
                        Icon(
                            painter = painterResource(BaseR.drawable.light_mode_24px),
                            contentDescription = stringResource(I18nR.string.telos_flashlight_app_label),
                            modifier = Modifier.size(52.dp),
                            tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isActive) stringResource(I18nR.string.net_hub_state_on) else stringResource(I18nR.string.hc_off),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${(torchStrength * 100).roundToInt()}% Beam (Slide ↕)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Light Target Selector with Auto-Switching
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(I18nR.string.app_polish_light_source), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FlashlightTarget.entries.forEach { target ->
                                FilterChip(
                                    selected = targetMode == target,
                                    onClick = { stopSignals(); switchLightTarget(target) },
                                    enabled = cameraId != null || target == FlashlightTarget.Screen,
                                    label = { Text(stringResource(when (target) { FlashlightTarget.Flash -> I18nR.string.camera_native_flash; FlashlightTarget.Screen -> I18nR.string.app_polish_screen_light; FlashlightTarget.Both -> I18nR.string.preference_wallpaper_set_both }), fontSize = 12.sp) },
                                )
                            }
                        }
                    }
                }
            }

            // Screen Color & Dimming Settings
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(I18nR.string.app_polish_screen_light), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(12.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(SCREEN_COLORS) { (name, color) ->
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { selectedScreenColor = color },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selectedScreenColor == color) {
                                        Icon(painterResource(BaseR.drawable.check_24px), contentDescription = null, tint = if (color == Color.White) Color.Black else Color.White)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(stringResource(I18nR.string.hc_brightness), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        Slider(
                            value = screenBrightness,
                            onValueChange = {
                                screenBrightness = it
                                applyScreenBrightness(it)
                            }
                        )
                    }
                }
            }

            // Police Siren & Emergency Alarm Button
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(I18nR.string.app_polish_siren), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = sirenAlternatingColors, onCheckedChange = { sirenAlternatingColors = it })
                            Text(stringResource(I18nR.string.app_polish_alternating), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (isSirenPlaying) {
                                    sirenJob?.cancel()
                                    isSirenPlaying = false
                                    setHardwareTorch(false)
                                    applyScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                                } else {
                                    stopSignals()
                                    isSirenPlaying = true
                                    sirenJob = scope.launch {
                                        playSiren(
                                            target = targetMode,
                                            alternating = sirenAlternatingColors,
                                            toneGenerator = toneGenerator,
                                            setTorch = { setHardwareTorch(it) }
                                        )
                                        isSirenPlaying = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isSirenPlaying) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isSirenPlaying) stringResource(I18nR.string.hc_stop) else stringResource(I18nR.string.app_polish_siren), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Strobe Light Slider
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(I18nR.string.app_polish_strobe), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = strobeFreqHz,
                                onValueChange = { hz ->
                                    stopSignals()
                                    strobeFreqHz = hz
                                    if (hz > 0f) {
                                        isTorchOn = false
                                        strobeJob = scope.launch {
                                            val intervalMs = (1000f / hz / 2f).toLong().coerceAtLeast(20L)
                                            var state = false
                                            while (isActive) {
                                                state = !state
                                                if (targetMode == FlashlightTarget.Flash || targetMode == FlashlightTarget.Both) {
                                                    setHardwareTorch(state)
                                                }
                                                delay(intervalMs)
                                            }
                                        }
                                    } else {
                                        setHardwareTorch(false)
                                    }
                                },
                                valueRange = 0f..15f,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${strobeFreqHz.toInt()} Hz", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // Morse Code Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(I18nR.string.app_polish_morse), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = morseText,
                            onValueChange = { morseText = it.take(2000) },
                            label = { Text(stringResource(I18nR.string.app_polish_message)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        val conversion by produceState<Pair<String, MorseCodec.Encoded>?>(null, morseText) {
                            val input = morseText
                            value = input to withContext(Dispatchers.IO) { MorseCodec.encode(input) }
                        }
                        val encoded = conversion?.takeIf { it.first == morseText }?.second
                        val morseCode = encoded?.code.orEmpty()
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FlashlightOutputMode.entries.forEach { mode ->
                                FilterChip(selected = outputMode == mode,
                                    onClick = { stopSignals(); outputMode = mode },
                                    enabled = toneGenerator != null || mode == FlashlightOutputMode.LightOnly,
                                    label = { Text(stringResource(when (mode) {
                                        FlashlightOutputMode.LightOnly -> I18nR.string.morse_light
                                        FlashlightOutputMode.SoundOnly -> I18nR.string.morse_sound
                                        FlashlightOutputMode.LightAndSound -> I18nR.string.morse_light_sound
                                    })) })
                            }
                        }
                        if (toneGenerator == null) Text(stringResource(I18nR.string.virtual_telemetry_unavailable), color = MaterialTheme.colorScheme.error)
                        Text(stringResource(I18nR.string.morse_language_help), style = MaterialTheme.typography.bodySmall)
                        if (encoded?.romanized == true) Text(encoded.readable, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
                        if (encoded?.unsupported == true) Text(stringResource(I18nR.string.morse_unsupported), color = MaterialTheme.colorScheme.error)

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = morseCode,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)
                        )

                        Spacer(Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    if (isMorsePlaying) stopSignals() else startMorse(morseCode, false)
                                },
                                enabled = isMorsePlaying || morseCode.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isMorsePlaying) stringResource(I18nR.string.hc_stop) else stringResource(I18nR.string.hc_send))
                            }

                            Button(
                                onClick = {
                                    morseText = "SOS"
                                    startMorse("... --- ...", true)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(I18nR.string.app_polish_sos))
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun playSiren(
    target: FlashlightTarget,
    alternating: Boolean,
    toneGenerator: ToneGenerator?,
    setTorch: (Boolean) -> Unit
) {
    var flashOn = false
    while (currentCoroutineContext().isActive) {
        flashOn = !flashOn
        if (target == FlashlightTarget.Flash || target == FlashlightTarget.Both) {
            setTorch(flashOn)
        }
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 250)
        } catch (_: Exception) {}
        delay(200L)
    }
}
