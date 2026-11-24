package de.mm20.launcher2.comms.search
import java.lang.management.ManagementFactory
fun main() {
    val fields = List(20_000) { index -> when (index % 4) {
        0 -> "Γιάννης Παπαδόπουλος +30 697 123 $index"
        1 -> "Giannis Papadopoulos +30 697 123 $index"
        2 -> "Κώστας Παπαδόπουλος +30 698 123 $index"
        else -> "Εργασίες ταξιδιού Αύγουστος $index.pdf"
    } }
    val query = "giannis papa 697"
    fun batch(): Int {
        val matcher = TelosSearch.compile(query)
        return fields.sumOf { matcher.score(it) }
    }
    val expected = batch(); check(expected > 0); repeat(2) { check(batch() == expected) }
    val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    bean.isThreadAllocatedMemoryEnabled = true
    val times = ArrayList<Double>(); val allocations = ArrayList<Long>()
    repeat(5) {
        val before = bean.getThreadAllocatedBytes(Thread.currentThread().id)
        val start = System.nanoTime(); check(batch() == expected)
        times += (System.nanoTime() - start) / 1e6
        allocations += bean.getThreadAllocatedBytes(Thread.currentThread().id) - before
    }
    println("search20k medianMs=${times.sorted()[2]} medianAllocatedBytes=${allocations.sorted()[2]} checksum=$expected")
}
