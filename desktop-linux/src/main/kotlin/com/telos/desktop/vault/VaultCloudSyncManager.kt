package com.telos.desktop.vault

import com.telos.desktop.linux.Sys
import java.io.File

class VaultCloudSyncManager {
    var remote: String? = null
    var localTarget: File? = File("/home/koukos/Nefos/MegaStronger/MusicBackup")

    fun listRcloneRemotes(): List<String> {
        val out = Sys.run("rclone", "listremotes", timeoutSec = 8).stdout
        return out.lines().map { it.trim().trimEnd(':') }.filter { it.isNotBlank() }
    }

    fun detectLocalCloud(): List<File> {
        val home = File(System.getProperty("user.home"))
        return listOf(
            File(home, "Nefos/MegaStronger"),
            File(home, "Nefos"),
            File(home, "MEGA"),
            File(home, "Dropbox"),
            File(home, "Nextcloud"),
        ).filter { it.isDirectory }
    }

    fun syncCarriers(carriers: List<File>): Boolean {
        Sys.run("sync", timeoutSec = 30)
        val dest = localTarget
        var ok = true
        if (dest != null && dest.exists()) {
            dest.mkdirs()
            carriers.forEach { f ->
                val r = Sys.run("cp", "--update", f.absolutePath, dest.absolutePath, timeoutSec = 60)
                if (!r.ok) ok = false
            }
        }
        val rem = remote
        if (!rem.isNullOrBlank() && Sys.which("rclone") != null) {
            val target = if (rem.endsWith(":")) rem else "$rem:"
            carriers.forEach { f ->
                val r = Sys.run("rclone", "copy", "--update", "--transfers", "4", f.absolutePath, target, timeoutSec = 120)
                if (!r.ok) ok = false
            }
        }
        return ok
    }

    fun pullLatest(destDir: File, remoteOrPath: String): Boolean {
        destDir.mkdirs()
        if (File(remoteOrPath).isDirectory) {
            return Sys.run("cp", "-u", "-r", "${remoteOrPath.trimEnd('/')}/.", destDir.absolutePath, timeoutSec = 180).ok
        }
        val rem = if (remoteOrPath.endsWith(":")) remoteOrPath else "$remoteOrPath:"
        return Sys.run("rclone", "copy", "--update", "--transfers", "4", rem, destDir.absolutePath, timeoutSec = 180).ok
    }
}
