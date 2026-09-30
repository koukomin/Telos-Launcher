// === TELOS_PENDING_REVIEW_START: sandbox_provisioning ===
package de.mm20.launcher2.sandbox

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.util.Log

object SandboxProvisioner {

    fun startProvisioning(activity: Activity, requestCode: Int) {
        try {
            val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
                putExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                    ComponentName(activity, "de.mm20.launcher2.sandbox.SandboxDeviceAdminReceiver")
                )
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true)
            }
            activity.startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            Log.e("SandboxProvisioner", "Failed to start provisioning", e)
        }
    }
}
// === TELOS_PENDING_REVIEW_END: sandbox_provisioning ===
