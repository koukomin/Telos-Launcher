package de.mm20.launcher2.ui.system

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import de.mm20.launcher2.ui.theme.LauncherTheme
import de.mm20.launcher2.ui.component.VirtualAppWindow

/** Internal notification target; AppOps remains part of this APK and uses the Telos theme. */
class AppOpsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme { VirtualAppWindow {
                if (android.os.Build.VERSION.SDK_INT >= 35) dev.izumi.appopsnext.EmbeddedAppOpsScreen(
                    reportPackage = intent.getStringExtra(dev.izumi.appopsnext.newapps.NewAppPolicyNotifier.EXTRA_PACKAGE),
                    installedAt = intent.getLongExtra(dev.izumi.appopsnext.newapps.NewAppPolicyNotifier.EXTRA_INSTALL_TIME, -1)
                )
            } }
        }
    }
}
