package de.mm20.launcher2.ui.freeze

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Runs the production app_process entry point with shell privileges on the CI emulator. */
class RecentsBridgeTest {
    @Test(timeout = 25000) fun privilegedHelperRegistersAndReturnsACompleteSnapshot() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val apk = instrumentation.targetContext.applicationInfo.sourceDir.replace("'", "'\\''")
        val user = android.os.Process.myUid() / 100000
        val fds = instrumentation.uiAutomation.executeShellCommandRw(
            "CLASSPATH='$apk' /system/bin/app_process /system/bin de.mm20.launcher2.freeze.RootRecentsBridge $user")
        val lines = LinkedBlockingQueue<String>()
        val input = ParcelFileDescriptor.AutoCloseInputStream(fds[0])
        val output = ParcelFileDescriptor.AutoCloseOutputStream(fds[1])
        val reader = Thread({ runCatching { input.bufferedReader().useLines { stream -> stream.forEach { lines.offer(it) } } } }).apply { isDaemon = true; start() }
        try {
            fun await(prefix: String): String {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
                while (System.nanoTime() < deadline) {
                    val line = lines.poll(500, TimeUnit.MILLISECONDS) ?: continue
                    if (line.startsWith(prefix)) return line
                }
                error("No $prefix response from the production task listener")
            }
            assertEquals("READY", await("READY"))
            output.write("snapshot\n".toByteArray()); output.flush()
            val tasks = JSONArray(await("S ").removePrefix("S "))
            assertTrue(tasks.length() < 256)
            repeat(tasks.length()) { index ->
                val task = tasks.getJSONObject(index)
                assertTrue(task.getInt("id") >= 0)
                assertTrue(task.getString("package").isNotBlank())
                assertTrue(task.getInt("user") >= 0)
            }
        } finally {
            output.close(); input.close(); reader.interrupt()
        }
    }
}
