package de.mm20.launcher2.ui.tools
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import kotlinx.serialization.Serializable

@Serializable
data object MagnifierRoute : NavKey

enum class MagnifierFilter(val label: String, val matrix: FloatArray?) {
    Normal("Normal", null),
    Inverted("Inverted", floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )),
    Monochrome("Monochrome", floatArrayOf(
        0.33f, 0.33f, 0.33f, 0f, 0f,
        0.33f, 0.33f, 0.33f, 0f, 0f,
        0.33f, 0.33f, 0.33f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    YellowOnBlack("Yellow/Black", floatArrayOf(
        0.2126f, 0.7152f, 0.0722f, 0f, 0f,
        0.2126f, 0.7152f, 0.0722f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    BlueOnYellow("Blue/Yellow", floatArrayOf(
        0.2126f, 0.7152f, 0.0722f, 0f, 0f,
        0.2126f, 0.7152f, 0.0722f, 0f, 0f,
        -0.2126f, -0.7152f, -0.0722f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    ))
}

@Composable
fun MagnifierScreen() {
    val context = LocalContext.current
    var filterMenu by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            Toast.makeText(context, context.getString(I18nR.string.virtual_magnifier_permission), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasPermission) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(I18nR.string.virtual_magnifier_permission), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(I18nR.string.hc_allow))
                    }
                }
            }
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraActive by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var zoomRange by remember { mutableStateOf(1f..1f) }
    var sensorRect by remember { mutableStateOf<Rect?>(null) }
    var hasFlash by remember { mutableStateOf(false) }
    var zoomLevel by remember { mutableStateOf(1f) }
    var torchOn by remember { mutableStateOf(false) }
    var currentFilter by remember { mutableStateOf(MagnifierFilter.Normal) }
    var isFrozen by remember { mutableStateOf(false) }

    var cameraDevice by remember { mutableStateOf<CameraDevice?>(null) }
    var previewSurface by remember { mutableStateOf<Surface?>(null) }
    var captureSession by remember { mutableStateOf<CameraCaptureSession?>(null) }
    var previewRequestBuilder by remember { mutableStateOf<CaptureRequest.Builder?>(null) }

    fun closeCamera() {
        runCatching { captureSession?.close() }
        runCatching { cameraDevice?.close() }
        captureSession = null
        cameraDevice = null
        previewRequestBuilder = null
        previewSurface?.release()
        previewSurface = null
        torchOn = false
        isFrozen = false
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) cameraActive = true
            if (event == Lifecycle.Event.ON_PAUSE) {
                cameraActive = false
                closeCamera()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            closeCamera()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
                            val manager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                            val cameraId = manager.cameraIdList.firstOrNull { id ->
                                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                            } ?: manager.cameraIdList.firstOrNull() ?: return

                            val characteristics = manager.getCameraCharacteristics(cameraId)
                            hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                            sensorRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                            val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val ratios = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                                if (ratios != null) {
                                    zoomRange = ratios.lower..ratios.upper
                                }
                            } else {
                                zoomRange = 1f..maxZoom
                            }

                            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return

                            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                                override fun onOpened(camera: CameraDevice) {
                                    cameraDevice = camera
                                    surfaceTexture.setDefaultBufferSize(1920, 1080)
                                    val surface = Surface(surfaceTexture)
                                    previewSurface = surface
                                    try {
                                        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                            addTarget(surface)
                                            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                                        }
                                        previewRequestBuilder = builder
                                        camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                                            override fun onConfigured(session: CameraCaptureSession) {
                                                captureSession = session
                                                try {
                                                    session.setRepeatingRequest(builder.build(), null, null)
                                                } catch (_: Exception) {}
                                            }
                                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                                Toast.makeText(ctx, "Camera config failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }, null)
                                    } catch (_: Exception) {}
                                }

                                override fun onDisconnected(camera: CameraDevice) {
                                    camera.close()
                                    cameraDevice = null
                                }

                                override fun onError(camera: CameraDevice, error: Int) {
                                    camera.close()
                                    cameraDevice = null
                                }
                            }, null)
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            closeCamera()
                            return true
                        }
                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
                }
            },
            update = { view ->
                if (currentFilter.matrix != null) {
                    val paint = Paint().apply {
                        colorFilter = ColorMatrixColorFilter(ColorMatrix(currentFilter.matrix))
                    }
                    view.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
                } else {
                    view.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            }
        )

        // Google Apps 2026 Material 3 Expressive Floating Control Bar
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Zoom Slider & Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(BaseR.drawable.search_24px),
                        contentDescription = stringResource(I18nR.string.au_video_zoom),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Slider(
                        value = zoomLevel,
                        onValueChange = { level ->
                            val builder = previewRequestBuilder ?: return@Slider
                            val session = captureSession ?: return@Slider
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, level)
                                } else sensorRect?.let { sensor ->
                                    val width = (sensor.width() / level).toInt().coerceAtLeast(1)
                                    val height = (sensor.height() / level).toInt().coerceAtLeast(1)
                                    val left = sensor.centerX() - width / 2
                                    val top = sensor.centerY() - height / 2
                                    builder.set(CaptureRequest.SCALER_CROP_REGION, Rect(left, top, left + width, top + height))
                                }
                                session.setRepeatingRequest(builder.build(), null, null)
                                zoomLevel = level
                            } catch (_: Exception) {}
                        },
                        enabled = zoomRange.endInclusive > 1f && !isFrozen && captureSession != null,
                        valueRange = 1f..zoomRange.endInclusive.coerceAtLeast(1.001f),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = "${"%.1f".format(zoomLevel)}x",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Action Buttons (Filter, Torch, Freeze) - Google Apps 2026 Expressive Style
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Filter Menu Pill
                    Box {
                        FilledTonalButton(
                            onClick = { filterMenu = true },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(
                                painter = painterResource(BaseR.drawable.palette_24px),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(currentFilter.label)
                        }
                        DropdownMenu(
                            expanded = filterMenu,
                            onDismissRequest = { filterMenu = false },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            MagnifierFilter.entries.forEach { filter ->
                                DropdownMenuItem(
                                    text = { Text(filter.label) },
                                    onClick = { currentFilter = filter; filterMenu = false }
                                )
                            }
                        }
                    }

                    // Torch Button
                    FilledTonalButton(
                        onClick = {
                            val builder = previewRequestBuilder ?: return@FilledTonalButton
                            val session = captureSession ?: return@FilledTonalButton
                            try {
                                builder.set(
                                    CaptureRequest.FLASH_MODE,
                                    if (!torchOn) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
                                )
                                session.setRepeatingRequest(builder.build(), null, null)
                                torchOn = !torchOn
                            } catch (_: Exception) {}
                        },
                        enabled = hasFlash && !isFrozen && captureSession != null,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (torchOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (torchOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            painter = painterResource(BaseR.drawable.light_mode_24px),
                            contentDescription = stringResource(I18nR.string.telos_flashlight_app_label),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (torchOn) "Flash On" else "Flash")
                    }

                    // Freeze Frame Button
                    Button(
                        onClick = {
                            val session = captureSession ?: return@Button
                            try {
                                if (!isFrozen) session.stopRepeating()
                                else {
                                    val builder = previewRequestBuilder ?: return@Button
                                    session.setRepeatingRequest(builder.build(), null, null)
                                }
                                isFrozen = !isFrozen
                            } catch (_: Exception) {}
                        },
                        enabled = captureSession != null,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFrozen) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                            contentColor = if (isFrozen) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Icon(
                            painter = painterResource(if (isFrozen) BaseR.drawable.play_arrow_24px else BaseR.drawable.pause_24px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isFrozen) "Resume" else "Freeze")
                    }
                }
            }
        }
    }
}
