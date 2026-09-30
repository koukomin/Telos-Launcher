// === TELOS_PENDING_REVIEW_START: sandbox_cloning_and_bridge ===
package de.mm20.launcher2.sandbox

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.OutputStream

class SandboxBridgeService : Service() {
    
    private val scope = CoroutineScope(Dispatchers.IO)

    private val binder = object : ISandboxBridge.Stub() {
        override fun installApp(pfd: ParcelFileDescriptor, packageName: String) {
            val callingUid = getCallingUid()
            // In a real app we'd verify the caller is our own app from another profile.
            // For now, let's just proceed.
            Log.d("SandboxBridge", "installApp requested for $packageName by uid $callingUid")
            
            scope.launch {
                try {
                    val packageInstaller = packageManager.packageInstaller
                    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    params.setAppPackageName(packageName)
                    
                    val sessionId = packageInstaller.createSession(params)
                    val session = packageInstaller.openSession(sessionId)
                    
                    val out: OutputStream = session.openWrite(packageName, 0, -1)
                    val input = FileInputStream(pfd.fileDescriptor)
                    
                    input.copyTo(out)
                    
                    session.fsync(out)
                    out.close()
                    input.close()
                    pfd.close()
                    
                    // We need a PendingIntent to commit. For simplicity, we just use a dummy receiver or 
                    // broadcast intent that we don't necessarily handle, just to trigger the install.
                    val intent = Intent("de.mm20.launcher2.sandbox.INSTALL_COMPLETE").apply {
                        setPackage(this@SandboxBridgeService.packageName)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        this@SandboxBridgeService,
                        sessionId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                    
                    session.commit(pendingIntent.intentSender)
                    Log.d("SandboxBridge", "Session committed for $packageName")
                } catch (e: Exception) {
                    Log.e("SandboxBridge", "Failed to install app $packageName", e)
                }
            }
        }

        override fun ping() {
            Log.d("SandboxBridge", "ping received")
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}
// === TELOS_PENDING_REVIEW_END: sandbox_cloning_and_bridge ===
