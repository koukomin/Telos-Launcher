package de.mm20.launcher2.ui.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** CameraX must release the device before this one-shot RAW+JPEG session, then rebind afterwards. */
internal object CameraRawCapture {
    fun supported(context: Context, cameraId: String): Boolean = runCatching {
        val manager = context.getSystemService(CameraManager::class.java)
        manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    suspend fun capture(context: Context, cameraId: String, rotation: Int): List<Uri> = withTimeout(20_000) {
        suspendCancellableCoroutine { continuation ->
            val thread = HandlerThread("telos-raw").apply { start() }
            val handler = Handler(thread.looper)
            var device: CameraDevice? = null
            var session: CameraCaptureSession? = null
            var rawReader: ImageReader? = null
            var jpegReader: ImageReader? = null
            var raw: Image? = null
            var jpeg: Image? = null
            var result: TotalCaptureResult? = null
            var complete = false
            fun close() {
                runCatching { session?.close() }; runCatching { device?.close() }
                runCatching { raw?.close() }; runCatching { jpeg?.close() }
                runCatching { rawReader?.close() }; runCatching { jpegReader?.close() }
                thread.quitSafely()
            }
            fun fail(error: Throwable) {
                if (complete) return
                complete = true
                close()
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            continuation.invokeOnCancellation { handler.post { complete = true; close() } }
            try {
                val manager = context.getSystemService(CameraManager::class.java)
                val characteristics = manager.getCameraCharacteristics(cameraId)
                check(supported(context, cameraId)) { "RAW unavailable" }
                val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: error("No camera outputs")
                val rawSize = sizes.getOutputSizes(ImageFormat.RAW_SENSOR)?.minByOrNull { it.width.toLong() * it.height } ?: error("No RAW output")
                val jpegSizes = sizes.getOutputSizes(ImageFormat.JPEG) ?: error("No JPEG output")
                val jpegSize = jpegSizes.filter { it.width.toLong() * it.height <= 12_000_000 }.maxByOrNull { it.width.toLong() * it.height }
                    ?: jpegSizes.minBy { it.width.toLong() * it.height }
                val rawOutput = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2)
                val jpegOutput = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2)
                rawReader = rawOutput; jpegReader = jpegOutput
                fun finishIfReady() {
                    val r = raw ?: return
                    val j = jpeg ?: return
                    val metadata = result ?: return
                    if (complete || !continuation.isActive) return
                    complete = true
                    val saved = mutableListOf<Uri>()
                    try {
                        val stem = "TELOS_${System.currentTimeMillis()}"
                        saved += save(context, "$stem.dng", "image/x-adobe-dng") { output ->
                            DngCreator(characteristics, metadata).use { creator ->
                                creator.setOrientation(when (rotation) { 90 -> 6; 180 -> 3; 270 -> 8; else -> 1 })
                                creator.writeImage(output, r)
                            }
                        }
                        saved += save(context, "$stem.jpg", "image/jpeg") { output ->
                            val buffer = j.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining()); buffer.get(bytes); output.write(bytes)
                        }
                        if (continuation.isActive) continuation.resume(saved)
                    } catch (error: Throwable) {
                        saved.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } finally { close() }
                }
                rawOutput.setOnImageAvailableListener({ reader ->
                    try {
                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        if (complete) image.close() else { raw?.close(); raw = image; finishIfReady() }
                    } catch (error: Exception) { fail(error) }
                }, handler)
                jpegOutput.setOnImageAvailableListener({ reader ->
                    try {
                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        if (complete) image.close() else { jpeg?.close(); jpeg = image; finishIfReady() }
                    } catch (error: Exception) { fail(error) }
                }, handler)
                manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (complete) { camera.close(); return }
                        device = camera
                        try { camera.createCaptureSession(listOf(rawOutput.surface, jpegOutput.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(configured: CameraCaptureSession) {
                                if (complete) { configured.close(); return }
                                session = configured
                                try {
                                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                        addTarget(rawOutput.surface); addTarget(jpegOutput.surface)
                                        set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                                        set(CaptureRequest.JPEG_ORIENTATION, rotation)
                                    }
                                    configured.capture(request.build(), object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureCompleted(s: CameraCaptureSession, request: CaptureRequest, metadata: TotalCaptureResult) {
                                            result = metadata; finishIfReady()
                                        }
                                        override fun onCaptureFailed(s: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) = fail(IOException("RAW capture failed"))
                                    }, handler)
                                } catch (error: Exception) { fail(error) }
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) = fail(IOException("Unsupported RAW/JPEG combination"))
                        }, handler) } catch (error: Exception) { fail(error) }
                    }
                    override fun onDisconnected(camera: CameraDevice) { camera.close(); fail(IOException("Camera disconnected")) }
                    override fun onError(camera: CameraDevice, error: Int) { camera.close(); fail(IOException("Camera error $error")) }
                }, handler)
            } catch (error: Exception) { fail(error) }
        }
    }

    private fun save(context: Context, name: String, mime: String, write: (java.io.OutputStream) -> Unit): Uri {
        if (Build.VERSION.SDK_INT < 29) {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DCIM), "TelosCamera/$name").apply { parentFile?.mkdirs() }
            try { file.outputStream().use(write) } catch (error: Exception) { file.delete(); throw error }
            android.media.MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(mime), null)
            return Uri.fromFile(file)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/TelosCamera"); put(MediaStore.Images.Media.IS_PENDING, 1)
        }) ?: throw IOException("Cannot create image")
        try {
            (resolver.openOutputStream(uri) ?: throw IOException("Cannot write image")).use(write)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
    }
}
