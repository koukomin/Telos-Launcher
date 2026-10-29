package com.telos.desktop.monitor

import com.telos.desktop.linux.Sys
import java.io.File
import java.nio.file.FileSystems
import kotlin.math.max

data class CpuSample(
    val totalPercent: Double,
    val perCore: List<Double>,
    val freqsMhz: List<Int>,
    val tempC: Double?,
    val fanRpm: Int?,
    val load1: Double,
    val load5: Double,
    val load15: Double,
    val uptimeSec: Double,
    val model: String,
)

data class MemSample(
    val totalKb: Long,
    val availableKb: Long,
    val usedKb: Long,
    val buffersKb: Long,
    val cachedKb: Long,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
    val zramUsedKb: Long,
)

data class GpuSample(
    val name: String,
    val kind: Kind,
    val loadPercent: Double?,
    val vramUsedMb: Long?,
    val vramTotalMb: Long?,
    val tempC: Double?,
    val powerW: Double?,
) {
    enum class Kind { AmdIgpu, NvidiaDgpu }
}

data class DiskIoSample(val device: String, val readMBps: Double, val writeMBps: Double)

data class MountSample(val path: String, val totalBytes: Long, val usedBytes: Long, val fstype: String)

data class NetSample(val iface: String, val rxKBps: Double, val txKBps: Double)

data class ProcRow(
    val pid: Int,
    val ppid: Int,
    val user: String,
    val name: String,
    val cmdline: String,
    val cpuPercent: Double,
    val rssKb: Long,
    val sharedKb: Long,
    val nice: Int,
    val oomScoreAdj: Int,
    val uid: Int,
)

data class AppGroup(
    val key: String,
    val name: String,
    val pids: List<Int>,
    val cpuPercent: Double,
    val rssKb: Long,
    val children: Int,
)

data class SystemSnapshot(
    val cpu: CpuSample,
    val mem: MemSample,
    val gpus: List<GpuSample>,
    val disks: List<DiskIoSample>,
    val mounts: List<MountSample>,
    val nets: List<NetSample>,
    val processes: List<ProcRow>,
    val apps: List<AppGroup>,
    val kernel: String,
    val os: String,
    val session: String,
    val timestampMs: Long,
)

