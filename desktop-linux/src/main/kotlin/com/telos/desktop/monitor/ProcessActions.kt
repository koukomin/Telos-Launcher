package com.telos.desktop.monitor

import com.telos.desktop.linux.Sys
import java.io.File

object ProcessActions {
    fun signal(pid: Int, sig: String): Boolean = Sys.run("kill", "-s", sig, pid.toString()).ok

    fun term(pid: Int) = signal(pid, "TERM")
    fun kill(pid: Int) = signal(pid, "KILL")
    fun stop(pid: Int) = signal(pid, "STOP")
    fun cont(pid: Int) = signal(pid, "CONT")

    fun termGroup(pids: List<Int>) = pids.forEach { term(it) }
    fun killGroup(pids: List<Int>) = pids.forEach { kill(it) }

    fun renice(pid: Int, nice: Int): Boolean {
        val r = Sys.run("renice", nice.toString(), "-p", pid.toString())
        if (r.ok) return true
        return Sys.run("pkexec", "renice", nice.toString(), "-p", pid.toString()).ok
    }

    fun setOomScoreAdj(pid: Int, value: Int): Boolean {
        val f = File("/proc/$pid/oom_score_adj")
        val written = runCatching { f.writeText(value.toString()) }.isSuccess
        if (written) return true
        return Sys.run("pkexec", "tee", f.absolutePath, input = value.toString()).ok
    }

    fun openLocation(cmdline: String): Boolean {
        val bin = cmdline.substringBefore(' ').ifBlank { return false }
        val resolved = if (bin.startsWith("/")) File(bin) else File(Sys.which(bin.substringAfterLast('/')) ?: return false)
        val dir = resolved.parentFile ?: return false
        return Sys.runBg("xdg-open", dir.absolutePath)
    }
}

data class SystemdUnit(val name: String, val load: String, val active: String, val sub: String, val description: String, val user: Boolean)

object SystemdManager {
    fun list(user: Boolean): List<SystemdUnit> {
        val args = if (user) arrayOf("systemctl", "--user", "list-units", "--type=service", "--all", "--no-pager", "--no-legend")
        else arrayOf("systemctl", "list-units", "--type=service", "--all", "--no-pager", "--no-legend")
        return Sys.run(*args, timeoutSec = 8).stdout.lineSequence().mapNotNull { line ->
            val p = line.trim().split(Regex("\\s+"), 5)
            if (p.size < 5) null else SystemdUnit(p[0], p[1], p[2], p[3], p[4], user)
        }.toList()
    }

    fun action(unit: SystemdUnit, verb: String): Boolean {
        val cmd = if (unit.user) arrayOf("systemctl", "--user", verb, unit.name)
        else arrayOf("pkexec", "systemctl", verb, unit.name)
        return Sys.run(*cmd, timeoutSec = 15).ok
    }
}
