package de.mm20.launcher2.data.store.installer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Installs an APK through the standard, unprivileged [PackageInstaller] session API. This always
 * shows the system's install confirmation UI (there is no way around that without Shizuku/root,
 * see [ShizukuInstallBackend]/[RootInstallBackend]) - it's the universal fallback that works on
 * every device regardless of privilege source.
 */
internal class SessionApiInstallBackend(private val context: Context) : InstallBackend {

    override suspend fun isAvailable(): Boolean = true
    override suspend fun hasPermission(): Boolean = true
    override suspend fun requestPermission(): Boolean = true

    override suspend fun install(apk: File, packageName: String): BackendInstallResult {
        return try {
            FileInputStream(apk).use { install(it, packageName, apk.length()) }
        } catch (e: Exception) {
            Log.w(TAG, "Session install failed for $packageName", e)
            BackendInstallResult.Failed("Could not read APK file: ${e.message}", e)
        }
    }

    /**
     * Installs from an already-open [InputStream] (e.g. a download that was streamed straight
     * into the installer session rather than written to a file first). [sizeBytes], if known,
     * lets [PackageInstaller.Session] preallocate instead of growing the backing file on demand.
     */
    suspend fun install(
        apkStream: InputStream,
        packageName: String,
        sizeBytes: Long = -1,
    ): BackendInstallResult {
        val packageInstaller = context.packageManager.packageInstaller
        val sessionId = try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(packageName)
                if (isAtLeastApiLevel(Build.VERSION_CODES.O)) {
                    setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
                }
            }
            packageInstaller.createSession(params)
        } catch (e: Exception) {
            return BackendInstallResult.Failed("Could not create install session: ${e.message}", e)
        }

        return try {
            val session = packageInstaller.openSession(sessionId)
            session.use { s ->
                writeApkIntoSession(s, apkStream, sizeBytes)
                commitSession(s, sessionId, packageName)
            }
        } catch (e: Exception) {
            packageInstaller.abandonSession(sessionId)
            Log.w(TAG, "Session install failed for $packageName", e)
            BackendInstallResult.Failed("Install session failed: ${e.message}", e)
        }
    }

    private fun writeApkIntoSession(
        session: PackageInstaller.Session,
        apkStream: InputStream,
        sizeBytes: Long,
    ) {
        val out: OutputStream = session.openWrite("telos_store_$sessionWriteName", 0, sizeBytes)
        out.use { output ->
            apkStream.copyTo(output)
            session.fsync(output)
        }
    }

    /**
     * Commits the session and suspends until the system reports success/failure via the
     * broadcast receiver registered on [SESSION_ACTION] - [PackageInstaller.Session.commit]
     * itself is fire-and-forget, the actual result only arrives through that intent.
     */
    private suspend fun commitSession(
        session: PackageInstaller.Session,
        sessionId: Int,
        packageName: String,
    ): BackendInstallResult = suspendCancellableCoroutine { cont ->
        val action = "$SESSION_ACTION.$sessionId"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != action) return
                context.unregisterReceiver(this)
                val status = intent.getIntExtra(
                    PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE,
                )
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                val result = if (status == PackageInstaller.STATUS_SUCCESS) {
                    BackendInstallResult.Success
                } else {
                    BackendInstallResult.Failed(message ?: "Install failed with status $status")
                }
                if (cont.isActive) cont.resumeWith(Result.success(result))
            }
        }
        val filter = IntentFilter(action)
        if (isAtLeastApiLevel(Build.VERSION_CODES.TIRAMISU)) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        cont.invokeOnCancellation {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // Already unregistered by onReceive - fine.
            }
        }

        val flags = if (isAtLeastApiLevel(Build.VERSION_CODES.S)) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(action).setPackage(context.packageName),
            flags,
        )
        session.commit(pendingIntent.intentSender)
    }

    // Monotonic per-call name suffix so concurrent installs don't collide on the same session
    // write name; the session itself already scopes writes, this is just defensive.
    private val sessionWriteName: String
        get() = System.nanoTime().toString()

    companion object {
        private const val TAG = "SessionApiInstallBackend"
        private const val SESSION_ACTION = "de.mm20.launcher2.store.INSTALL_SESSION_STATUS"
    }
}
