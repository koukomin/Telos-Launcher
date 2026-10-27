package de.mm20.launcher2.comms.files

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** SD Maid SE-style expendable files. Ideas from SD Maid SE (GPL-3); no source copied. */
class DiskCleaner(private val context: Context) {
    enum class Kind { Cache, Temp, Log, Thumbnail, Apk, EmptyDir, Corpse }

    data class Junk(val file: File, val kind: Kind, val size: Long)

    data class CleanResult(val moved: Int, val deleted: Int, val failed: Int, val bytes: Long)

    var intervalHours: Int
        get() = prefs.getInt("intervalHours", 0)
        set(v) { prefs.edit().putInt("intervalHours", v).apply() }
    var onlyCharging: Boolean
        get() = prefs.getBoolean("onlyCharging", false)
        set(v) { prefs.edit().putBoolean("onlyCharging", v).apply() }
    var skipPowerSave: Boolean
        get() = prefs.getBoolean("skipPowerSave", true)
        set(v) { prefs.edit().putBoolean("skipPowerSave", v).apply() }
    var privileged: Boolean
        get() = prefs.getBoolean("privileged", false)
        set(v) { prefs.edit().putBoolean("privileged", v).apply() }

    private val prefs = context.getSharedPreferences("telos_disk_cleaner", Context.MODE_PRIVATE)

    fun shouldRunNow(): Boolean {
        if (intervalHours <= 0) return false
        if (skipPowerSave && context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true) return false
        if (onlyCharging) {
            val batt = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = batt?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = batt?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val charging = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING
            if (!charging) return false
        }
        val last = prefs.getLong("lastRun", 0L)
        return System.currentTimeMillis() - last >= intervalHours * 3600_000L
    }

    fun markRan() {
        prefs.edit().putLong("lastRun", System.currentTimeMillis()).apply()
    }

    suspend fun scan(): List<Junk> = withContext(Dispatchers.IO) {
        val out = ArrayList<Junk>()
        val seen = HashSet<String>()
        fun add(f: File, kind: Kind) {
            val p = f.absolutePath
            if (!f.exists() || p in seen) return
            seen += p
            val size = if (f.isFile) f.length() else dirSize(f)
            if (kind == Kind.EmptyDir && size > 0) return
            out += Junk(f, kind, size)
        }
        context.cacheDir?.walkTopDown()?.maxDepth(6)?.forEach { if (it.isFile) add(it, Kind.Cache) }
        context.externalCacheDir?.walkTopDown()?.maxDepth(6)?.forEach { if (it.isFile) add(it, Kind.Cache) }
        val roots = publicRoots()
        for (root in roots) {
            listOf(".thumbnails", "Thumbnails").forEach { name ->
                File(root, name).takeIf { it.isDirectory }?.let { add(it, Kind.Thumbnail) }
                File(File(root, "DCIM"), name).takeIf { it.isDirectory }?.let { add(it, Kind.Thumbnail) }
                File(File(root, "Pictures"), name).takeIf { it.isDirectory }?.let { add(it, Kind.Thumbnail) }
            }
            File(root, "LOST.DIR").takeIf { it.exists() }?.let { add(it, Kind.Temp) }
            walkPublic(File(root, "Download"), 3) { f ->
                when {
                    f.isFile && (f.extension.equals("tmp", true) || f.extension.equals("temp", true)) -> add(f, Kind.Temp)
                    f.isFile && f.extension.equals("log", true) -> add(f, Kind.Log)
                    f.isFile && f.extension.equals("apk", true) && f.lastModified() < System.currentTimeMillis() - 14L * 86400_000 -> add(f, Kind.Apk)
                    f.isDirectory && f.list().isNullOrEmpty() -> add(f, Kind.EmptyDir)
                }
            }
        }
        if (privileged) scanCorpses().forEach { add(it, Kind.Corpse) }
        out.sortedByDescending { it.size }
    }

    suspend fun fillTrash(trash: TelosTrash, items: List<Junk>): CleanResult = withContext(Dispatchers.IO) {
        var moved = 0
        var deleted = 0
        var failed = 0
        var bytes = 0L
        for (j in items) {
            val ok = try {
                when (j.kind) {
                    Kind.Cache, Kind.EmptyDir -> {
                        val d = if (j.file.isDirectory) j.file.deleteRecursively() else j.file.delete()
                        if (d) deleted++ else failed++
                        d
                    }
                    else -> {
                        if (trash.supports(j.file.absolutePath) && j.file.isFile) {
                            trash.move(j.file.absolutePath)
                            moved++
                            true
                        } else if (j.file.isDirectory) {
                            var all = true
                            j.file.walkBottomUp().forEach { f ->
                                if (f.isFile && trash.supports(f.absolutePath)) {
                                    runCatching { trash.move(f.absolutePath) }.onSuccess { moved++ }.onFailure { all = false; failed++ }
                                } else if (f.isFile) {
                                    if (f.delete()) deleted++ else { all = false; failed++ }
                                }
                            }
                            j.file.delete()
                            all
                        } else if (j.file.delete()) {
                            deleted++
                            true
                        } else {
                            failed++
                            false
                        }
                    }
                }
            } catch (_: Exception) {
                failed++
                false
            }
            if (ok) bytes += j.size
        }
        if (privileged) runCatching { privilegedShell("pm trim-caches 512M") }
        markRan()
        CleanResult(moved, deleted, failed, bytes)
    }

    private fun publicRoots(): List<File> {
        val out = ArrayList<File>()
        Environment.getExternalStorageDirectory()?.let { out += it }
        context.getExternalFilesDirs(null)?.mapNotNull { f ->
            f?.path?.substringBefore("/Android/")?.let { File(it) }
        }?.let { out += it }
        return out.distinctBy { it.absolutePath }.filter { it.exists() }
    }

    private fun walkPublic(dir: File, depth: Int, visit: (File) -> Unit) {
        if (depth < 0 || !dir.isDirectory) return
        val kids = dir.listFiles() ?: return
        for (f in kids) {
            visit(f)
            if (f.isDirectory) walkPublic(f, depth - 1, visit)
        }
    }

    private fun dirSize(dir: File): Long {
        var n = 0L
        dir.walkTopDown().maxDepth(8).forEach { if (it.isFile) n += it.length() }
        return n
    }

    private fun installedPackages(): Set<String> =
        context.packageManager.getInstalledPackages(0).map { it.packageName }.toSet()

    private suspend fun scanCorpses(): List<File> {
        val installed = installedPackages()
        val out = ArrayList<File>()
        val listing = privilegedShell("ls -1 /sdcard/Android/data 2>/dev/null; ls -1 /sdcard/Android/obb 2>/dev/null")
            .orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
        for (pkg in listing) {
            if (pkg in installed || pkg.startsWith(".")) continue
            File("/sdcard/Android/data/$pkg").takeIf { it.exists() }?.let { out += it }
            File("/sdcard/Android/obb/$pkg").takeIf { it.exists() }?.let { out += it }
        }
        return out
    }

    suspend fun privilegedAvailable(): Boolean = de.mm20.launcher2.ktx.PrivilegedShell.available()

    private suspend fun privilegedShell(cmd: String): String? =
        de.mm20.launcher2.ktx.PrivilegedShell.run(cmd)?.let { result -> result.output.takeIf { result.exitCode == 0 } }
}
