package de.mm20.launcher2.helper
import java.lang.management.ManagementFactory
import java.nio.file.Files
fun main() {
    val root = Files.createTempDirectory("index-benchmark").toFile()
    try {
        var directory = root
        repeat(16) { directory = directory.resolve("nested-$it").also { it.mkdir() } }
        repeat(4000) { directory.resolve("file-$it.txt").writeText("x") }
        repeat(2) { check(FileSearchIndex.scan(root).files.size == 4016) }
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled = true
        val times = ArrayList<Double>(); val allocations = ArrayList<Long>()
        repeat(5) {
            val before = bean.getThreadAllocatedBytes(Thread.currentThread().id)
            val start = System.nanoTime()
            check(FileSearchIndex.scan(root).files.size == 4016)
            times += (System.nanoTime() - start) / 1e6
            allocations += bean.getThreadAllocatedBytes(Thread.currentThread().id) - before
        }
        println("index-4000-depth16 medianMs=${times.sorted()[2]} medianAllocatedBytes=${allocations.sorted()[2]}")
    } finally { root.deleteRecursively() }
}
