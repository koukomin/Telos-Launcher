package com.telos.desktop.xdg

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService

class DesktopWatchService(
    private val directory: File,
    private val scope: CoroutineScope,
    private val onChange: () -> Unit,
) {
    private var job: Job? = null
    private var watch: WatchService? = null

    fun start() {
        stop()
        if (!directory.isDirectory) directory.mkdirs()
        job = scope.launch(Dispatchers.IO) {
            val ws = FileSystems.getDefault().newWatchService()
            watch = ws
            val path = directory.toPath()
            path.register(
                ws,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY,
            )
            while (isActive) {
                val key = try {
                    ws.take()
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {
                    break
                }
                key.pollEvents()
                onChange()
                if (!key.reset()) break
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { watch?.close() }
        watch = null
    }
}
