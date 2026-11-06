/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.mm20.launcher2.appmanagement.startup

import android.content.Context
import de.mm20.launcher2.appmanagement.shizuku.TelosShizukuHost
import de.mm20.launcher2.appmanagement.shizuku.TelosShizukuWatchdog
import de.mm20.launcher2.preferences.ui.UiSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.util.concurrent.atomic.AtomicBoolean

object LauncherStartup {
    private val once = AtomicBoolean()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun onForeground(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val auto = runCatching {
                GlobalContext.get().get<UiSettings>().shizukuAutoStart.first()
            }.getOrDefault(false)
            // Each foreground entry can recover a service start denied while backgrounded.
            // Do this before the bounded server start; that attempt may take many seconds.
            if (auto) TelosShizukuWatchdog.start(app)
            if (!once.compareAndSet(false, true)) return@launch
            if (auto) runCatching {
                // Existing paired TLS identity + own-device mDNS; no pairing UI or port scan.
                TelosShizukuHost.start(app)
            }
            StartupScripts.onLauncherStarted(app)
        }
    }
}
