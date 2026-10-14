import de.mm20.launcher2.helper.ResumableFileTransfer
import java.nio.file.Files
import java.lang.management.ManagementFactory
fun main() {
 val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
 bean.isThreadAllocatedMemoryEnabled = true
 val times = mutableListOf<Double>(); val allocations = mutableListOf<Long>()
 repeat(7) { run ->
  val root = Files.createTempDirectory("telos-perf").toFile()
  try {
   val src = root.resolve("src").apply { mkdir() }; val dst = root.resolve("dst").apply { mkdir() }
   val files = (0 until 400).map { i -> src.resolve("$i.bin").apply { writeBytes(ByteArray(1024 + i % 31) { (it + i).toByte() }) } }
   val plan = ResumableFileTransfer.plan(files, dst, true)
   val before = bean.getThreadAllocatedBytes(Thread.currentThread().id); val start = System.nanoTime()
   ResumableFileTransfer.execute(plan, {}, {}, { _, _ -> })
   val elapsed = (System.nanoTime()-start)/1e6; val allocated = bean.getThreadAllocatedBytes(Thread.currentThread().id)-before
   check(files.none { it.exists() } && dst.listFiles()!!.size == files.size)
   if (run >= 2) { times += elapsed; allocations += allocated }
  } finally { root.deleteRecursively() }
 }
 println("median_ms=${times.sorted()[2]} allocated_bytes=${allocations.sorted()[2]}")
}
