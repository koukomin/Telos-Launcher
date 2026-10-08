package de.mm20.launcher2.ui.screenshot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationCompat
import de.mm20.launcher2.globalactions.GlobalActionsService
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Takes the screenshots of Telos Screenshot through the accessibility service: a full
 * screenshot, a partial one (a region you choose) and a scrolling one (experimental). The picture
 * comes from the service (Android 11+), so Telos can edit it before it is saved.
 */
object ScreenshotController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var scrolling = false

    private fun actions(): GlobalActionsService = GlobalContext.get().get()

    /** Takes a screenshot of what is on screen now and saves it */
    fun captureFull(context: Context, afterDelay: Boolean = true) {
        val app = context.applicationContext
        scope.launch {
            waitBeforeCapture(app, afterDelay)
            val bitmap = grab()
            if (bitmap == null) {
                // below Android 11, or the service cannot give the picture: the system takes it
                if (!actions().takeScreenshot()) toast(app, R.string.screenshot_needs_accessibility)
                return@launch
            }
            saveOffMainThread(app, bitmap)
        }
    }

    /** Takes a screenshot and opens it so that you can choose a part of it */
    fun capturePartial(context: Context) {
        val app = context.applicationContext
        scope.launch {
            waitBeforeCapture(app, true)
            val bitmap = grab()
            if (bitmap == null) {
                toast(app, R.string.screenshot_needs_android11)
                return@launch
            }
            val file = withContext(Dispatchers.IO) {
                File(app.cacheDir, "screenshot_pending.png").also { f -> f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }
            app.startActivity(
                Intent(app, ScreenshotEditorActivity::class.java)
                    .putExtra(ScreenshotEditorActivity.EXTRA_PATH, file.absolutePath)
                    .putExtra(ScreenshotEditorActivity.EXTRA_PARTIAL, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * A screenshot of more than one screen: scrolls the content with a swipe, takes a picture
     * after each swipe and joins them. Stops at the end of the content or after 12 screens.
     */
    fun captureScrolling(context: Context) {
        val app = context.applicationContext
        if (scrolling) return
        scrolling = true
        scope.launch {
            try {
                waitBeforeCapture(app, true)
                val first = grab()
                if (first == null) {
                    toast(app, R.string.screenshot_needs_android11)
                    return@launch
                }
                toast(app, R.string.screenshot_scrolling_started)
                val metrics = app.resources.displayMetrics
                // the bars at the top and the bottom are the same in every frame, they are kept once
                val top = statusBarHeight(app)
                val bottom = navigationBarHeight(app)
                val frames = mutableListOf(first)
                var previous: Bitmap = first
                while (frames.size < MAX_SCREENS) {
                    val swiped = suspendCancellableCoroutine<Boolean> { c -> actions().swipeUp(0.55f) { c.resume(it) } }
                    if (!swiped) break
                    delay(700)
                    val next = grab() ?: break
                    if (sameContent(previous, next)) break
                    frames += next
                    previous = next
                }
                if (frames.size == 1) {
                    saveOffMainThread(app, first)
                } else {
                    val stitched = try {
                        withContext(Dispatchers.Default) { stitch(frames, top, bottom) }
                    } catch (e: OutOfMemoryError) {
                        // a very long page: keep at least the first screen instead of crashing the app
                        first
                    }
                    saveOffMainThread(app, stitched)
                    if (stitched !== first) stitched.recycle()
                    frames.forEach { if (it !== stitched && !it.isRecycled) it.recycle() }
                }
            } finally {
                scrolling = false
            }
        }
    }

    private suspend fun waitBeforeCapture(context: Context, afterDelay: Boolean) {
        val seconds = if (afterDelay) ScreenshotSettings(context).delay else 0
        // time for the sidebar or the app that started the capture to leave the screen
        delay(if (seconds > 0) seconds * 1000L else 700L)
    }

    private suspend fun grab(): Bitmap? = suspendCancellableCoroutine { c -> actions().takeScreenshotBitmap { c.resume(it) } }

    /** Compressing a full screen PNG takes hundreds of milliseconds: not on the main thread */
    private suspend fun saveOffMainThread(context: Context, bitmap: Bitmap) {
        val uri = withContext(Dispatchers.IO) { ScreenshotStore.save(context, bitmap) }
        afterSave(context, uri, bitmap)
    }

    fun saveAndNotify(context: Context, bitmap: Bitmap) = afterSave(context, ScreenshotStore.save(context, bitmap), bitmap)

    private fun afterSave(context: Context, uri: Uri?, bitmap: Bitmap) {
        if (uri == null) {
            toast(context, R.string.screenshot_save_failed)
            return
        }
        if (ScreenshotSettings(context).notify && context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) notifySaved(context, uri, bitmap)
        else toast(context, R.string.screenshot_saved)
    }

    private fun notifySaved(context: Context, uri: Uri, bitmap: Bitmap) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.screenshot_notification_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val id = (System.currentTimeMillis() % 100000).toInt() + 8000
        val edit = PendingIntent.getActivity(
            context, id,
            Intent(context, ScreenshotEditorActivity::class.java).putExtra(ScreenshotEditorActivity.EXTRA_URI, uri.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val share = PendingIntent.getActivity(
            context, id + 100000,
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, shareableUri(context, uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                null,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val delete = PendingIntent.getBroadcast(
            context, id + 200000,
            Intent(context, ScreenshotActionReceiver::class.java).setAction(ScreenshotActionReceiver.ACTION_DELETE)
                .putExtra(ScreenshotActionReceiver.EXTRA_URI, uri.toString()).putExtra(ScreenshotActionReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val thumb = Bitmap.createScaledBitmap(bitmap, 640, (640f * bitmap.height / bitmap.width).toInt().coerceAtLeast(1), true)
        nm.notify(
            id,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sidebar_screenshot)
                .setContentTitle(context.getString(R.string.screenshot_saved))
                .setContentText(context.getString(R.string.screenshot_notification_text))
                .setStyle(NotificationCompat.BigPictureStyle().bigPicture(thumb))
                .setContentIntent(edit)
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.voice_share), share)
                .addAction(0, context.getString(R.string.screenshot_edit), edit)
                .addAction(0, context.getString(R.string.voice_delete), delete)
                .build()
        )
    }

    /** A file:// URI (Android 9 and below) must not leave the app: it is shared through the FileProvider */
    private fun shareableUri(context: Context, uri: Uri): Uri {
        if (uri.scheme != "file") return uri
        val path = uri.path ?: return uri
        return try {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        } catch (e: Exception) {
            uri
        }
    }

    private fun toast(context: Context, text: Int) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private fun navigationBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    /** True if the screen did not move between the two pictures (the end of the content) */
    private fun sameContent(a: Bitmap, b: Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        val y = a.height / 2
        for (x in 0 until a.width step 7) if (a.getPixel(x, y) != b.getPixel(x, y)) return false
        for (x in 0 until a.width step 7) if (a.getPixel(x, y / 2) != b.getPixel(x, y / 2)) return false
        return true
    }

    /**
     * Joins the pictures: every picture after the first is placed where its first rows equal the
     * last rows of what is already there (found by comparing rows).
     */
    internal fun stitch(frames: List<Bitmap>, top: Int, bottom: Int): Bitmap {
        val width = frames[0].width
        val height = frames[0].height
        val bodyTop = top.coerceIn(0, height / 4)
        val bodyBottom = (height - bottom).coerceIn(height * 3 / 4, height)
        // the rows of the result, each as a bitmap strip that is added to
        var result = Bitmap.createBitmap(frames[0], 0, 0, width, bodyBottom)
        for (index in 1 until frames.size) {
            val next = frames[index]
            val overlap = findOverlap(result, next, bodyTop, bodyBottom)
            val newTop = bodyTop + overlap
            val addHeight = bodyBottom - newTop
            if (addHeight <= 0) continue
            val combined = Bitmap.createBitmap(width, result.height + addHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(combined)
            canvas.drawBitmap(result, 0f, 0f, null)
            val strip = Bitmap.createBitmap(next, 0, newTop, width, addHeight)
            canvas.drawBitmap(strip, 0f, result.height.toFloat(), null)
            if (strip !== next) strip.recycle()
            // the previous result is garbage now (frames are owned by the caller)
            if (frames.none { it === result }) result.recycle()
            result = combined
        }
        return result
    }

    /** How many rows at the top of the body of [next] are already at the bottom of [result] */
    private fun findOverlap(result: Bitmap, next: Bitmap, bodyTop: Int, bodyBottom: Int): Int {
        val width = next.width
        val bodyHeight = bodyBottom - bodyTop
        fun rowHash(bitmap: Bitmap, y: Int): Long {
            var h = 1125899906842597L
            var x = 0
            while (x < width) {
                h = 31 * h + bitmap.getPixel(x, y)
                x += 5
            }
            return h
        }
        val resultHashes = LongArray(minOf(result.height, bodyHeight)) { rowHash(result, result.height - it - 1) } // from the bottom
        val nextHashes = LongArray(bodyHeight) { rowHash(next, bodyTop + it) }
        // the overlap is the largest n for which the first n rows of next equal the last n rows of result
        for (n in minOf(resultHashes.size, bodyHeight) downTo MIN_OVERLAP) {
            var equal = true
            for (i in 0 until n) {
                if (nextHashes[i] != resultHashes[n - 1 - i]) {
                    equal = false
                    break
                }
            }
            if (equal) return n
        }
        // nothing matched (a fixed header or an animation): put the pictures one under the other
        return 0
    }

    private const val CHANNEL_ID = "screenshots"
    private const val MAX_SCREENS = 12
    private const val MIN_OVERLAP = 40
}

class ScreenshotActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DELETE) return
        intent.getStringExtra(EXTRA_URI)?.let { ScreenshotStore.delete(context, Uri.parse(it)) }
        context.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra(EXTRA_ID, 0))
    }

    companion object {
        const val ACTION_DELETE = "de.mm20.launcher2.action.SCREENSHOT_DELETE"
        const val EXTRA_URI = "uri"
        const val EXTRA_ID = "id"
    }
}
