package dev.izumi.appopsnext.shizuku.process

import de.mm20.launcher2.ktx.ShizukuProcesses

internal class ShizukuRemoteProcessLauncher {
    fun launch(arguments: List<String>, environment: Map<String, String> = emptyMap(), workingDirectory: String? = null): RemoteProcessHandle =
        RemoteProcessHandle(ShizukuProcesses.start(arguments.toTypedArray(), environment.map { (k, v) -> "$k=$v" }.toTypedArray().takeIf { it.isNotEmpty() }, workingDirectory))
}
