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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
        0f, 0f, 0f, 0f, 255f,
        0f, 0f, 0f, 0f, 255f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    BlueOnYellow("Blue/Yellow", floatArrayOf(
        0f, 0f, 0f, 0f, 255f,
        0f, 0f, 0f, 0f, 255f,
        0f, 0f, 0f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    ))
}

@Composable
fun MagnifierScreen() {
    val context = LocalContext.current
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
            Toast.makeText(context, "Camera permission required for Magnifier", Toast.LENGTH_SHORT).show()
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
                Text("Camera Permission Required", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    var zoomLevel by remember { mutableStateOf(1f) }
    var torchOn by remember { mutableStateOf(false) }
    var currentFilter by remember { mutableStateOf(MagnifierFilter.Normal) }
    var isFrozen by remember { mutableStateOf(false) }

    var cameraDevice by remember { mutableStateOf<CameraDevice?>(null) }
    var captureSession by remember { mutableStateOf<CameraCaptureSession?>(null) }
    var previewRequestBuilder by remember { mutableStateOf<CaptureRequest.Builder?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                captureSession?.close()
                cameraDevice?.close()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Camera Preview
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    currentFilter.matrix?.let {
                        val cm = androidx.compose.ui.graphics.ColorMatrix(it)
                        colorFilter = ColorFilter.colorMatrix(cm)
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

                                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                                    override fun onOpened(camera: CameraDevice) {
                                        cameraDevice = camera
                                        val surface = Surface(surfaceTexture)
                                        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                        builder.addTarget(surface)
                                        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                        previewRequestBuilder = builder

                                        camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                                            override fun onConfigured(session: CameraCaptureSession) {
                                                captureSession = session
                                                try {
                                                    session.setRepeatingRequest(builder.build(), null, null)
                                                } catch (_: Exception) {}
                                            }
                                            override fun onConfigureFailed(session: CameraCaptureSession) {}
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
                            try {
                                captureSession?.close()
                                cameraDevice?.close()
                            } catch (_: Exception) {}
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
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(24.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom Slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Icon(painterResource(R.drawable.search_24px), contentDescription = "Zoom", tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = zoomLevel,
                    onValueChange = { level ->
                        zoomLevel = level
                        val builder = previewRequestBuilder ?: return@Slider
                        val session = captureSession ?: return@Slider
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, level)
                            }
                            session.setRepeatingRequest(builder.build(), null, null)
                        } catch (_: Exception) {}
                    },
                    valueRange = 1f..10f,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text("${"%.1f".format(zoomLevel)}x", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            // Color Filters Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(MagnifierFilter.entries) { filter ->
                    FilterChip(
                        selected = currentFilter == filter,
                        onClick = { currentFilter = filter },
                        label = { Text(filter.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action Buttons (Torch & Freeze Frame)
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        torchOn = !torchOn
                        val builder = previewRequestBuilder ?: return@IconButton
                        val session = captureSession ?: return@IconButton
                        try {
                            builder.set(
                                CaptureRequest.FLASH_MODE,
                                if (torchOn) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
                            )
                            session.setRepeatingRequest(builder.build(), null, null)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (torchOn) MaterialTheme.colorScheme.primary else Color.DarkGray)
                ) {
                    Icon(
                        painterResource(R.drawable.light_mode_24px),
                        contentDescription = "Torch",
                        tint = if (torchOn) MaterialTheme.colorScheme.onPrimary else Color.White
                    )
                }

                Button(
                    onClick = {
                        isFrozen = !isFrozen
                        val session = captureSession ?: return@Button
                        try {
                            if (isFrozen) session.stopRepeating()
                            else {
                                val builder = previewRequestBuilder ?: return@Button
                                session.setRepeatingRequest(builder.build(), null, null)
                            }
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFrozen) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(if (isFrozen) "Unfreeze" else "Freeze Frame")
                }
            }
        }
    }
}