class LinuxSystemTelemetryEngine(
    var intervalMs: Long = 1000L,
) {
    private var lastCpu: List<LongArray> = emptyList()
    private var lastDisk: Map<String, Pair<Long, Long>> = emptyMap()
    private var lastNet: Map<String, Pair<Long, Long>> = emptyMap()
    private var lastProcCpu: Map<Int, Long> = emptyMap()
    private var lastWallNs: Long = 0L
    private val history = ArrayDeque<SystemSnapshot>(120)
    val overviewHistory: List<SystemSnapshot> get() = history.toList()

    fun poll(): SystemSnapshot {
        val now = System.nanoTime()
        val dt = if (lastWallNs == 0L) 1.0 else ((now - lastWallNs) / 1e9).coerceAtLeast(0.2)
        lastWallNs = now
        val cpu = readCpu(dt)
        val mem = readMem()
        val gpus = readGpus()
        val disks = readDisks(dt)
        val mounts = readMounts()
        val nets = readNet(dt)
        val procs = readProcs(dt)
        val snap = SystemSnapshot(
            cpu = cpu,
            mem = mem,
            gpus = gpus,
            disks = disks,
            mounts = mounts,
            nets = nets,
            processes = procs,
            apps = groupApps(procs),
            kernel = Sys.readText("/proc/sys/kernel/osrelease") ?: Sys.run("uname", "-r").stdout.trim(),
            os = File("/etc/os-release").takeIf { it.isFile }?.readLines()?.firstOrNull { it.startsWith("PRETTY_NAME=") }
                ?.substringAfter('=')?.trim('"') ?: "Linux",
            session = System.getenv("XDG_SESSION_TYPE") ?: "unknown",
            timestampMs = System.currentTimeMillis(),
        )
        if (history.size >= 60) history.removeFirst()
        history.addLast(snap)
        return snap
    }

    private fun readCpu(dt: Double): CpuSample {
        val lines = File("/proc/stat").takeIf { it.isFile }?.readLines().orEmpty()
        val cpuLines = lines.filter { it.startsWith("cpu") && !it.startsWith("cpu ") || it.startsWith("cpu ") }
        val cores = lines.filter { it.matches(Regex("cpu\\d+.*")) }
        fun parse(line: String): LongArray {
            val p = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            return longArrayOf(p.getOrElse(0) { 0 }, p.getOrElse(1) { 0 }, p.getOrElse(2) { 0 }, p.getOrElse(3) { 0 }, p.sum())
        }
        val all = listOf(lines.first { it.startsWith("cpu ") }) + cores
        val parsed = all.map { parse(it) }
        val percents = if (lastCpu.size == parsed.size) {
            parsed.mapIndexed { i, cur ->
                val prev = lastCpu[i]
                val dTotal = (cur[4] - prev[4]).toDouble().coerceAtLeast(1.0)
                val dIdle = (cur[3] - prev[3]).toDouble()
                ((1.0 - dIdle / dTotal) * 100.0).coerceIn(0.0, 100.0)
            }
        } else parsed.map { 0.0 }
        lastCpu = parsed
        val freqs = File("/sys/devices/system/cpu").listFiles()?.filter { it.name.matches(Regex("cpu\\d+")) }?.sortedBy { it.name.removePrefix("cpu").toIntOrNull() ?: 0 }?.map { cpu ->
            ((Sys.readLong("${cpu.path}/cpufreq/scaling_cur_freq") ?: 0L) / 1000L).toInt()
        }.orEmpty()
        val temp = thermalTemp()
        val fan = fanRpm()
        val load = Sys.readText("/proc/loadavg")?.split(' ') ?: listOf("0", "0", "0")
        val uptime = Sys.readText("/proc/uptime")?.split(' ')?.firstOrNull()?.toDoubleOrNull() ?: 0.0
        val model = File("/proc/cpuinfo").takeIf { it.isFile }?.readLines()?.firstOrNull { it.startsWith("model name") }?.substringAfter(':')?.trim() ?: "CPU"
        return CpuSample(
            totalPercent = percents.firstOrNull() ?: 0.0,
            perCore = percents.drop(1),
            freqsMhz = freqs,
            tempC = temp,
            fanRpm = fan,
            load1 = load.getOrNull(0)?.toDoubleOrNull() ?: 0.0,
            load5 = load.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
            load15 = load.getOrNull(2)?.toDoubleOrNull() ?: 0.0,
            uptimeSec = uptime,
            model = model,
        )
    }

    private fun thermalTemp(): Double? {
        val zones = File("/sys/class/thermal").listFiles()?.filter { it.name.startsWith("thermal_zone") }.orEmpty()
        for (z in zones) {
            val type = Sys.readText("${z.path}/type").orEmpty()
            val t = Sys.readLong("${z.path}/temp") ?: continue
            val c = if (t > 1000) t / 1000.0 else t.toDouble()
            if (type.contains("x86_pkg") || type.contains("k10") || type.contains("cpu") || type.contains("acpitz")) return c
        }
        val hw = File("/sys/class/hwmon").listFiles().orEmpty()
        for (h in hw) {
            val name = Sys.readText("${h.path}/name").orEmpty()
            if (name.contains("k10temp") || name.contains("coretemp") || name.contains("zenpower")) {
                h.listFiles()?.firstOrNull { it.name.matches(Regex("temp\\d+_input")) }?.let {
                    val t = it.readText().trim().toLongOrNull() ?: return@let
                    return if (t > 1000) t / 1000.0 else t.toDouble()
                }
            }
        }
        return null
    }

    private fun fanRpm(): Int? {
        File("/sys/class/hwmon").listFiles().orEmpty().forEach { h ->
            h.listFiles()?.firstOrNull { it.name.matches(Regex("fan\\d+_input")) }?.let {
                return it.readText().trim().toIntOrNull()
            }
        }
        return null
    }

    private fun readMem(): MemSample {
        val map = HashMap<String, Long>()
        File("/proc/meminfo").takeIf { it.isFile }?.forEachLine { line ->
            val k = line.substringBefore(':').trim()
            val v = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L
            map[k] = v
        }
        val total = map["MemTotal"] ?: 0
        val avail = map["MemAvailable"] ?: 0
        var zram = 0L
        File("/proc/swaps").takeIf { it.isFile }?.readLines()?.drop(1)?.forEach { line ->
            val p = line.split(Regex("\\s+"))
            if (p.firstOrNull()?.contains("zram") == true) zram += (p.getOrNull(3)?.toLongOrNull() ?: 0)
        }
        return MemSample(
            totalKb = total,
            availableKb = avail,
            usedKb = (total - avail).coerceAtLeast(0),
            buffersKb = map["Buffers"] ?: 0,
            cachedKb = map["Cached"] ?: 0,
            swapTotalKb = map["SwapTotal"] ?: 0,
            swapFreeKb = map["SwapFree"] ?: 0,
            zramUsedKb = zram,
        )
    }

    private fun readGpus(): List<GpuSample> {
        val out = ArrayList<GpuSample>()
        File("/sys/class/drm").listFiles()?.filter { it.name.matches(Regex("card\\d+")) }?.forEach { card ->
            val dev = File(card, "device")
            val busy = Sys.readText("${dev.path}/gpu_busy_percent")?.toDoubleOrNull()
            val vramUsed = Sys.readLong("${dev.path}/mem_info_vram_used")
            val vramTotal = Sys.readLong("${dev.path}/mem_info_vram_total")
            if (busy != null || vramTotal != null) {
                var temp: Double? = null
                File(dev, "hwmon").listFiles()?.firstOrNull()?.let { hw ->
                    hw.listFiles()?.firstOrNull { it.name.startsWith("temp") && it.name.endsWith("_input") }?.let {
                        val t = it.readText().trim().toLongOrNull()
                        if (t != null) temp = if (t > 1000) t / 1000.0 else t.toDouble()
                    }
                }
                out += GpuSample("AMD iGPU ${card.name}", GpuSample.Kind.AmdIgpu, busy, vramUsed?.div(1024 * 1024), vramTotal?.div(1024 * 1024), temp, null)
            }
        }
        val nvidia = File("/proc/driver/nvidia/gpus")
        val awake = nvidia.isDirectory && nvidia.listFiles()?.isNotEmpty() == true
        if (awake && Sys.which("nvidia-smi") != null) {
            val csv = Sys.run(
                "nvidia-smi",
                "--query-gpu=name,utilization.gpu,memory.used,memory.total,temperature.gpu,power.draw",
                "--format=csv,noheader,nounits",
            ).stdout.trim()
            csv.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val p = line.split(',').map { it.trim() }
                if (p.size >= 6) {
                    out += GpuSample(
                        name = p[0],
                        kind = GpuSample.Kind.NvidiaDgpu,
                        loadPercent = p[1].toDoubleOrNull(),
                        vramUsedMb = p[2].toLongOrNull(),
                        vramTotalMb = p[3].toLongOrNull(),
                        tempC = p[4].toDoubleOrNull(),
                        powerW = p[5].toDoubleOrNull(),
                    )
                }
            }
        }
        return out
    }

    private fun readDisks(dt: Double): List<DiskIoSample> {
        val cur = HashMap<String, Pair<Long, Long>>()
        File("/proc/diskstats").takeIf { it.isFile }?.forEachLine { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size < 14) return@forEachLine
            val name = p[2]
            if (name.matches(Regex("loop\\d+|ram\\d+|sr\\d+"))) return@forEachLine
            val readSec = p[5].toLongOrNull() ?: 0
            val writeSec = p[9].toLongOrNull() ?: 0
            cur[name] = readSec to writeSec
        }
        val out = cur.map { (name, rw) ->
            val prev = lastDisk[name]
            val read = if (prev != null) ((rw.first - prev.first) * 512.0 / dt) / (1024 * 1024) else 0.0
            val write = if (prev != null) ((rw.second - prev.second) * 512.0 / dt) / (1024 * 1024) else 0.0
            DiskIoSample(name, max(0.0, read), max(0.0, write))
        }
        lastDisk = cur
        return out.sortedByDescending { it.readMBps + it.writeMBps }.take(8)
    }

    private fun readMounts(): List<MountSample> {
        val out = ArrayList<MountSample>()
        val seen = HashSet<String>()
        fun add(path: String, fstype: String = "") {
            if (!seen.add(path)) return
            val f = File(path)
            if (!f.exists()) return
            val store = runCatching { FileSystems.getDefault().getPath(path).toFile() }.getOrNull() ?: f
            val total = store.totalSpace
            val used = total - store.freeSpace
            if (total > 0) out += MountSample(path, total, used, fstype)
        }
        add("/", "btrfs")
        add("/home")
        val vault = File(System.getProperty("user.home"), ".local/share/telos/vault_mnt")
        if (vault.isDirectory) add(vault.absolutePath, "luks")
        return out
    }

    private fun readNet(dt: Double): List<NetSample> {
        val cur = HashMap<String, Pair<Long, Long>>()
        File("/proc/net/dev").takeIf { it.isFile }?.readLines()?.drop(2)?.forEach { line ->
            val parts = line.trim().split(Regex("[:\\s]+"))
            if (parts.size < 10) return@forEach
            val name = parts[0].removeSuffix(":")
            if (name == "lo") return@forEach
            val rx = parts[1].toLongOrNull() ?: 0
            val tx = parts[9].toLongOrNull() ?: 0
            cur[name] = rx to tx
        }
        val out = cur.map { (name, rw) ->
            val prev = lastNet[name]
            val rx = if (prev != null) ((rw.first - prev.first) / dt) / 1024.0 else 0.0
            val tx = if (prev != null) ((rw.second - prev.second) / dt) / 1024.0 else 0.0
            NetSample(name, max(0.0, rx), max(0.0, tx))
        }
        lastNet = cur
        return out
    }

    private fun readProcs(dt: Double): List<ProcRow> {
        val passwd = HashMap<Int, String>()
        File("/etc/passwd").takeIf { it.isFile }?.forEachLine {
            val p = it.split(':')
            if (p.size >= 3) passwd[p[2].toIntOrNull() ?: return@forEachLine] = p[0]
        }
        val myUid = androidishUid()
        val curCpu = HashMap<Int, Long>()
        val rows = ArrayList<ProcRow>()
        File("/proc").listFiles()?.forEach { dir ->
            val pid = dir.name.toIntOrNull() ?: return@forEach
            val stat = runCatching { File(dir, "stat").readText() }.getOrNull() ?: return@forEach
            val rpar = stat.lastIndexOf(')')
            if (rpar < 0) return@forEach
            val name = stat.substringAfter('(').substringBeforeLast(')')
            val rest = stat.substring(rpar + 1).trim().split(' ')
            val ppid = rest.getOrNull(1)?.toIntOrNull() ?: 0
            val utime = rest.getOrNull(11)?.toLongOrNull() ?: 0
            val stime = rest.getOrNull(12)?.toLongOrNull() ?: 0
            val nice = rest.getOrNull(16)?.toIntOrNull() ?: 0
            val total = utime + stime
            curCpu[pid] = total
            val prev = lastProcCpu[pid]
            val cpu = if (prev != null) ((total - prev) / (dt * hertz())) * 100.0 else 0.0
            val status = runCatching { File(dir, "status").readLines() }.getOrNull().orEmpty()
            fun field(k: String) = status.firstOrNull { it.startsWith(k) }?.substringAfter(':')?.trim()
            val uid = field("Uid:")?.split(Regex("\\s+"))?.firstOrNull()?.toIntOrNull() ?: myUid
            val rss = field("VmRSS:")?.substringBefore(' ')?.toLongOrNull() ?: 0
            val shared = runCatching {
                File(dir, "statm").readText().trim().split(' ').getOrNull(2)?.toLongOrNull()?.times(4)
            }.getOrNull() ?: 0
            val cmdline = runCatching { File(dir, "cmdline").readBytes().joinToString("") { c -> if (c == 0.toByte()) " " else c.toInt().toChar().toString() }.trim() }.getOrNull().orEmpty()
            val oom = Sys.readText("${dir.path}/oom_score_adj")?.toIntOrNull() ?: 0
            rows += ProcRow(pid, ppid, passwd[uid] ?: uid.toString(), name, cmdline.ifBlank { name }, cpu.coerceAtLeast(0.0), rss, shared, nice, oom, uid)
        }
        lastProcCpu = curCpu
        return rows.sortedByDescending { it.cpuPercent }
    }

    private fun groupApps(procs: List<ProcRow>): List<AppGroup> {
        val grouped = procs.groupBy { p ->
            val cmd = p.cmdline.ifBlank { p.name }
            val base = cmd.substringBefore(' ').substringAfterLast('/')
            base.ifBlank { p.name }
        }
        return grouped.map { (k, list) ->
            AppGroup(k, k, list.map { it.pid }, list.sumOf { it.cpuPercent }, list.sumOf { it.rssKb }, list.size)
        }.sortedByDescending { it.cpuPercent }.take(40)
    }

    private fun hertz(): Double = 100.0

    private fun androidishUid(): Int = try {
        val id = Sys.run("id", "-u").stdout.trim().toIntOrNull()
        id ?: 1000
    } catch (_: Exception) { 1000 }
}
