package de.mm20.launcher2.freeze.providers

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import de.mm20.launcher2.freeze.IFreezeUserService
import de.mm20.launcher2.freeze.PrivilegedAccessProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Talks to the Shizuku service (running with adb-shell or root privilege, whichever the user set
 * Shizuku up with) via a bound [rikka.shizuku.Shizuku.UserServiceArgs] process that shells out to
 * `pm suspend`/`pm unsuspend`, exactly what `adb shell pm suspend` does.
 *
 * Requires the host app to have `dev.rikka.shizuku:api`/`:provider` and declare
 * `rikka.shizuku.ShizukuProvider` in the manifest (done in this module's manifest, merged in).
 */
internal class ShizukuProvider(
    private val context: Context,
) : PrivilegedAccessProvider {

    companion object {
        private const val REQUEST_CODE = 9316
        private const val TAG = "ShizukuProvider"
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(context.packageName, FreezeUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("freeze")
            .debuggable(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
            .version(runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                PackageInfoCompat.getLongVersionCode(info).toInt()
            }.getOrDefault(1))
    }

    private var boundService: IFreezeUserService? = null
    private var connection: ServiceConnection? = null

    override suspend fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    override suspend fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    override suspend fun requestPermission(): Boolean {
        if (!isAvailable()) return false
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true

        return suspendCancellableCoroutine { cont ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != REQUEST_CODE) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (cont.isActive) {
                        cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                    }
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            cont.invokeOnCancellation {
                Shizuku.removeRequestPermissionResultListener(listener)
            }
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    override suspend fun setPackagesSuspended(packageNames: List<String>, suspended: Boolean): Boolean {
        val service = bindService() ?: return false
        val action = if (suspended) "suspend" else "unsuspend"
        return try {
            packageNames.all { pkg -> service.runShellCommand("pm $action --user 0 $pkg") }
        } catch (e: Throwable) {
            Log.e(TAG, "setPackagesSuspended failed", e)
            boundService = null
            false
        }
    }

    private suspend fun bindService(): IFreezeUserService? {
        boundService?.let { return it }
        if (!hasPermission()) return null

        return suspendCancellableCoroutine { cont ->
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val service = binder?.let { IFreezeUserService.Stub.asInterface(it) }
                    boundService = service
                    if (cont.isActive) cont.resume(service)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    boundService = null
                }
            }
            connection = conn
            try {
                Shizuku.bindUserService(userServiceArgs, conn)
            } catch (e: Throwable) {
                Log.e(TAG, "bindUserService failed", e)
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}
