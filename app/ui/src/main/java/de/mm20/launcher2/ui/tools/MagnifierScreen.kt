package de.mm20.launcher2.ui.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
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
            Toast.makeText(context, context.getString(R.string.virtual_magnifier_permission), Toast.LENGTH_SHORT).show()
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.virtual_magnifier_permission), color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.hc_allow))
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Camera Preview
        if (cameraActive) AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    colorFilter = currentFilter.matrix?.let {
                        ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(it))
                    }
                },
            factory = { ctx ->
                TextureView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            val cameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                            try {
                                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                                    val chars = cameraManager.getCameraCharacteristics(id)
                                    chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                                } ?: cameraManager.cameraIdList.first()

                                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                                hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                                sensorRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                                val ratioRange = if (Build.VERSION.SDK_INT >= 30) characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
                                val maximum = ratioRange?.upper ?: characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
                                zoomRange = 1f..maximum.coerceAtLeast(1f)
                                zoomLevel = zoomLevel.coerceIn(zoomRange)
                                val outputSize = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                                    ?.getOutputSizes(SurfaceTexture::class.java)?.minByOrNull {
                                        kotlin.math.abs(it.width.toLong() * it.height - width.toLong() * height)
                                    }
                                outputSize?.let { surfaceTexture.setDefaultBufferSize(it.width, it.height) }
                                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                                    override fun onOpened(camera: CameraDevice) {
                                        if (!cameraActive) { camera.close(); return }
                                        cameraDevice = camera
                                        val surface = Surface(surfaceTexture)
                                        previewSurface = surface
                                        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                        builder.addTarget(surface)
                                        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                        previewRequestBuilder = builder

                                        camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                                            override fun onConfigured(session: CameraCaptureSession) {
                                                if (!cameraActive || cameraDevice !== camera) { session.close(); surface.release(); return }
                                                captureSession = session
                                                try {
                                                    session.setRepeatingRequest(builder.build(), null, null)
                                                } catch (_: Exception) {}
                                            }
                                            override fun onConfigureFailed(session: CameraCaptureSession) { session.close(); surface.release() }
                                        }, null)
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
                            } catch (_: Exception) {}
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            closeCamera()
                            return true
                        }
                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
                }
            }
        )

        // Overlay Controls (Glassmorphism M3 2026 UI)
        Column(
            modifier = Modifier
                .fillMaxWidth(if (maxWidth >= 480.dp && maxHeight < 400.dp) 0.6f else 1f)
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(24.dp))
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom Slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Icon(painterResource(R.drawable.search_24px), contentDescription = stringResource(R.string.au_video_zoom), tint = Color.White)
                Spacer(Modifier.width(8.dp))
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
                Spacer(Modifier.width(8.dp))
                Text("${"%.1f".format(zoomLevel)}x", color = Color.White, fontWeight = FontWeight.Bold)
            }

            // Action Buttons (Torch & Freeze Frame)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box {
                    IconButton(onClick = { filterMenu = true }) {
                        Icon(painterResource(R.drawable.palette_24px), stringResource(when (currentFilter) {
                            MagnifierFilter.Normal -> R.string.vn_size_normal
                            MagnifierFilter.Inverted -> R.string.au3_appearance_cs_inverse_colors
                            MagnifierFilter.Monochrome -> R.string.au3_searchset_icon_monochrome
                            MagnifierFilter.YellowOnBlack -> R.string.app_polish_yellow_black
                            MagnifierFilter.BlueOnYellow -> R.string.app_polish_blue_yellow
                        }), tint = Color.White)
                    }
                    DropdownMenu(filterMenu, { filterMenu = false }) {
                        MagnifierFilter.entries.forEach { filter ->
                            DropdownMenuItem(text = { Text(stringResource(when (filter) {
                                MagnifierFilter.Normal -> R.string.vn_size_normal
                                MagnifierFilter.Inverted -> R.string.au3_appearance_cs_inverse_colors
                                MagnifierFilter.Monochrome -> R.string.au3_searchset_icon_monochrome
                                MagnifierFilter.YellowOnBlack -> R.string.app_polish_yellow_black
                                MagnifierFilter.BlueOnYellow -> R.string.app_polish_blue_yellow
                            })) }, onClick = { currentFilter = filter; filterMenu = false })
                        }
                    }
                }
                IconButton(
                    onClick = {
                        val builder = previewRequestBuilder ?: return@IconButton
                        val session = captureSession ?: return@IconButton
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
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (torchOn) MaterialTheme.colorScheme.primary else Color.DarkGray)
                ) {
                    Icon(
                        painterResource(R.drawable.light_mode_24px),
                        contentDescription = stringResource(R.string.telos_flashlight_app_label),
                        tint = if (torchOn) MaterialTheme.colorScheme.onPrimary else Color.White
                    )
                }

                IconButton(
                    onClick = {
                        val session = captureSession ?: return@IconButton
                        try {
                            if (!isFrozen) session.stopRepeating()
                            else {
                                val builder = previewRequestBuilder ?: return@IconButton
                                session.setRepeatingRequest(builder.build(), null, null)
                            }
                            isFrozen = !isFrozen
                        } catch (_: Exception) {}
                    },
                    enabled = captureSession != null,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(
                        if (isFrozen) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(painterResource(if (isFrozen) R.drawable.play_arrow_24px else R.drawable.pause_24px),
                        stringResource(if (isFrozen) R.string.virtual_magnifier_resume else R.string.virtual_magnifier_freeze),
                        tint = if (isFrozen) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSecondary)
                }
            }
        }
    }
}
