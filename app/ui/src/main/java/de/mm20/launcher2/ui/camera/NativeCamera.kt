package de.mm20.launcher2.ui.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.CaptureResult
import androidx.camera.camera2.interop.Camera2Interop
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.*
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Lifecycle-bound native capture; never hands the camera off to another application. */
@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
internal class NativeCamera(private val context: Context, private val owner: LifecycleOwner) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = ContextCompat.getMainExecutor(context)
    private var provider: ProcessCameraProvider? = null
    private var extensions: ExtensionsManager? = null
    private var viewfinder: PreviewView? = null
    private var photo: ImageCapture? = null
    private var video: VideoCapture<Recorder>? = null
    var camera: Camera? = null; private set
    var recording: Recording? = null; private set
    var isoRange: android.util.Range<Int>? = null; private set
    var exposureRange: android.util.Range<Long>? = null; private set
    var manualAvailable = false; private set
    var rawAvailable = false; private set
    var analysisAvailable = false; private set
    var awbModes: List<Int> = emptyList(); private set
    var aeLockAvailable = false; private set
    private var extensionActive = false
    @Volatile private var closed = false
    @Volatile private var generation = 0
    var faceAvailable = false; private set
    @Volatile var onFace: (() -> Unit)? = null
    @Volatile var onMotion: (() -> Unit)? = null
    @Volatile var onCode: ((CameraCode) -> Unit)? = null
    private val codeDecoder = CameraCodeDecoder()
    private var lastCodeScan = 0L
    private var lastCodeText: String? = null
    private var lastCodeAt = 0L
    private var lastAutomaticCapture = 0L
    private fun trigger(face: Boolean) {
        val callback = if (face) onFace else onMotion
        val now = android.os.SystemClock.elapsedRealtime()
        if (callback != null && now - lastAutomaticCapture >= 5000) {
            lastAutomaticCapture = now
            main.execute { if (!closed && owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) callback() }
        }
    }

    fun bind(view: PreviewView, front: Boolean, mode: Int, onReady: (Set<Int>) -> Unit, onHistogram: (List<Float>) -> Unit, onError: (String) -> Unit) {
        viewfinder = view
        val token = ++generation
        recording?.stop(); recording = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (closed || token != generation) return@addListener
            try {
                val p = future.get(); provider = p
                val extensionFuture = ExtensionsManager.getInstanceAsync(context, p)
                extensionFuture.addListener({
                    if (closed || token != generation) return@addListener
                    try {
                        val ext = extensionFuture.get(); extensions = ext
                        val base = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        val modes = setOf(ExtensionMode.BOKEH, ExtensionMode.NIGHT).filter { ext.isExtensionAvailable(base, it) }.toSet()
                        val selected = if (mode in modes) ext.getExtensionEnabledCameraSelector(base, mode) else base
                        p.unbindAll()
                        val previewBuilder = Preview.Builder()
                        val initialInfo = Camera2CameraInfo.from(base.filter(p.availableCameraInfos).first())
                        val faceModes = initialInfo.getCameraCharacteristic(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES) ?: intArrayOf()
                        faceAvailable = mode == MODE_PHOTO && CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE in faceModes
                        if (faceAvailable) Camera2Interop.Extender(previewBuilder)
                            .setCaptureRequestOption(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE)
                            .setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                                    if (token == generation && !result.get(CaptureResult.STATISTICS_FACES).isNullOrEmpty()) trigger(true)
                                }
                            })
                        val preview = previewBuilder.build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        photo = ImageCapture.Builder().setTargetResolution(android.util.Size(2560, 1920)).setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                        val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD))).build()
                        video = VideoCapture.withOutput(recorder)
                        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        var lastHistogram = 0L
                        var previousLuminance: IntArray? = null
                        analysis.setAnalyzer(executor) { image ->
                            try {
                                val time = android.os.SystemClock.elapsedRealtime()
                                if (onCode != null && time - lastCodeScan >= 700) {
                                    lastCodeScan = time
                                    val plane = image.planes[0]
                                    val result = codeDecoder.decode(plane.buffer, image.width, image.height, plane.rowStride, plane.pixelStride)
                                    if (result != null && (result.text != lastCodeText || time - lastCodeAt > 10_000)) {
                                        lastCodeText = result.text; lastCodeAt = time
                                        main.execute {
                                            if (!closed && token == generation && owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) onCode?.invoke(result)
                                        }
                                    }
                                }
                                if (time - lastHistogram > 350) {
                                    lastHistogram = time
                                    val plane = image.planes[0]; val buffer = plane.buffer; val bins = FloatArray(32)
                                    for (y in 0 until image.height step 8) for (x in 0 until image.width step 8) {
                                        val offset = y * plane.rowStride + x * plane.pixelStride
                                        if (offset < buffer.limit()) bins[(buffer.get(offset).toInt() and 255) / 8]++
                                    }
                                    val luminance = IntArray(32 * 24) { index ->
                                        val x = (index % 32) * (image.width - 1) / 31
                                        val y = (index / 32) * (image.height - 1) / 23
                                        buffer.get(y * plane.rowStride + x * plane.pixelStride).toInt() and 255
                                    }
                                    previousLuminance?.let { previous ->
                                        if (luminance.indices.sumOf { kotlin.math.abs(luminance[it] - previous[it]) }.toFloat() / luminance.size > 20f) trigger(false)
                                    }
                                    previousLuminance = luminance
                                    val maximum = bins.maxOrNull()?.coerceAtLeast(1f) ?: 1f
                                    main.execute { if (!closed && token == generation) onHistogram(bins.map { it / maximum }) }
                                }
                            } finally { image.close() }
                        }
                        analysisAvailable = false
                        camera = when {
                            mode == MODE_VIDEO -> p.bindToLifecycle(owner, base, preview, video!!)
                            mode in modes -> p.bindToLifecycle(owner, selected, preview, photo!!)
                            else -> try { p.bindToLifecycle(owner, base, preview, photo!!, analysis).also { analysisAvailable = true } }
                                catch (_: IllegalArgumentException) { p.bindToLifecycle(owner, base, preview, photo!!) }
                        }
                        val info = Camera2CameraInfo.from(camera!!.cameraInfo)
                        val capabilities = info.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                        rawAvailable = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW in capabilities
                        extensionActive = mode in modes
                        awbModes = if (extensionActive) emptyList() else info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toList().orEmpty()
                        aeLockAvailable = !extensionActive && info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true
                        manualAvailable = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in capabilities && mode !in modes && mode != MODE_VIDEO
                        isoRange = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                        exposureRange = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                        onReady(modes)
                    } catch (error: Exception) { onError(error.message.orEmpty()) }
                }, main)
            } catch (error: Exception) { onError(error.message.orEmpty()) }
        }, main)
    }

    fun focus(view: PreviewView, x: Float, y: Float) {
        val point = view.meteringPointFactory.createPoint(x, y)
        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).setAutoCancelDuration(5, java.util.concurrent.TimeUnit.SECONDS).build())
    }
    fun zoom(ratio: Float) {
        val state = camera?.cameraInfo?.zoomState?.value ?: return
        camera?.cameraControl?.setZoomRatio(ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio))
    }
    fun exposure(index: Int) { camera?.cameraControl?.setExposureCompensationIndex(index) }
    fun manual(enabled: Boolean, iso: Int, durationMs: Float, aeLock: Boolean, whiteBalance: Int) {
        val control = camera?.let { Camera2CameraControl.from(it.cameraControl) } ?: return
        if (extensionActive) return
        val options = CaptureRequestOptions.Builder()
        if (aeLockAvailable) options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, aeLock)
        if (whiteBalance in awbModes) options.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, whiteBalance)
        if (enabled && manualAvailable) {
            val sensitivity = isoRange?.clamp(iso) ?: return
            val duration = exposureRange?.clamp((durationMs * 1_000_000).toLong()) ?: return
            options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, sensitivity)
                .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, duration)
        }
        control.setCaptureRequestOptions(options.build())
    }
    fun capture(format: String, keepExif: Boolean, flash: Boolean, hidden: Boolean, location: android.location.Location?, onDone: (Uri?) -> Unit, onError: (String) -> Unit) {
        val capture = photo ?: return
        capture.targetRotation = viewfinder?.display?.rotation ?: android.view.Surface.ROTATION_0
        capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        val temporary = File.createTempFile("capture", ".jpg", context.cacheDir)
        val metadata = ImageCapture.Metadata().apply { this.location = location }
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(temporary).setMetadata(metadata).build(), executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val mime = when (format) { "PNG" -> "image/png"; "WebP" -> "image/webp"; else -> "image/jpeg" }
                    val extension = when (format) { "PNG" -> "png"; "WebP" -> "webp"; else -> "jpg" }
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "Telos_${System.currentTimeMillis()}.$extension")
                        put(MediaStore.Images.Media.MIME_TYPE, mime)
                        if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Telos"); put(MediaStore.Images.Media.IS_PENDING, 1) }
                    }
                    val hiddenFile = if (hidden) File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "TelosHidden").let { directory ->
                        check(directory.exists() || directory.mkdirs())
                        File(directory, ".nomedia").createNewFile()
                        File(directory, values.getAsString(MediaStore.Images.Media.DISPLAY_NAME))
                    } else null
                    val uri = if (hiddenFile != null) Uri.fromFile(hiddenFile) else context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Media storage unavailable")
                    try {
                        (hiddenFile?.outputStream() ?: context.contentResolver.openOutputStream(uri))?.use { destination ->
                            if (format == "JPEG" && keepExif) temporary.inputStream().use { it.copyTo(destination) }
                            else {
                                val exif = ExifInterface(temporary)
                                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(temporary.path, bounds)
                                val options = BitmapFactory.Options().apply {
                                    inSampleSize = CameraImageSizing.sampleSize(bounds.outWidth, bounds.outHeight, 5_000_000)
                                }
                                val bitmap = BitmapFactory.decodeFile(temporary.path, options) ?: error("Image decode failed")
                                val matrix = Matrix().apply { postRotate(exif.rotationDegrees.toFloat()); if (exif.isFlipped) postScale(-1f, 1f) }
                                val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                val type = when (format) { "PNG" -> Bitmap.CompressFormat.PNG; "WebP" -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP; else -> Bitmap.CompressFormat.JPEG }
                                try { check(oriented.compress(type, 95, destination)) }
                                finally {
                                    if (oriented !== bitmap) oriented.recycle()
                                    bitmap.recycle()
                                }
                            }
                        } ?: error("Media storage unavailable")
                        if (hiddenFile == null && Build.VERSION.SDK_INT >= 29) context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                        main.execute { onDone(uri) }
                    } catch (error: Exception) { if (hiddenFile != null) hiddenFile.delete() else context.contentResolver.delete(uri, null, null); throw error }
                } catch (error: Exception) { main.execute { onError(error.message.orEmpty()) } }
                finally { temporary.delete() }
            }
            override fun onError(error: ImageCaptureException) { temporary.delete(); main.execute { onError(error.message.orEmpty()) } }
        })
    }
    fun captureDocument(destination: File, onDone: () -> Unit, onError: (String) -> Unit) {
        val capture = photo ?: run { onError("Camera not ready"); return }
        capture.targetRotation = viewfinder?.display?.rotation ?: android.view.Surface.ROTATION_0
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(destination).build(), executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) { main.execute(onDone) }
                override fun onError(error: ImageCaptureException) { main.execute { onError(error.message.orEmpty()) } }
            })
    }

    @Suppress("MissingPermission")
    fun startVideo(audio: Boolean, flash: Boolean, onStarted: () -> Unit, onDone: (Uri?) -> Unit, onError: (String) -> Unit) {
        video?.targetRotation = viewfinder?.display?.rotation ?: android.view.Surface.ROTATION_0
        val recorder = video?.output ?: return
        camera?.cameraControl?.enableTorch(flash)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Telos_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Telos")
        }
        var pending = recorder.prepareRecording(context, MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build())
        if (audio) pending = pending.withAudioEnabled()
        recording = pending.start(main) { event -> when(event) {
            is VideoRecordEvent.Start -> onStarted()
            is VideoRecordEvent.Finalize -> {
                recording = null; camera?.cameraControl?.enableTorch(false)
                if (event.hasError()) onError(event.cause?.message ?: event.error.toString()) else onDone(event.outputResults.outputUri)
            }
        } }
    }
    val cameraId: String? get() = camera?.let { Camera2CameraInfo.from(it.cameraInfo).cameraId }
    fun unbind() { generation++; provider?.unbindAll(); readyForRebind() }
    private fun readyForRebind() { camera = null; photo = null; video = null }
    fun close() {
        closed = true; generation++; onFace = null; onMotion = null
        recording?.stop(); recording = null; provider?.unbindAll()
        readyForRebind(); viewfinder = null; executor.shutdown()
    }
    companion object { const val MODE_PHOTO = 0; const val MODE_VIDEO = -1 }
}
