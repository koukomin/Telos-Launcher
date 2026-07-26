package de.mm20.launcher2.ui.applock

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.addCallback
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import de.mm20.launcher2.applock.AppLockManager
import de.mm20.launcher2.preferences.SettingsLockMethod
import de.mm20.launcher2.preferences.applock.AppLockSettings
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.theme.LauncherTheme
import org.koin.android.ext.android.inject

/**
 * The actual biometric prompt, launched only from a direct tap on [AppLockOverlayService]'s
 * "Unlock" button - never straight from the background monitor, since Android blocks that (see
 * [AppLockOverlayService]'s doc for why). A standalone task (own affinity, excluded from recents)
 * rather than something embedded in the locked app's own task - finishing it after a successful
 * unlock simply reveals whatever was already behind it (the overlay, which then hides itself once
 * [de.mm20.launcher2.applock.AppLockManager.pendingLock] clears, revealing the locked app itself).
 */
class AppLockActivity : BaseActivity() {

    private val appLockManager: AppLockManager by inject()
    private val appLockSettings: AppLockSettings by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val instant = intent.getBooleanExtra(EXTRA_INSTANT, false)
        if (packageName == null) {
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this) {
            appLockManager.dismiss()
            goHome()
            finish()
        }

        val appLabel = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }

        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    val lockMethod by remember { appLockSettings.lockMethod }
                        .collectAsState(SettingsLockMethod.DeviceCredential)

                    AppLockGateScreen(
                        appLabel = appLabel,
                        lockMethod = lockMethod,
                        instant = instant,
                        onUnlocked = {
                            appLockManager.reportUnlocked(packageName)
                            finish()
                        },
                        onCancelled = {
                            appLockManager.dismiss()
                            goHome()
                            finish()
                        },
                    )
                }
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "de.mm20.launcher2.applock.PACKAGE_NAME"
        const val EXTRA_INSTANT = "de.mm20.launcher2.applock.INSTANT"
    }
}
