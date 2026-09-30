// === TELOS_PENDING_REVIEW_START: sandbox_provisioning ===
package de.mm20.launcher2.sandbox

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class SandboxDeviceAdminReceiver : DeviceAdminReceiver() {
    @Suppress("DEPRECATION")
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.d("SandboxDeviceAdmin", "Profile provisioning complete")
        
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, SandboxDeviceAdminReceiver::class.java)

        try {
            dpm.setProfileName(admin, "Telos Sandbox")
            // Make sure the profile is enabled
            dpm.setProfileEnabled(admin)
            
            // Allow cross-profile caller id
            dpm.setCrossProfileCallerIdDisabled(admin, false)
        } catch (e: Exception) {
            Log.e("SandboxDeviceAdmin", "Failed to configure profile", e)
        }
    }
}
// === TELOS_PENDING_REVIEW_END: sandbox_provisioning ===
