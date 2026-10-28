import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/tools/FlashlightScreen.kt', 'r') as f:
    code = f.read()

# Add imports
imports = """import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import kotlin.math.roundToInt
"""
code = code.replace("import androidx.navigation3.runtime.NavKey\n", "import androidx.navigation3.runtime.NavKey\n" + imports)

# Top Bar settings button
top_bar_old = """                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.desktop_mode_orientation_landscape), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Switch(
                            checked = allowLandscape,
                            onCheckedChange = { allowLandscape = it },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                },"""

top_bar_new = """                actions = {
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
                            Icon(painterResource(R.drawable.settings_24px), contentDescription = "Font Settings", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(stringResource(R.string.desktop_mode_orientation_landscape), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Switch(
                            checked = allowLandscape,
                            onCheckedChange = { allowLandscape = it },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                },"""

code = code.replace(top_bar_old, top_bar_new)

# OxygenOS 16 Flashlight Button + Beam Canvas
toggle_old = """            // Main Toggle Button
            item {
                Spacer(Modifier.height(8.dp))
                IconButton(
                    onClick = {
                        val on = !isTorchOn
                        stopSignals()
                        isTorchOn = on

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
                        .background(if (isTorchOn || isScreenFlashOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.light_mode_24px),
                        contentDescription = stringResource(R.string.telos_flashlight_app_label),
                        modifier = Modifier.size(48.dp),
                        tint = if (isTorchOn || isScreenFlashOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isTorchOn || isScreenFlashOn) stringResource(R.string.net_hub_state_on) else stringResource(R.string.hc_off),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }"""

toggle_new = """            // OxygenOS 16 Interactive Flashlight Beam Control
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
                            painter = painterResource(R.drawable.light_mode_24px),
                            contentDescription = stringResource(R.string.telos_flashlight_app_label),
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
                            text = if (isActive) stringResource(R.string.net_hub_state_on) else stringResource(R.string.hc_off),
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
            }"""

code = code.replace(toggle_old, toggle_new)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/tools/FlashlightScreen.kt', 'w') as f:
    f.write(code)

