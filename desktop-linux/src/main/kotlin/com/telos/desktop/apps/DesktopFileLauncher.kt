package com.telos.desktop.apps

import com.telos.desktop.network.DesktopNetworkController
import com.telos.desktop.vault.TelosSessionAuditLog
import java.io.File

object DesktopFileLauncher {
    private val fieldCodes = Regex("%[fFuUdDnNickvm]|%i")
    private val terminals = listOf("kitty", "konsole", "alacritty", "foot", "xterm")

    fun launch(file: File): Boolean {
        val entry = DesktopFileParser.parse(file) ?: return false
        return launch(entry)
    }

    fun launch(entry: DesktopEntry): Boolean {
        val file = entry.file
        if (file.parentFile?.name.equals("Desktop", ignoreCase = true) ||
            file.parentFile?.absolutePath?.endsWith("/Desktop") == true
        ) {
            markExecutable(file)
        }
        val command = stripFieldCodes(entry.exec).trim()
        if (command.isBlank()) return false
        val argv = tokenize(command)
        if (argv.isEmpty()) return false
        val candidates = DesktopNetworkController.desktopIdCandidates(file.name, command)
        val decision = DesktopNetworkController.launchRouteDecision(candidates)
        when (decision) {
            DesktopNetworkController.LaunchRouteDecision.Deny -> {
                TelosSessionAuditLog.append(
                    "network_policy",
                    "app_route_deny:${candidates.firstOrNull().orEmpty()}",
                )
                return false
            }
            DesktopNetworkController.LaunchRouteDecision.Allow,
            DesktopNetworkController.LaunchRouteDecision.Vpn,
            -> Unit
        }
        val finalArgv = if (entry.terminal) wrapTerminal(argv) else argv
        return try {
            val pb = ProcessBuilder(finalArgv)
            entry.path?.let { dir ->
                val wd = File(dir)
                if (wd.isDirectory) pb.directory(wd)
            }

            // Integrate KsshaskpassManager env injection
            com.telos.desktop.security.KsshaskpassManager().injectAskPassEnv(pb)

            // === TELOS_PENDING_REVIEW_START: telos_elastic_sharded_pqc_vault ===
            com.telos.desktop.vault.PortableEnvironmentIsolator.inject(pb)
            // === TELOS_PENDING_REVIEW_END: telos_elastic_sharded_pqc_vault ===
            com.telos.desktop.cursor.CursorThemeApplier.inject(pb)
            if (decision == DesktopNetworkController.LaunchRouteDecision.Vpn) {
                // Best-effort tag only — do not fake a network namespace.
                pb.environment()["TELOS_APP_ROUTE"] = "vpn"
                TelosSessionAuditLog.append(
                    "network_policy",
                    "app_route_vpn:${candidates.firstOrNull().orEmpty()}",
                )
            }
            pb.redirectErrorStream(true)
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            pb.start()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun launchLnk(file: File): LaunchLnkResult {
        val wine = firstOnPath(listOf("wine", "wine64"))
        return if (wine != null) {
            try {
                ProcessBuilder(wine, file.absolutePath).start()
                LaunchLnkResult.LaunchedWithWine
            } catch (e: Exception) {
                LaunchLnkResult.Failed(e.message ?: "wine failed")
            }
        } else {
            LaunchLnkResult.WineMissing
        }
    }

    private fun markExecutable(file: File) {
        if (!file.canExecute()) {
            runCatching { file.setExecutable(true, false) }
            runCatching {
                ProcessBuilder("chmod", "+x", file.absolutePath).start().waitFor()
            }
        }
    }

    fun stripFieldCodes(exec: String): String = fieldCodes.replace(exec, "").replace(Regex("\\s+"), " ")

    private fun wrapTerminal(argv: List<String>): List<String> {
        val term = firstOnPath(terminals) ?: return argv
        return when (term) {
            "kitty" -> listOf(term) + argv
            "konsole" -> listOf(term, "-e") + argv
            "alacritty" -> listOf(term, "-e") + argv
            "foot" -> listOf(term) + argv
            else -> listOf(term, "-e") + argv
        }
    }

    fun firstOnPath(names: List<String>): String? {
        val path = System.getenv("PATH") ?: return null
        val dirs = path.split(':')
        for (name in names) {
            for (dir in dirs) {
                val f = File(dir, name)
                if (f.isFile && f.canExecute()) return f.absolutePath
            }
        }
        return null
    }

    fun openTerminalHere(directory: File): Boolean {
        val term = firstOnPath(terminals) ?: return false
        return try {
            val pb = ProcessBuilder(term)
            if (directory.isDirectory) pb.directory(directory)
            pb.start()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tokenize(command: String): List<String> {
        val out = ArrayList<String>()
        val buf = StringBuilder()
        var quote: Char? = null
        for (ch in command) {
            when {
                quote != null && ch == quote -> quote = null
                quote == null && (ch == '"' || ch == '\'') -> quote = ch
                quote == null && ch.isWhitespace() -> {
                    if (buf.isNotEmpty()) {
                        out += buf.toString()
                        buf.clear()
                    }
                }
                else -> buf.append(ch)
            }
        }
        if (buf.isNotEmpty()) out += buf.toString()
        return out
    }

    sealed class LaunchLnkResult {
        data object LaunchedWithWine : LaunchLnkResult()
        data object WineMissing : LaunchLnkResult()
        data class Failed(val message: String) : LaunchLnkResult()
    }
}
