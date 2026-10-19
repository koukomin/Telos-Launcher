package de.mm20.launcher2.ui.tools

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
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

private val MORSE_MAP = mapOf(
    'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
    'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
    'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
    'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
    'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
    'Z' to "--..", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
    '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
    '0' to "-----", ' ' to " "
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
    var targetMode by remember { mutableStateOf(if (cameraId == null) FlashlightTarget.Screen else FlashlightTarget.Flash) }
    var allowLandscape by remember { mutableStateOf(false) }

    var screenBrightness by remember { mutableFloatStateOf(1.0f) }
    var selectedScreenColor by remember { mutableStateOf(Color.White) }
    var isScreenFlashOn by remember { mutableStateOf(false) }

    var torchStrength by remember { mutableFloatStateOf(1f) }
    var maxStrength by remember { mutableIntStateOf(1) }

    var outputMode by remember { mutableStateOf(FlashlightOutputMode.LightOnly) }
    var strobeFreqHz by remember { mutableFloatStateOf(0f) }
    var morseText by remember { mutableStateOf("SOS") }
    var isMorsePlaying by remember { mutableStateOf(false) }

    var isSirenPlaying by remember { mutableStateOf(false) }
    var sirenAlternatingColors by remember { mutableStateOf(true) }

    var morseJob by remember { mutableStateOf<Job?>(null) }
    var strobeJob by remember { mutableStateOf<Job?>(null) }
    var sirenJob by remember { mutableStateOf<Job?>(null) }

    val toneGenerator = remember {
        try { ToneGenerator(AudioManager.STREAM_MUSIC, 100) } catch (_: Exception) { null }
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
    if (isScreenFlashOn) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(selectedScreenColor)
                .clickable {
                    isScreenFlashOn = false
                    isTorchOn = false
                    applyScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                },
            contentAlignment = Alignment.Center
        ) {
            Text("Tap to exit Screen Flashlight", color = if (selectedScreenColor == Color.White) Color.Black else Color.White, style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Φακός & Σειρήνα / Μορς", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Landscape", color = Color.Gray, fontSize = 12.sp)
                        Switch(
                            checked = allowLandscape,
                            onCheckedChange = { allowLandscape = it },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Toggle Button
            item {
                Spacer(Modifier.height(8.dp))
                IconButton(
                    onClick = {
                        isTorchOn = !isTorchOn
                        strobeJob?.cancel()
                        strobeFreqHz = 0f
                        morseJob?.cancel()
                        sirenJob?.cancel()
                        isMorsePlaying = false
                        isSirenPlaying = false

                        if (targetMode == FlashlightTarget.Flash || targetMode == FlashlightTarget.Both) {
                            setHardwareTorch(isTorchOn, (torchStrength * maxStrength).toInt())
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
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(if (isTorchOn || isScreenFlashOn) MaterialTheme.colorScheme.primary else Color.DarkGray)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.light_mode_24px),
                        contentDescription = "Flashlight",
                        modifier = Modifier.size(48.dp),
                        tint = if (isTorchOn || isScreenFlashOn) MaterialTheme.colorScheme.onPrimary else Color.White
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isTorchOn || isScreenFlashOn) "ON" else "OFF",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // Light Target Selector with Auto-Switching
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Light Source", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FlashlightTarget.entries.forEach { target ->
                                FilterChip(
                                    selected = targetMode == target,
                                    onClick = { switchLightTarget(target) },
                                    label = { Text(target.label, fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f)
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Screen Light Color & Brightness", style = MaterialTheme.typography.titleMedium, color = Color.White)
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
                                        Icon(painterResource(R.drawable.check_24px), contentDescription = null, tint = if (color == Color.White) Color.Black else Color.White)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Text("Screen Brightness", style = MaterialTheme.typography.labelMedium, color = Color.White)
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Emergency Siren & Police Flasher", style = MaterialTheme.typography.titleMedium, color = Color.Red, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = sirenAlternatingColors, onCheckedChange = { sirenAlternatingColors = it })
                            Text("Alternating Red/Blue Police Strobe", color = Color.White, style = MaterialTheme.typography.bodySmall)
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
                                    isSirenPlaying = true
                                    strobeJob?.cancel()
                                    morseJob?.cancel()
                                    sirenJob = scope.launch(Dispatchers.Default) {
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
                            colors = ButtonDefaults.buttonColors(containerColor = if (isSirenPlaying) Color.DarkGray else Color.Red),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isSirenPlaying) "STOP SIREN" else "START POLICE SIREN & ALARM", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Strobe Light Slider
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Strobe Light (Blink Frequency)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = strobeFreqHz,
                                onValueChange = { hz ->
                                    strobeFreqHz = hz
                                    strobeJob?.cancel()
                                    if (hz > 0f) {
                                        isTorchOn = false
                                        strobeJob = scope.launch(Dispatchers.Default) {
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
                            Text("${strobeFreqHz.toInt()} Hz", color = Color.White)
                        }
                    }
                }
            }

            // Morse Code Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Text-to-Morse & SOS Signals", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = morseText,
                            onValueChange = { morseText = it },
                            label = { Text("Message to transmit") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        val morseCode = remember(morseText) {
                            morseText.uppercase().map { MORSE_MAP[it] ?: "" }.joinToString(" ")
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Code: $morseCode",
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    if (isMorsePlaying) {
                                        morseJob?.cancel()
                                        isMorsePlaying = false
                                        setHardwareTorch(false)
                                    } else {
                                        isMorsePlaying = true
                                        strobeJob?.cancel()
                                        strobeFreqHz = 0f
                                        morseJob = scope.launch(Dispatchers.Default) {
                                            playMorseSequence(
                                                code = morseCode,
                                                mode = outputMode,
                                                target = targetMode,
                                                toneGenerator = toneGenerator,
                                                setTorch = { setHardwareTorch(it) }
                                            )
                                            isMorsePlaying = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isMorsePlaying) "Stop" else "Transmit")
                            }

                            Button(
                                onClick = {
                                    morseText = "SOS"
                                    isMorsePlaying = true
                                    strobeJob?.cancel()
                                    strobeFreqHz = 0f
                                    morseJob = scope.launch(Dispatchers.Default) {
                                        while (isActive) {
                                            playMorseSequence(
                                                code = "... --- ...",
                                                mode = outputMode,
                                                target = targetMode,
                                                toneGenerator = toneGenerator,
                                                setTorch = { setHardwareTorch(it) }
                                            )
                                            delay(1000L)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("SOS Signal")
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
    while (coroutineScopeActive()) {
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

private fun coroutineScopeActive(): Boolean = CoroutineScope(Dispatchers.Default).isActive

private suspend fun playMorseSequence(
    code: String,
    mode: FlashlightOutputMode,
    target: FlashlightTarget,
    toneGenerator: ToneGenerator?,
    setTorch: (Boolean) -> Unit
) {
    val unitMs = 100L
    for (char in code) {
        when (char) {
            '.' -> {
                emitSignal(true, mode, target, toneGenerator, setTorch)
                delay(unitMs)
                emitSignal(false, mode, target, toneGenerator, setTorch)
                delay(unitMs)
            }
            '-' -> {
                emitSignal(true, mode, target, toneGenerator, setTorch)
                delay(unitMs * 3)
                emitSignal(false, mode, target, toneGenerator, setTorch)
                delay(unitMs)
            }
            ' ' -> {
                delay(unitMs * 3)
            }
        }
    }
}

private fun emitSignal(
    on: Boolean,
    mode: FlashlightOutputMode,
    target: FlashlightTarget,
    toneGenerator: ToneGenerator?,
    setTorch: (Boolean) -> Unit
) {
    if (on) {
        if (mode == FlashlightOutputMode.LightOnly || mode == FlashlightOutputMode.LightAndSound) {
            if (target == FlashlightTarget.Flash || target == FlashlightTarget.Both) {
                setTorch(true)
            }
        }
        if (mode == FlashlightOutputMode.SoundOnly || mode == FlashlightOutputMode.LightAndSound) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
            } catch (_: Exception) {}
        }
    } else {
        setTorch(false)
    }
}
