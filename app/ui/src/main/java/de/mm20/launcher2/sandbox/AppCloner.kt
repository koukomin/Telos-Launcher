// === TELOS_PENDING_REVIEW_START: sandbox_cloning_and_bridge ===
package de.mm20.launcher2.sandbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.UserHandle
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import de.mm20.launcher2.search.Application
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AppCloner {

    suspend fun cloneAppToWorkProfile(context: Context, app: Application, workProfileUser: UserHandle): Boolean = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(app.componentName.packageName, PackageManager.GET_META_DATA)
            val apkFile = File(appInfo.sourceDir)
            
            if (!apkFile.exists() || !apkFile.canRead()) {
                Log.e("AppCloner", "APK file not found or not readable: ${apkFile.absolutePath}")
                return@withContext false
            }

            val pfd = ParcelFileDescriptor.open(apkFile, ParcelFileDescriptor.MODE_READ_ONLY)
            
            val bridge = bindToSandboxBridge(context, workProfileUser) ?: run {
                Log.e("AppCloner", "Could not bind to SandboxBridgeService in work profile")
                pfd.close()
                return@withContext false
            }
            
            bridge.installApp(pfd, app.componentName.packageName)
            // pfd is closed by the remote service or when the local reference goes away
            return@withContext true
        } catch (e: Exception) {
            Log.e("AppCloner", "Error cloning app", e)
            return@withContext false
        }
    }

    private suspend fun bindToSandboxBridge(context: Context, userHandle: UserHandle): ISandboxBridge? = suspendCancellableCoroutine { cont ->
        val intent = Intent().apply {
            component = ComponentName("com.dimitris.telos", "de.mm20.launcher2.sandbox.SandboxBridgeService")
            // Or component = ComponentName(context, "de.mm20.launcher2.sandbox.SandboxBridgeService")
        }
        intent.setPackage(context.packageName)

        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (cont.isActive) {
                    cont.resume(ISandboxBridge.Stub.asInterface(service))
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // Ignore
            }
            
            override fun onBindingDied(name: ComponentName?) {
                if (cont.isActive) {
                    cont.resume(null)
                }
            }
            
            override fun onNullBinding(name: ComponentName?) {
                if (cont.isActive) {
                    cont.resume(null)
                }
            }
        }

        try {
            val crossProfileApps = context.getSystemService(Context.CROSS_PROFILE_APPS_SERVICE) as android.content.pm.CrossProfileApps
            // There is no easy way to bindServiceAsUser without reflection or SYSTEM privileges.
            // Using reflection for Context.bindServiceAsUser
            val bindServiceAsUser = Context::class.java.getMethod(
                "bindServiceAsUser",
                Intent::class.java,
                ServiceConnection::class.java,
                Int::class.javaPrimitiveType,
                UserHandle::class.java
            )
            val success = bindServiceAsUser.invoke(context, intent, serviceConnection, Context.BIND_AUTO_CREATE, userHandle) as Boolean
            if (!success) {
                if (cont.isActive) cont.resume(null)
            }
        } catch (e: Exception) {
            Log.e("AppCloner", "Failed to bindServiceAsUser", e)
            if (cont.isActive) cont.resume(null)
        }

        cont.invokeOnCancellation {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
// === TELOS_PENDING_REVIEW_END: sandbox_cloning_and_bridge ===
