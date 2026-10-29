package de.mm20.launcher2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import de.mm20.launcher2.ui.launcher.SharedLauncherActivity

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        Log.i("PackageReplacedReceiver", "Telos Launcher updated! Checking default launcher status...")

        try {
            // Check if Telos is set as the default HOME launcher
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val defaultHome = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val isDefaultLauncher = defaultHome?.activityInfo?.packageName == context.packageName

            if (isDefaultLauncher) {
                Log.i("PackageReplacedReceiver", "Telos Launcher is default HOME! Relaunching main activity...")
                val launchIntent = Intent(context, SharedLauncherActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                context.startActivity(launchIntent)
            }

            // Trigger background self-optimization (ART speed compile) if Shizuku/root is available
            de.mm20.launcher2.art.TelosSelfOptimizer.checkAndSelfCompileAsync(
                context = context,
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()),
            )
        } catch (e: Exception) {
            Log.e("PackageReplacedReceiver", "Failed to relaunch Telos Launcher post-update", e)
        }
    }
}
