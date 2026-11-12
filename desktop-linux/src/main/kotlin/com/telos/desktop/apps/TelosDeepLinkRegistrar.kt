package com.telos.desktop.apps

import com.telos.desktop.linux.Sys
import com.telos.desktop.platform.PlatformEnvironment
import com.telos.desktop.platform.TelosRuntimePlatform
import com.telos.desktop.vault.PortableEnvironmentIsolator
import com.telos.desktop.vault.PortableTraceGuard
import java.io.File

/**
 * Registers the `telos://` URL scheme for Linux sessions and portable Windows where possible.
 * Missing host tools / permissions degrade to false without throwing.
 */
object TelosDeepLinkRegistrar {
    const val DESKTOP_FILE_NAME = "telos-url-handler.desktop"
    const val MIME = "x-scheme-handler/telos"

    fun ensureRegistered(): Boolean = when (PlatformEnvironment.current) {
        TelosRuntimePlatform.LinuxDesktop -> registerLinux()
        TelosRuntimePlatform.WindowsDesktop -> registerWindows()
        else -> false
    }

    fun isRegistered(): Boolean = when (PlatformEnvironment.current) {
        TelosRuntimePlatform.LinuxDesktop -> linuxRegistered()
        TelosRuntimePlatform.WindowsDesktop -> windowsRegistered()
        else -> false
    }

    internal fun desktopEntry(exec: String): String = """
        [Desktop Entry]
        Name=Telos Desktop
        Name[el]=Telos Desktop
        Comment=Open telos:// links in Telos Desktop
        Comment[el]=Άνοιγμα συνδέσμων telos:// στο Telos Desktop
        Exec=$exec
        Terminal=false
        Type=Application
        Categories=Utility;
        MimeType=$MIME;
        NoDisplay=true
    """.trimIndent().replace("\n        ", "\n") + "\n"

    internal fun linuxExecLine(javaExe: String, jar: File): String =
        "$javaExe -jar ${shellQuote(jar.absolutePath)} --open %u"

    internal fun windowsCommand(javaExe: String, jar: File): String =
        "\"$javaExe\" -jar \"${jar.absolutePath}\" --open \"%1\""

    private fun registerLinux(): Boolean {
        val jar = detectJar() ?: return false
        val java = detectJava()
        val apps = File(userDataHome(), "applications").apply { mkdirs() }
        val desktop = File(apps, DESKTOP_FILE_NAME)
        val exec = linuxExecLine(java, jar)
        return try {
            desktop.writeText(desktopEntry(exec))
            desktop.setExecutable(true)
            Sys.run("update-desktop-database", apps.absolutePath, timeoutSec = 8)
            val mime = Sys.run(
                "xdg-mime",
                "default",
                DESKTOP_FILE_NAME,
                MIME,
                timeoutSec = 8,
            )
            mime.ok || desktop.isFile
        } catch (_: Exception) {
            desktop.isFile
        }
    }

    private fun linuxRegistered(): Boolean {
        val r = Sys.run("xdg-mime", "query", "default", MIME, timeoutSec = 5)
        if (!r.ok) return File(userDataHome(), "applications/$DESKTOP_FILE_NAME").isFile
        return r.stdout.trim().contains("telos", ignoreCase = true)
    }

    private fun registerWindows(): Boolean {
        val jar = detectJar() ?: return false
        val java = detectJava()
        val cmd = windowsCommand(java, jar)
        val base = """HKCU\Software\Classes\telos"""
        val steps = listOf(
            listOf("reg", "add", base, "/ve", "/d", "URL:Telos Protocol", "/f"),
            listOf("reg", "add", base, "/v", "URL Protocol", "/d", "", "/f"),
            listOf("reg", "add", "$base\\shell\\open\\command", "/ve", "/d", cmd, "/f"),
        )
        var ok = true
        for (args in steps) {
            if (!Sys.run(*args.toTypedArray(), timeoutSec = 8).ok) ok = false
        }
        return ok
    }

    private fun windowsRegistered(): Boolean {
        val r = Sys.run(
            "reg",
            "query",
            """HKCU\Software\Classes\telos""",
            "/ve",
            timeoutSec = 5,
        )
        return r.ok && r.stdout.contains("Telos", ignoreCase = true)
    }

    private fun userDataHome(): File {
        PortableEnvironmentIsolator.vaultHome?.let { return File(it, ".local/share") }
        System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let { return File(it) }
        return File(System.getProperty("user.home"), ".local/share")
    }

    private fun detectJar(): File? {
        System.getProperty("telos.jar.path")?.let { return File(it).takeIf { f -> f.isFile } }
        PortableTraceGuard.jarSiblingDir()?.let { dir ->
            dir.listFiles()
                ?.firstOrNull { it.isFile && it.name.contains("telos-desktop") && it.extension == "jar" }
                ?.let { return it }
        }
        File("/opt/telos-desktop/telos-desktop.jar").takeIf { it.isFile }?.let { return it }
        return null
    }

    private fun detectJava(): String {
        val home = System.getProperty("java.home") ?: return "java"
        val bin = if (PlatformEnvironment.current == TelosRuntimePlatform.WindowsDesktop) {
            File(home, "bin/java.exe")
        } else {
            File(home, "bin/java")
        }
        return if (bin.isFile) bin.absolutePath else "java"
    }

    private fun shellQuote(path: String): String =
        if (path.any { it.isWhitespace() || it == '"' || it == '\'' }) {
            "'" + path.replace("'", "'\\''") + "'"
        } else {
            path
        }
}
