package de.mm20.launcher2.profiles

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * App Cloner & Parallel Workspace Manager for Telos Launcher.
 * Manages isolated sandbox directories for cloned app instances (e.g. dual WhatsApp, dual Messenger).
 */
object TelosAppClonerEngine {

    private const val TAG = "TelosAppClonerEngine"

    data class ClonedAppEntry(
        val originalPackageName: String,
        val cloneId: Int = 1,
        val appLabel: String,
        val sandboxDir: String
    ) {
        val cloneKey: String
            get() = "clone_${originalPackageName}_$cloneId"
    }

    private val _clonedApps = MutableStateFlow<List<ClonedAppEntry>>(emptyList())
    val clonedApps: StateFlow<List<ClonedAppEntry>> = _clonedApps.asStateFlow()

    fun createClonedAppSandbox(context: Context, packageName: String, appLabel: String): ClonedAppEntry? {
        val sandboxParent = File(context.applicationContext.filesDir, "cloned_apps_sandbox").also { it.mkdirs() }
        val cloneDir = File(sandboxParent, "${packageName}_clone_1").also { it.mkdirs() }

        val entry = ClonedAppEntry(
            originalPackageName = packageName,
            cloneId = 1,
            appLabel = appLabel,
            sandboxDir = cloneDir.absolutePath
        )

        val updated = (_clonedApps.value.filter { it.cloneKey != entry.cloneKey } + entry)
        _clonedApps.value = updated
        Log.i(TAG, "Created cloned app sandbox for $packageName at ${cloneDir.absolutePath}")
        return entry
    }

    fun removeClonedApp(context: Context, cloneKey: String) {
        val entry = _clonedApps.value.find { it.cloneKey == cloneKey } ?: return
        runCatching {
            File(entry.sandboxDir).deleteRecursively()
        }
        _clonedApps.value = _clonedApps.value.filter { it.cloneKey != cloneKey }
        Log.i(TAG, "Removed cloned app sandbox $cloneKey")
    }
}
