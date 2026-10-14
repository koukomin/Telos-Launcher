package de.mm20.launcher2.helper

import java.lang.management.ManagementFactory
import java.nio.file.Files

fun main() {
    val root = Files.createTempDirectory("analysis-benchmark").toFile()
    try {
        val content = ByteArray(1024) { (it % 251).toByte() }
        repeat(1200) { root.resolve("file-$it.bin").writeBytes(content) }
        val records = StorageAnalysis.scan(root).files
        repeat(2) { check(StorageAnalysis.duplicates(records).single().size == 1200) }
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled = true
        val times = ArrayList<Double>(); val allocations = ArrayList<Long>()
        repeat(5) {
            val before = bean.getThreadAllocatedBytes(Thread.currentThread().id)
            val start = System.nanoTime()
            check(StorageAnalysis.duplicates(records).single().size == 1200)
            times += (System.nanoTime() - start) / 1e6
            allocations += bean.getThreadAllocatedBytes(Thread.currentThread().id) - before
        }
        println("duplicate-1200x1KiB medianMs=${times.sorted()[2]} medianAllocatedBytes=${allocations.sorted()[2]}")
    } finally { root.deleteRecursively() }
}
