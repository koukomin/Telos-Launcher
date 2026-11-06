/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.mm20.launcher2.appmanagement.startup

import android.content.Context
import android.util.AtomicFile
import de.mm20.launcher2.ktx.ShizukuProcesses
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Private, opt-in scripts. Never import or execute a file from shared/backup storage. */
object StartupScripts {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storage = Mutex()
    private val execution = Mutex()
    private val once = AtomicBoolean()
    private val mutableScripts = MutableStateFlow<List<StartupScript>>(emptyList())
    private val mutableRuns = MutableStateFlow<Map<String, ScriptRun>>(emptyMap())
    private val mutableRunning = MutableStateFlow<String?>(null)
    val scripts = mutableScripts.asStateFlow()
    val runs = mutableRuns.asStateFlow()
    val running = mutableRunning.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true }
    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "startup-scripts.json"))

    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        storage.withLock { read(context).also { mutableScripts.value = it } }
    }

    private fun read(context: Context): List<StartupScript> {
        val file = file(context)
        if (!file.baseFile.exists()) return emptyList()
        return runCatching {
            file.openRead().use { input ->
                val bytes = input.readBytesBounded(1024 * 1024)
                json.decodeFromString<List<StartupScript>>(bytes).also { list ->
                    require(list.size <= 10 && list.map { it.id }.distinct().size == list.size)
                    list.forEach { it.validated() }
                }
            }
        }.getOrDefault(emptyList()) // Invalid storage is never eligible for execution.
    }

    suspend fun save(context: Context, script: StartupScript) = withContext(Dispatchers.IO) {
        script.validated()
        storage.withLock {
            val current = read(context)
            val next = if (current.any { it.id == script.id }) current.map { if (it.id == script.id) script else it }
                else current + script
            require(next.size <= 10)
            write(context, next)
        }
    }

    suspend fun remove(context: Context, id: String) = withContext(Dispatchers.IO) {
        storage.withLock { write(context, read(context).filterNot { it.id == id }) }
        mutableRuns.value = mutableRuns.value - id
    }

    private fun write(context: Context, scripts: List<StartupScript>) {
        val file = file(context)
        val stream = file.startWrite()
        try {
            val bytes = encodeStartupScripts(scripts)
            stream.write(bytes)
            file.finishWrite(stream)
            mutableScripts.value = scripts
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    /** Called from the actual launcher foreground, once per process (not from Application/providers). */
    fun onLauncherStarted(context: Context) {
        if (!once.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            val scripts = load(app).filter { it.enabled }
            for (script in scripts) run(app, script.id, expected = script)
        }
    }

    suspend fun run(context: Context, id: String, expected: StartupScript? = null) = withContext(Dispatchers.IO) {
        execution.withLock {
            val script = storage.withLock { read(context).firstOrNull { it.id == id } } ?: return@withLock
            if (expected != null && (!script.mayStartAutomatically(expected))) return@withLock
            mutableRunning.value = id
            try {
                ShizukuProcesses.init(context.applicationContext)
                // Modern toybox supervises a process group; older Android may kill only the direct child.
                // An absent timeout applet fails closed; never retry the script with another identity.
                val command = arrayOf("/system/bin/toybox", "timeout", "-k", "2", "30", "/system/bin/sh", "-c", script.body)
                val process = when (script.identity) {
                    ScriptIdentity.App -> ProcessBuilder(*command).directory(context.filesDir).start()
                    ScriptIdentity.Shizuku -> {
                        check(ShizukuProcesses.available()) { "Shizuku unavailable" }
                        ShizukuProcesses.start(command, retryOnSpawnFailure = false)
                    }
                    ScriptIdentity.Root -> {
                        val quoted = command.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
                        ProcessBuilder("su", "-c", quoted).start()
                    }
                }
                mutableRuns.value = mutableRuns.value + (id to ScriptProcessRunner.collect(id, process))
            } catch (error: Exception) {
                // Do not log script text, output or privileges to logcat.
                mutableRuns.value = mutableRuns.value + (id to ScriptRun(id, -1, error.javaClass.simpleName))
            } finally {
                mutableRunning.value = null
            }
        }
    }

    private fun InputStream.readBytesBounded(limit: Int, drain: Boolean = false): String {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            val left = limit - result.size()
            if (left > 0) result.write(buffer, 0, minOf(left, count))
            if (!drain) require(left >= count) { "Oversized script storage" }
        }
        return result.toString("UTF-8")
    }
}
