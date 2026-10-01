package de.mm20.launcher2.data.store.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives the final result of a [android.content.pm.PackageInstaller] session (manifest-
 * registered, not tied to any particular caller's coroutine scope) and writes it straight to
 * [de.mm20.launcher2.database.daos.StoreItemDao]. `StoreViewModel` observes Room reactively, so
 * this is what actually unsticks a `StoreInstallUiState.Installing` row once the system installer
 * UI finishes - previously that state had no way to resolve if the screen/process that started
 * the install wasn't still around to observe a one-shot callback.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

        if (status != PackageInstaller.STATUS_SUCCESS) {
            Log.w(
                TAG,
                "Install failed for $packageName: status=$status message=${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}",
            )
            return
        }

        // onReceive must return quickly and the DB write is suspending - goAsync() keeps the
        // receiver (and the process) alive long enough for it to finish.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val versionCode = resolveInstalledVersionCode(appContext, packageName)
                AppDatabase.getInstance(appContext).storeItemDao()
                    .updateInstalledVersion(packageName, versionCode)
            } catch (e: Exception) {
                CrashReporter.logException(e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "InstallResultReceiver"
        const val ACTION_INSTALL_STATUS = "de.mm20.launcher2.store.INSTALL_SESSION_STATUS"
        const val EXTRA_PACKAGE_NAME = "de.mm20.launcher2.store.EXTRA_PACKAGE_NAME"
    }
}
