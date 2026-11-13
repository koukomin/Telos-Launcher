package com.telos.desktop.platform

import com.telos.desktop.linux.Sys
import java.io.File

/**
 * Optional per-user Windows shell replacement (HKCU — no admin).
 * Replaces the user Shell value so Telos Desktop starts instead of explorer.exe.
 */
object WindowsShellIntegration {
    private const val HKCU_SHELL =
        "HKCU\\Software\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon"

    val isWindows: Boolean
        get() = PlatformEnvironment.current == TelosRuntimePlatform.WindowsDesktop

    fun currentShell(): String? {
        if (!isWindows) return null
        val r = Sys.run(
            "reg",
            "query",
            HKCU_SHELL,
            "/v",
            "Shell",
            timeoutSec = 8,
        )
        if (!r.ok) return null
        return r.stdout.lines()
            .map { it.trim() }
            .firstOrNull { it.startsWith("Shell") }
            ?.substringAfter("REG_SZ", "")
            ?.trim()
            ?.ifBlank { null }
    }

    fun telosShellCommand(javaExe: String, jarFile: File): String =
        "\"$javaExe\" -jar \"${jarFile.absolutePath}\""

    fun detectJar(): File? {
        com.telos.desktop.vault.PortableTraceGuard.jarSiblingDir()?.let { dir ->
            dir.listFiles()
                ?.firstOrNull { it.isFile && it.name.contains("telos-desktop") && it.extension == "jar" }
                ?.let { return it }
        }
        System.getProperty("telos.jar.path")?.let { return File(it).takeIf { f -> f.isFile } }
        return null
    }

    fun detectJava(): String =
        System.getProperty("java.home")?.let { File(it, "bin/java.exe").takeIf { f -> f.isFile }?.absolutePath }
            ?: "java"

    fun installTelosShell(): Boolean {
        if (!isWindows) return false
        val jar = detectJar() ?: return false
        val cmd = telosShellCommand(detectJava(), jar)
        val set = Sys.run(
            "reg",
            "add",
            HKCU_SHELL,
            "/v",
            "Shell",
            "/t",
            "REG_SZ",
            "/d",
            cmd,
            "/f",
            timeoutSec = 10,
        )
        return set.ok
    }

    fun restoreExplorerShell(): Boolean {
        if (!isWindows) return false
        val set = Sys.run(
            "reg",
            "add",
            HKCU_SHELL,
            "/v",
            "Shell",
            "/t",
            "REG_SZ",
            "/d",
            "explorer.exe",
            "/f",
            timeoutSec = 10,
        )
        return set.ok
    }

    fun isTelosShellActive(): Boolean {
        val cur = currentShell() ?: return false
        return cur.contains("telos-desktop", ignoreCase = true) ||
            cur.contains("TelosPortable", ignoreCase = true)
    }
}
