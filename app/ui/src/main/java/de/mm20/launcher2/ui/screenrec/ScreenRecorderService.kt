package de.mm20.launcher2.ui.screenrec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Records the screen with MediaProjection while it runs as a foreground service. Started by
 * [ScreenRecorderRequestActivity] once the user has allowed the capture. The recording goes to
 * Movies/Telos (Android 10+) so that Telos Video and the gallery list it.
 */
class ScreenRecorderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timer: Job? = null
    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var display: VirtualDisplay? = null
    private var pfd: ParcelFileDescriptor? = null
    private var outputUri: Uri? = null
    private var outputFile: File? = null
    private var accumulated = 0L
    private var segmentStart = 0L
    private var previousShowTouches: Int? = null
    private var screenOffReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                finishRecording()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> togglePause()
            ACTION_START -> {
                val current = ScreenRecorderState.state.value.status
                if (current != ScreenRecStatus.Idle) {
                    // started with startForegroundService(): startForeground() is mandatory within a few seconds, or the app crashes
                    startInForeground()
                    when (current) {
                        ScreenRecStatus.Recording -> notify(buildNotification(getString(R.string.screenrec_recording), chronometer = true, paused = false))
                        ScreenRecStatus.Paused -> notify(buildNotification(getString(R.string.screenrec_paused), chronometer = false, paused = true))
                        else -> {}
                    }
                    return START_NOT_STICKY
                }
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
                startInForeground()
                if (data == null) {
                    fail()
                } else {
                    scope.launch { countdownAndRecord(resultCode, data) }
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun countdownAndRecord(resultCode: Int, data: Intent) {
        val settings = ScreenRecorderSettings(this)
        for (second in settings.countdown downTo 1) {
            ScreenRecorderState.set(ScreenRecState(ScreenRecStatus.Countdown, countdown = second))
            notify(buildNotification(getString(R.string.screenrec_countdown, second), chronometer = false, paused = false, actions = false))
            delay(1000)
        }
        if (!startRecording(resultCode, data, settings)) fail()
    }

    private fun startRecording(resultCode: Int, data: Intent, settings: ScreenRecorderSettings): Boolean {
        var pendingRecorder: MediaRecorder? = null
        var pendingProjection: MediaProjection? = null
        return try {
            val (width, height, dpi) = captureSize(settings.resolution)
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            pendingRecorder = rec
            val withMic = settings.audio == ScreenAudio.Microphone &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (withMic) rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (withMic) {
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioEncodingBitRate(128_000)
                rec.setAudioSamplingRate(44_100)
            }
            rec.setVideoSize(width, height)
            rec.setVideoFrameRate(settings.fps)
            rec.setVideoEncodingBitRate((settings.quality.bitRate * (settings.fps / 30f)).toInt())
            openOutput(rec)
            rec.prepare()

            val manager = getSystemService(MediaProjectionManager::class.java)
            val mp = manager.getMediaProjection(resultCode, data) ?: throw IllegalStateException("no projection")
            pendingProjection = mp
            // required since Android 14, and called when the user stops the capture from the system
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    finishRecording()
                }
            }, Handler(Looper.getMainLooper()))
            val vd = mp.createVirtualDisplay(
                "TelosScreenRecorder", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, rec.surface, null, null,
            )
            rec.start()
            projection = mp
            recorder = rec
            display = vd
            accumulated = 0
            segmentStart = System.currentTimeMillis()
            if (settings.showTouches) setShowTouches(true)
            if (settings.stopOnScreenOff) registerScreenOff()
            ScreenRecorderState.set(ScreenRecState(ScreenRecStatus.Recording))
            notify(buildNotification(getString(R.string.screenrec_recording), chronometer = true, paused = false))
            timer = scope.launch {
                while (isActive) {
                    delay(500)
                    if (ScreenRecorderState.state.value.status == ScreenRecStatus.Recording) {
                        ScreenRecorderState.update { it.copy(elapsedMs = accumulated + System.currentTimeMillis() - segmentStart) }
                    }
                }
            }
            true
        } catch (e: Exception) {
            runCatching { pendingRecorder?.release() }
            runCatching { pendingProjection?.stop() }
            cleanup(deleteOutput = true)
            false
        }
    }

    /** The size of the video: the screen, scaled to the chosen height, with even sides (the encoder needs that) */
    private fun captureSize(resolution: ScreenResolution): Triple<Int, Int, Int> {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = resources.displayMetrics
        var screenW = metrics.widthPixels
        var screenH = metrics.heightPixels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            screenW = bounds.width()
            screenH = bounds.height()
        }
        val portrait = screenH >= screenW
        val shortSide = minOf(screenW, screenH)
        val longSide = maxOf(screenW, screenH)
        // the chosen height is the long side of the picture, 1080 means 1920x1080 like on TV
        val target = if (resolution.height == 0) shortSide else resolution.height
        val scale = if (resolution == ScreenResolution.Native) 1f else (target * 16f / 9f / longSide).coerceAtMost(1f)
        val scaledLong = even((longSide * scale).roundToInt())
        val scaledShort = even((shortSide * scale).roundToInt())
        val width = if (portrait) scaledShort else scaledLong
        val height = if (portrait) scaledLong else scaledShort
        return Triple(width, height, metrics.densityDpi)
    }

    private fun even(value: Int) = (value / 2) * 2

    private fun openOutput(rec: MediaRecorder) {
        val name = "Screen_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Telos")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("no output")
            outputUri = uri
            val descriptor = contentResolver.openFileDescriptor(uri, "w") ?: throw IllegalStateException("no output")
            pfd = descriptor
            rec.setOutputFile(descriptor.fileDescriptor)
        } else {
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Telos").apply { mkdirs() }
            val file = File(dir, name)
            outputFile = file
            rec.setOutputFile(file.absolutePath)
        }
    }

    private fun togglePause() {
        val rec = recorder ?: return
        val status = ScreenRecorderState.state.value.status
        try {
            if (status == ScreenRecStatus.Recording) {
                rec.pause()
                accumulated += System.currentTimeMillis() - segmentStart
                ScreenRecorderState.update { it.copy(status = ScreenRecStatus.Paused, elapsedMs = accumulated) }
                notify(buildNotification(getString(R.string.screenrec_paused), chronometer = false, paused = true))
            } else if (status == ScreenRecStatus.Paused) {
                rec.resume()
                segmentStart = System.currentTimeMillis()
                ScreenRecorderState.update { it.copy(status = ScreenRecStatus.Recording) }
                notify(buildNotification(getString(R.string.screenrec_recording), chronometer = true, paused = false))
            }
        } catch (_: Exception) {
        }
    }

    private fun registerScreenOff() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                finishRecording()
            }
        }
        ContextCompat.registerReceiver(this, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        screenOffReceiver = receiver
    }

    /** Stops the recording and keeps it */
    private fun finishRecording() {
        val status = ScreenRecorderState.state.value.status
        if (status == ScreenRecStatus.Idle) {
            stopSelf()
            return
        }
        var saved = recorder != null
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // stop() fails when nothing was recorded, the file is then empty
            saved = false
        }
        cleanup(deleteOutput = !saved)
        if (saved) Toast.makeText(this, R.string.screenrec_saved, Toast.LENGTH_SHORT).show()
        stopSelf()
    }

    private fun fail() {
        cleanup(deleteOutput = true)
        ScreenRecorderState.set(ScreenRecState(error = true))
        Toast.makeText(this, R.string.screenrec_start_failed, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    private fun cleanup(deleteOutput: Boolean) {
        timer?.cancel()
        timer = null
        runCatching { display?.release() }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        runCatching { projection?.stop() }
        runCatching { pfd?.close() }
        screenOffReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenOffReceiver = null
        display = null
        recorder = null
        projection = null
        pfd = null
        val uri = outputUri
        if (uri != null) {
            if (deleteOutput) {
                runCatching { contentResolver.delete(uri, null, null) }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                }
            }
        }
        val file = outputFile
        if (file != null && deleteOutput) file.delete()
        outputUri = null
        outputFile = null
        restoreShowTouches()
        ScreenRecorderState.set(ScreenRecState())
    }

    private fun setShowTouches(on: Boolean) {
        if (!Settings.System.canWrite(this)) return
        runCatching {
            if (on) {
                previousShowTouches = Settings.System.getInt(contentResolver, "show_touches", 0)
                Settings.System.putInt(contentResolver, "show_touches", 1)
            }
        }
    }

    private fun restoreShowTouches() {
        val previous = previousShowTouches ?: return
        runCatching { Settings.System.putInt(contentResolver, "show_touches", previous) }
        previousShowTouches = null
    }

    private fun startInForeground() {
        val notification = buildNotification(getString(R.string.screenrec_starting), chronometer = false, paused = false, actions = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notify(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String, chronometer: Boolean, paused: Boolean, actions: Boolean = true): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.screenrec_notification_channel), NotificationManager.IMPORTANCE_LOW))
        }
        fun action(action: String, request: Int) = PendingIntent.getService(
            this, request, Intent(this, ScreenRecorderService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, SettingsActivity::class.java).apply {
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_SCREEN_RECORDER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_glyph_screen_recorder)
            .setContentTitle(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (chronometer) {
            builder.setUsesChronometer(true).setWhen(System.currentTimeMillis() - ScreenRecorderState.state.value.elapsedMs)
        }
        if (actions) {
            builder.addAction(0, getString(if (paused) R.string.voice_resume else R.string.voice_pause), action(ACTION_TOGGLE_PAUSE, 1))
            builder.addAction(0, getString(R.string.voice_stop), action(ACTION_STOP, 2))
        }
        return builder.build()
    }

    override fun onDestroy() {
        if (ScreenRecorderState.state.value.status != ScreenRecStatus.Idle) {
            runCatching { recorder?.stop() }
            cleanup(deleteOutput = false)
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "screen_recorder"
        private const val NOTIFICATION_ID = 7420
        const val ACTION_START = "de.mm20.launcher2.action.SCREENREC_START"
        const val ACTION_STOP = "de.mm20.launcher2.action.SCREENREC_STOP"
        const val ACTION_TOGGLE_PAUSE = "de.mm20.launcher2.action.SCREENREC_TOGGLE_PAUSE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ScreenRecorderService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_DATA, data),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ScreenRecorderService::class.java).setAction(ACTION_STOP))
        }

        fun togglePause(context: Context) {
            context.startService(Intent(context, ScreenRecorderService::class.java).setAction(ACTION_TOGGLE_PAUSE))
        }
    }
}
