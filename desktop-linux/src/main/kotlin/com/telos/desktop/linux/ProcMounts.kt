package com.telos.desktop.linux

object ProcMounts {
    fun containsPath(mounts: String, path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val needle = path.trimEnd('/')
        if (needle.isEmpty()) return false
        return mounts.lineSequence().any { line ->
            val cols = line.split(Regex("\\s+"))
            cols.size >= 2 && cols[1].trimEnd('/') == needle
        }
    }
}
