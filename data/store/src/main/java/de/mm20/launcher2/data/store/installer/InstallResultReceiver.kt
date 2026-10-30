package de.mm20.launcher2.data.store.installer

import de.mm20.launcher2.base.containedScope
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
import de.mm20.launcher2.appmanagement.art.TelosArtOptimizer
import de.mm20.launcher2.preferences.ui.UiSettings
import kotlinx.coroutines.flow.first

class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // the system wants the user to confirm: show its dialog
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirm != null) {
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { Log.w(TAG, "Could not show the install confirmation", it) }
            }
            return
        }

        // onReceive must return quickly and the DB write is suspending - goAsync() keeps the
        // receiver (and the process) alive long enough for it to finish.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        containedScope(Dispatchers.IO).launch {
            try {
                val freeze = org.koin.core.context.GlobalContext.get().get<de.mm20.launcher2.freeze.FreezeManager>()
                val token = intent.getStringExtra(EXTRA_FREEZE_TOKEN)
                if (token != null && !freeze.finishFrozenUpdate(packageName, token))
                    Log.w(TAG, "Freeze restoration pending for $packageName")
                de.mm20.launcher2.store.installer.InstallEvents.report(
                    de.mm20.launcher2.store.installer.InstallEvent(packageName, status == PackageInstaller.STATUS_SUCCESS,
                        intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty().take(8192)))
                if (status != PackageInstaller.STATUS_SUCCESS) {
                    Log.w(TAG, "Install failed for $packageName, status=$status")
                    return@launch
                }
                val versionCode = resolveInstalledVersionCode(appContext, packageName)
                AppDatabase.getInstance(appContext).storeItemDao()
                    .updateInstalledVersion(packageName, versionCode)

                // When Telos Launcher updates itself, automatically relaunch the main Home Screen
                if (packageName == appContext.packageName) {
                    Log.i(TAG, "Telos Launcher self-update completed! Launching Home Screen...")
                    val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    }
                    if (launchIntent != null) {
                        appContext.startActivity(launchIntent)
                    }
                }

                val uiSettings = runCatching { org.koin.core.context.GlobalContext.get().get<UiSettings>() }.getOrNull()
                if (uiSettings != null && uiSettings.artOptimizationStorePostInstall.first()) {
                    TelosArtOptimizer.optimizePackage(appContext, packageName, profile = TelosArtOptimizer.PROFILE_SPEED)
                }
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
        const val EXTRA_FREEZE_TOKEN = "de.mm20.launcher2.store.EXTRA_FREEZE_TOKEN"
        const val EXTRA_PACKAGE_NAME = "de.mm20.launcher2.store.EXTRA_PACKAGE_NAME"
    }
}
