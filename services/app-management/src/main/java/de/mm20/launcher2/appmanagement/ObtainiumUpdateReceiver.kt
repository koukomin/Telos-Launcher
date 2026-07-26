package de.mm20.launcher2.appmanagement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Receiver that listens for update available broadcasts from Obtainium.
 * Also listens for package replacement to clear update state.
 */
class ObtainiumUpdateReceiver : BroadcastReceiver(), KoinComponent {

    private val repository: FossUpdateRepository by inject()

    companion object {
        private const val TAG = "ObtainiumReceiver"
        const val ACTION_UPDATE_AVAILABLE = "com.itachi1706.obtainium.ACTION_UPDATE_AVAILABLE"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_UPDATE_AVAILABLE -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                if (packageName != null) {
                    Log.d(TAG, "Update available for: $packageName")
                    repository.addUpdate(packageName)
                }
            }
            Intent.ACTION_PACKAGE_REPLACED -> {
                val packageName = intent.data?.schemeSpecificPart
                if (packageName != null) {
                    Log.d(TAG, "Package replaced: $packageName, clearing update state")
                    repository.removeUpdate(packageName)
                }
            }
        }
    }
}
