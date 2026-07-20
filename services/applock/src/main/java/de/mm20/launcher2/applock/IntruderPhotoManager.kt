package de.mm20.launcher2.applock

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.media.ImageReader
import android.util.Log
import androidx.core.content.getSystemService
import de.mm20.launcher2.preferences.applock.AppLockSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Silent front-camera capture triggered by a failed App Lock authentication attempt. Storage is
 * strictly local: files live in app-private internal storage ([Context.filesDir]) and are never
 * uploaded, shared, or exposed via MediaStore or any content provider.
 */
class IntruderPhotoManager(
    private val context: Context,
    private val appLockSettings: AppLockSettings,
) {
    // A single persistent background thread for the camera2 callbacks, kept alive for this
    // singleton's process lifetime rather than spun up/torn down per capture - CameraDevice.close()
    // delivers its onClosed callback asynchronously, so quitting the thread right after a capture
    // races that callback and logs a "dead thread" warning even though nothing is broken.
    private val cameraThread by lazy { HandlerThread("IntruderPhotoCamera").apply { start() } }
    private val cameraHandler by lazy { Handler(cameraThread.looper) }

    /**
     * Silently captures one front-camera photo and stores it locally. Never throws and never
     * surfaces an error to the caller - missing hardware, missing permission, or any capture
     * failure is swallowed, since this must not be able to disrupt the lock gate it's called
     * from.
     */
    suspend fun capture() {
        try {
            val bytes = captureJpeg() ?: return
            withContext(Dispatchers.IO) {
                val dir = photoDir()
                dir.mkdirs()
                val name = fileNameFormat.format(Date())
                File(dir, "$name.jpg").writeBytes(bytes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to capture intruder photo", e)
        }
    }

    suspend fun listPhotos(): List<File> = withContext(Dispatchers.IO) {
        photoDir().listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    suspend fun delete(file: File) = withContext(Dispatchers.IO) {
        file.delete()
        Unit
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        photoDir().listFiles()?.forEach { it.delete() }
        Unit
    }

    /** Deletes any photo older than the configured retention period. */
    suspend fun deleteExpired() {
        val retentionDays = appLockSettings.intruderPhotoRetentionDays.first()
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        withContext(Dispatchers.IO) {
            photoDir().listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) file.delete()
            }
        }
    }

    private fun photoDir() = File(context.filesDir, "intruder_photos")

    private suspend fun captureJpeg(): ByteArray? {
        val cameraManager = context.getSystemService<CameraManager>() ?: return null
        val cameraId = cameraManager.cameraIdList.firstOrNull {
            cameraManager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        } ?: return null

        val configs = cameraManager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val size = configs?.getOutputSizes(ImageFormat.JPEG)?.minByOrNull { it.width * it.height }
            ?: return null

        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
        try {
            return withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                openCameraAndCapture(cameraManager, cameraId, reader, cameraHandler)
            }
        } finally {
            reader.close()
        }
    }

    private suspend fun openCameraAndCapture(
        cameraManager: CameraManager,
        cameraId: String,
        reader: ImageReader,
        handler: Handler,
    ): ByteArray? = suspendCancellableCoroutine { cont ->
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null

        fun finish(result: ByteArray?) {
            try {
                session?.close()
            } catch (_: Exception) {
            }
            try {
                device?.close()
            } catch (_: Exception) {
            }
            if (cont.isActive) cont.resumeWith(Result.success(result))
        }

        reader.setOnImageAvailableListener({ r ->
            val image = try {
                r.acquireLatestImage()
            } catch (_: Exception) {
                null
            }
            if (image != null) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                finish(bytes)
            }
        }, handler)

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(cameraDevice: CameraDevice) {
                    device = cameraDevice
                    try {
                        cameraDevice.createCaptureSession(
                            listOf(reader.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(s: CameraCaptureSession) {
                                    session = s
                                    try {
                                        val request = cameraDevice.createCaptureRequest(
                                            CameraDevice.TEMPLATE_STILL_CAPTURE
                                        ).apply {
                                            addTarget(reader.surface)
                                            set(
                                                CaptureRequest.FLASH_MODE,
                                                CaptureRequest.FLASH_MODE_OFF,
                                            )
                                        }.build()
                                        s.capture(request, null, handler)
                                    } catch (e: Exception) {
                                        finish(null)
                                    }
                                }

                                override fun onConfigureFailed(s: CameraCaptureSession) {
                                    finish(null)
                                }
                            },
                            handler,
                        )
                    } catch (e: Exception) {
                        finish(null)
                    }
                }

                override fun onDisconnected(cameraDevice: CameraDevice) {
                    device = cameraDevice
                    finish(null)
                }

                override fun onError(cameraDevice: CameraDevice, error: Int) {
                    device = cameraDevice
                    finish(null)
                }
            }, handler)
        } catch (e: Exception) {
            finish(null)
        }

        cont.invokeOnCancellation { finish(null) }
    }

    private companion object {
        private const val TAG = "IntruderPhotoManager"
        private const val CAPTURE_TIMEOUT_MS = 5000L
        private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US)
    }
}
