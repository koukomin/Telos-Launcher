package com.telos.desktop.apps

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
        val finalArgv = if (entry.terminal) wrapTerminal(argv) else argv
        return try {
            val pb = ProcessBuilder(finalArgv)
            entry.path?.let { dir ->
                val wd = File(dir)
                if (wd.isDirectory) pb.directory(wd)
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
