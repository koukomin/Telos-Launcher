package de.mm20.launcher2.ui.applock

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.net.toUri
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.SettingsLockMethod
import de.mm20.launcher2.preferences.applock.AppLockSettings
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.theme.LauncherTheme
import de.mm20.launcher2.webapp.WebAppLaunchContract
import de.mm20.launcher2.webapp.WebAppLockLaunchContract
import de.mm20.launcher2.webappshortcuts.CustomTabsBrowsers
import org.koin.android.ext.android.inject

/**
 * The biometric prompt for a locked web app shortcut, redirected into from
 * [de.mm20.launcher2.webappshortcuts.WebAppShortcutImpl.launch] (see [WebAppLockLaunchContract]).
 * Unlike [AppLockActivity], this is reached directly from the user's own tap on the shortcut (in
 * our own foreground Activity) rather than a background foreground-app watcher, so there's no
 * BAL restriction to work around and no overlay needed - a plain Activity launch is enough.
 *
 * On success, performs the same open-shortcut dispatch [de.mm20.launcher2.webappshortcuts.WebAppShortcutImpl.launch]
 * would have (Custom Tabs if a working renderer package was set, the embedded WebView activity
 * otherwise) - duplicated here rather than reusing that private logic, since this activity only
 * has the shortcut's plain launch parameters (url/label/renderer), not the shortcut object
 * itself, once they've crossed the intent boundary.
 */
class WebAppLockActivity : BaseActivity() {

    private val appLockSettings: AppLockSettings by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val url = intent.getStringExtra(WebAppLockLaunchContract.EXTRA_URL)
        val label = intent.getStringExtra(WebAppLockLaunchContract.EXTRA_LABEL)
        if (url == null || label == null) {
            finish()
            return
        }
        val rendererPackage = intent.getStringExtra(WebAppLockLaunchContract.EXTRA_RENDERER_PACKAGE)

        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    val lockMethod by remember { appLockSettings.lockMethod }
                        .collectAsState(SettingsLockMethod.DeviceCredential)

                    AppLockGateScreen(
                        appLabel = label,
                        lockMethod = lockMethod,
                        onUnlocked = {
                            openShortcut(url, label, rendererPackage)
                            finish()
                        },
                        onCancelled = {
                            finish()
                        },
                    )
                }
            }
        }
    }

    private fun openShortcut(url: String, label: String, rendererPackage: String?) {
        if (rendererPackage != null &&
            CustomTabsBrowsers.isInstalled(this, rendererPackage) &&
            CustomTabsBrowsers.isCustomTabsSupported(this, rendererPackage)
        ) {
            try {
                val customTabsIntent = CustomTabsIntent.Builder().build()
                customTabsIntent.intent.setPackage(rendererPackage)
                customTabsIntent.launchUrl(this, url.toUri())
                return
            } catch (e: ActivityNotFoundException) {
                // Fall through to the embedded WebView below.
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClassName(packageName, WebAppLaunchContract.ACTIVITY_CLASS_NAME)
            putExtra(WebAppLaunchContract.EXTRA_URL, url)
            putExtra(WebAppLaunchContract.EXTRA_LABEL, label)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        tryStartActivity(intent)
    }
}
