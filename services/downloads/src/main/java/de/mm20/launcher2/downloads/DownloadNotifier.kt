package de.mm20.launcher2.downloads

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.downloads.logic.BlockReason
import de.mm20.launcher2.downloads.logic.Formatting
import de.mm20.launcher2.i18n.R as I18nR

/** Builds the notifications of Telos Downloads: one summary (the foreground notification), one per active download, and results. */
class DownloadNotifier(private val context: Context, private val settings: DownloadSettings) {

    private val nm get() = context.getSystemService(NotificationManager::class.java)

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (nm.getNotificationChannel(CHANNEL_PROGRESS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, context.getString(I18nR.string.dl_channel_progress), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }
        if (nm.getNotificationChannel(CHANNEL_DONE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, context.getString(I18nR.string.dl_channel_done), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun canPost() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent().setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME).apply {
            putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_DOWNLOADS)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceAction(action: String, taskId: String?, request: Int): PendingIntent {
        val intent = Intent(context, DownloadService::class.java).setAction(action)
        if (taskId != null) intent.putExtra(DownloadService.EXTRA_TASK_ID, taskId)
        return PendingIntent.getForegroundService(context, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** The notification the service runs in the foreground with, summing up all downloads */
    fun summary(tasks: List<DownloadTask>, blocked: BlockReason?): Notification {
        ensureChannels()
        val active = tasks.filter { it.state.isActive }
        val queued = tasks.count { it.state == DownloadState.Queued }
        val speed = active.sumOf { it.speedBps }
        val known = active.filter { it.totalBytes > 0 }
        val total = known.sumOf { it.totalBytes }
        val done = known.sumOf { it.downloadedBytes }
        val title = when {
            blocked != null -> context.getString(I18nR.string.dl_notification_waiting)
            active.isEmpty() -> context.getString(I18nR.string.dl_notification_preparing)
            else -> context.resources.getQuantityString(I18nR.plurals.dl_notification_downloading, active.size, active.size)
        }
        val text = when (blocked) {
            BlockReason.Offline -> context.getString(I18nR.string.dl_block_offline)
            BlockReason.WifiOnly -> context.getString(I18nR.string.dl_block_wifi)
            BlockReason.LowBattery -> context.getString(I18nR.string.dl_block_battery)
            BlockReason.Schedule -> context.getString(I18nR.string.dl_p3_block_schedule)
            null -> buildString {
                if (speed > 0) append(Formatting.speed(speed))
                if (queued > 0) { if (isNotEmpty()) append(" · "); append(context.getString(I18nR.string.dl_notification_queued, queued)) }
            }
        }
        val b = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(BaseR.drawable.ic_glyph_downloads)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .addAction(0, context.getString(I18nR.string.dl_pause_all), serviceAction(DownloadService.ACTION_PAUSE_ALL, null, 1))
        if (total > 0) b.setProgress(1000, (done * 1000 / total).toInt().coerceIn(0, 1000), false)
        else if (active.isNotEmpty()) b.setProgress(0, 0, true)
        return b.build()
    }

    private val shown = HashSet<Int>()

    private fun notificationId(taskId: String) = 20000 + (taskId.hashCode() and 0x7FFF)

    /** One notification for each of the first few active downloads, with pause and cancel */
    fun updateTasks(tasks: List<DownloadTask>) {
        if (!settings.current.notifications || !canPost()) return
        ensureChannels()
        val active = tasks.filter { it.state.isActive }.take(MAX_TASK_NOTIFICATIONS)
        val ids = HashSet<Int>()
        for ((i, t) in active.withIndex()) {
            val id = notificationId(t.id)
            ids.add(id)
            val eta = t.etaSeconds?.let { " · " + Formatting.duration(it) }.orEmpty()
            val seeding = t.state == DownloadState.Seeding && t.torrent != null
            val text = if (seeding) {
                context.getString(I18nR.string.dl_t_notification_seeding, de.mm20.launcher2.downloads.logic.SeedRules.formatRatio(t.torrent!!.ratio), Formatting.speed(t.torrent.uploadBps))
            } else if (t.totalBytes > 0) {
                "${Formatting.size(t.downloadedBytes)} / ${Formatting.size(t.totalBytes)}" + (if (t.speedBps > 0) " · " + Formatting.speed(t.speedBps) else "") + eta
            } else Formatting.size(t.downloadedBytes)
            val b = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
                .setSmallIcon(BaseR.drawable.ic_glyph_downloads)
                .setContentTitle(t.displayName)
                .setContentText(text)
                .setContentIntent(openApp())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setGroup(GROUP)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .addAction(0, context.getString(I18nR.string.dl_pause), serviceAction(DownloadService.ACTION_PAUSE, t.id, 100 + i))
                .addAction(0, context.getString(I18nR.string.dl_cancel), serviceAction(DownloadService.ACTION_CANCEL, t.id, 200 + i))
            if (seeding) b.setProgress(0, 0, false)
            else if (t.totalBytes > 0) b.setProgress(1000, (t.downloadedBytes * 1000 / t.totalBytes).toInt().coerceIn(0, 1000), false)
            else b.setProgress(0, 0, true)
            nm.notify(id, b.build())
        }
        for (gone in shown - ids) nm.cancel(gone)
        shown.clear(); shown.addAll(ids)
    }

    /** Removes the finished or failed notification of a task (it is retried or removed) */
    fun clearResult(taskId: String) {
        nm.cancel(notificationId(taskId) + RESULT_OFFSET)
    }

    fun clearTaskNotifications() {
        for (id in shown) nm.cancel(id)
        shown.clear()
    }

    fun notifyCompleted(task: DownloadTask) {
        if (!settings.current.notifications || !canPost()) return
        ensureChannels()
        nm.cancel(notificationId(task.id))
        val uri = task.fileUri?.let(Uri::parse)
        val b = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(BaseR.drawable.ic_glyph_downloads)
            .setContentTitle(task.displayName)
            .setContentText(context.getString(I18nR.string.dl_notification_done, Formatting.size(task.totalBytes)))
            .setAutoCancel(true)
            .setContentIntent(openApp())
        if (uri != null && uri.scheme == "content" && task.torrent == null) {
            val mime = task.mimeType ?: "*/*"
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            val send = Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                null,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            b.addAction(0, context.getString(I18nR.string.dl_open), PendingIntent.getActivity(context, notificationId(task.id), view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            b.addAction(0, context.getString(I18nR.string.dl_share), PendingIntent.getActivity(context, notificationId(task.id) + 1, send, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        nm.notify(notificationId(task.id) + RESULT_OFFSET, b.build())
    }

    fun notifyFailed(task: DownloadTask) {
        if (!settings.current.notifications || !canPost()) return
        ensureChannels()
        nm.cancel(notificationId(task.id))
        nm.notify(
            notificationId(task.id) + RESULT_OFFSET,
            NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(BaseR.drawable.ic_glyph_downloads)
                .setContentTitle(task.displayName)
                .setContentText(context.getString(I18nR.string.dl_notification_failed, task.error.orEmpty()))
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .addAction(0, context.getString(I18nR.string.dl_retry), serviceAction(DownloadService.ACTION_RESUME, task.id, notificationId(task.id) + RESULT_OFFSET))
                .build()
        )
    }

    companion object {
        const val CHANNEL_PROGRESS = "downloads_progress"
        const val CHANNEL_DONE = "downloads_done"
        const val SUMMARY_ID = 7420
        private const val GROUP = "de.mm20.launcher2.downloads"
        private const val MAX_TASK_NOTIFICATIONS = 5
        private const val RESULT_OFFSET = 0x10000
    }
}
