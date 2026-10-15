package de.mm20.launcher2.data.store.installer

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import java.io.File

internal const val APK_MIME = "application/vnd.android.package-archive"

internal fun externalInstallerActivities(context: Context) = context.packageManager.queryIntentActivities(
    Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://${context.packageName}.fileprovider/cache/store.apk"), APK_MIME),
    PackageManager.MATCH_DEFAULT_ONLY,
).filter { it.activityInfo.exported && it.activityInfo.packageName != context.packageName }

/** Owns the URI grant and reconciles the actual package version when an external installer returns. */
class ExternalInstallActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        try {
            val file = File(requireNotNull(intent.getStringExtra("file")))
            require(file.canonicalFile.parentFile == File(cacheDir, "store-handoff").canonicalFile)
            val component = requireNotNull(ComponentName.unflattenFromString(requireNotNull(intent.getStringExtra("component"))))
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivityForResult(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                this.component = component
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("APK", uri)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }, 1)
        } catch (error: Exception) { finishWithResult() }
    }

    @Deprecated("Legacy result is required by third-party APK handlers")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) finishWithResult()
    }

    private fun finishWithResult() {
        val target = intent.getStringExtra(InstallResultReceiver.EXTRA_PACKAGE_NAME)
        val expected = intent.getLongExtra("version", Long.MAX_VALUE)
        val installed = target?.let { resolveInstalledVersionCode(this, it) }
        sendBroadcast(Intent(this, InstallResultReceiver::class.java).apply {
            action = InstallResultReceiver.ACTION_INSTALL_STATUS
            putExtra(InstallResultReceiver.EXTRA_PACKAGE_NAME, target)
            putExtra(InstallResultReceiver.EXTRA_FREEZE_TOKEN, intent.getStringExtra(InstallResultReceiver.EXTRA_FREEZE_TOKEN))
            putExtra(PackageInstaller.EXTRA_STATUS, if (installed != null && installed >= expected) PackageInstaller.STATUS_SUCCESS else PackageInstaller.STATUS_FAILURE_ABORTED)
        })
        intent.getStringExtra("file")?.let { path ->
            val file = File(path)
            if (file.canonicalFile.parentFile == File(cacheDir, "store-handoff").canonicalFile) file.delete()
        }
        finish()
    }
}
