package com.telos.desktop.vault

import com.telos.desktop.linux.Sys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class TelosOpSecGuard {
    var usbSerial: String? = null
    var panic: (() -> Unit)? = null
    private var job: Job? = null

    fun enableCoreDumpBlock() {
        Sys.run("bash", "-lc", "ulimit -c 0")
        runCatching { File("/proc/self/coredump_filter").writeText("0") }
    }

    fun startUsbDeadMan(scope: CoroutineScope) {
        val serial = usbSerial ?: return
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(2000)
                if (!usbPresent(serial)) panic?.invoke()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun usbPresent(serial: String): Boolean {
        val roots = listOf(File("/dev/disk/by-id"), File("/sys/bus/usb/devices"), File("/run/media"), File("/media"))
        return roots.any { root ->
            root.exists() && root.walkTopDown().maxDepth(4).any { it.name.contains(serial, ignoreCase = true) }
        }
    }
}
