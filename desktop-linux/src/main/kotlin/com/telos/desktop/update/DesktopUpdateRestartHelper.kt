package com.telos.desktop.update

import com.telos.desktop.vault.PortableEnvironmentIsolator
import com.telos.desktop.vault.PortableTraceGuard
import com.telos.desktop.vault.TelosSessionAuditLog
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Best-effort restart helper after a confirmed update stage (VOS-5).
 *
 * In-place swap of a **running** JVM jar is inherently best-effort: the JVM may
 * keep the original file mapped. This helper writes a pending-update marker and
 * an optional shell script that waits for the current process to exit, then
 * copies the staged jar over the target and optionally relaunches.
 *
 * [prepareRestartAfterUpdate] spawns the helper and then exits the process
 * (after the caller has already obtained user confirmation).
 */
object DesktopUpdateRestartHelper {
    const val MARKER_NAME = "telos-update-pending.marker"
    const val SCRIPT_NAME = "telos-apply-update.sh"
    const val MARKER_PREFIX = "telos-desktop-update-pending/v1"

    data class PendingUpdate(
        val stagedJar: File,
        val targetJar: File?,
        val version: String?,
        val marker: File,
    )

    data class RestartPlan(
        val marker: File,
        val script: File?,
        val stagedJar: File,
        val targetJar: File?,
    )

    fun markerFile(dir: File = markerDir()): File = File(dir, MARKER_NAME)

    fun markerDir(): File {
        PortableEnvironmentIsolator.tmpDir?.let { return File(it, DesktopSignedUpdate.STAGING_DIR_NAME) }
        return DesktopSignedUpdate.defaultInstallDir()
    }

    fun detectRunningJar(): File? {
        val loc = DesktopUpdateRestartHelper::class.java.protectionDomain?.codeSource?.location ?: return null
        return runCatching {
            val f = File(loc.toURI())
            f.takeIf { it.isFile && it.name.endsWith(".jar", ignoreCase = true) }
        }.getOrNull()
            ?: PortableTraceGuard.jarSiblingDir()?.listFiles()
                ?.firstOrNull {
                    it.isFile && it.name.startsWith("telos", ignoreCase = true) && it.name.endsWith(".jar")
                }
    }

    fun writeUpdateMarker(
        stagedJar: File,
        targetJar: File? = detectRunningJar(),
        version: String? = null,
        dir: File = markerDir(),
    ): File {
        require(stagedJar.isFile) { "Staged update jar missing" }
        dir.mkdirs()
        val marker = markerFile(dir)
        marker.writeText(
            buildString {
                append(MARKER_PREFIX).append('\n')
                append("staged=").append(stagedJar.absolutePath).append('\n')
                if (targetJar != null) append("target=").append(targetJar.absolutePath).append('\n')
                if (!version.isNullOrBlank()) append("version=").append(version.trim()).append('\n')
                append("writtenAt=").append(System.currentTimeMillis()).append('\n')
            },
        )
        TelosSessionAuditLog.append(
            "update_restart_marker",
            "staged=${stagedJar.name} target=${targetJar?.name ?: "none"}",
        )
        return marker
    }

    fun readMarker(dir: File = markerDir()): PendingUpdate? {
        val marker = markerFile(dir)
        if (!marker.isFile) return null
        var staged: String? = null
        var target: String? = null
        var version: String? = null
        marker.bufferedReader().use { reader ->
            reader.lineSequence().forEach { line ->
                when {
                    line.startsWith("staged=") -> staged = line.substringAfter('=').trim()
                    line.startsWith("target=") -> target = line.substringAfter('=').trim()
                    line.startsWith("version=") -> version = line.substringAfter('=').trim()
                }
            }
        }
        val stagedFile = staged?.let { File(it) }?.takeIf { it.isFile } ?: return null
        return PendingUpdate(
            stagedJar = stagedFile,
            targetJar = target?.let { File(it) },
            version = version,
            marker = marker,
        )
    }

    fun clearMarker(dir: File = markerDir()) {
        markerFile(dir).delete()
    }

    /**
     * Write a POSIX shell helper that replaces [targetJar] with [stagedJar] after
     * [pid] exits. Best-effort only — does not claim atomic JVM self-replace.
     */
    fun writeReplaceScript(
        stagedJar: File,
        targetJar: File,
        pid: Long = ProcessHandle.current().pid(),
        relaunchCommand: String? = null,
        dir: File = markerDir(),
    ): File {
        dir.mkdirs()
        val script = File(dir, SCRIPT_NAME)
        val relaunch = relaunchCommand?.trim().orEmpty()
        script.writeText(
            """
            #!/bin/sh
            # Telos Desktop update apply helper (best-effort; running JVM jar swap is not atomic)
            set -eu
            STAGED='${shellSingleQuote(stagedJar.absolutePath)}'
            TARGET='${shellSingleQuote(targetJar.absolutePath)}'
            PID='$pid'
            while kill -0 "${'$'}PID" 2>/dev/null; do sleep 0.2; done
            sleep 0.3
            cp -f "${'$'}STAGED" "${'$'}TARGET"
            rm -f '${shellSingleQuote(markerFile(dir).absolutePath)}'
            ${if (relaunch.isNotEmpty()) "exec $relaunch" else ":"}
            """.trimIndent() + "\n",
        )
        script.setExecutable(true)
        return script
    }

    /**
     * After the user confirms restart: write marker (+ optional replace script),
     * spawn the helper, then exit the JVM. Does not auto-install updates.
     */
    fun prepareRestartAfterUpdate(
        stagedJar: File,
        targetJar: File? = detectRunningJar(),
        version: String? = null,
        dir: File = markerDir(),
        exitAfterSpawn: Boolean = true,
        spawnHelper: (script: File) -> Unit = ::defaultSpawn,
        exitFn: () -> Unit = { kotlin.system.exitProcess(0) },
    ): Result<RestartPlan> = runCatching {
        require(stagedJar.isFile) { "Staged update jar missing" }
        val marker = writeUpdateMarker(stagedJar, targetJar, version, dir)
        val script = if (targetJar != null) {
            writeReplaceScript(stagedJar, targetJar, dir = dir).also { spawnHelper(it) }
        } else {
            null
        }
        val plan = RestartPlan(marker = marker, script = script, stagedJar = stagedJar, targetJar = targetJar)
        TelosSessionAuditLog.append(
            "update_restart_prepare",
            "staged=${stagedJar.name} script=${script != null}",
        )
        if (exitAfterSpawn) exitFn()
        plan
    }

    /**
     * Apply a pending marker on a subsequent cold start (copy staged → target).
     * Returns false when nothing to do. Never mounts vaults or installs without a marker.
     */
    fun applyPendingMarkerIfPresent(dir: File = markerDir()): Boolean {
        val pending = readMarker(dir) ?: return false
        val target = pending.targetJar ?: return false
        if (!pending.stagedJar.isFile) {
            clearMarker(dir)
            return false
        }
        target.parentFile?.mkdirs()
        pending.stagedJar.copyTo(target, overwrite = true)
        clearMarker(dir)
        TelosSessionAuditLog.append(
            "update_restart_applied",
            "target=${target.name}",
        )
        return true
    }

    private fun defaultSpawn(script: File) {
        ProcessBuilder("/bin/sh", script.absolutePath)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .also {
                // Detach: do not wait; the script waits for this JVM to exit.
                runCatching { it.waitFor(1, TimeUnit.MILLISECONDS) }
            }
    }

    private fun shellSingleQuote(path: String): String =
        path.replace("'", "'\"'\"'")
}
