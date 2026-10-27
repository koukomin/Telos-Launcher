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
        val scenario = androidx.test.core.app.ActivityScenario.launch(androidx.activity.ComponentActivity::class.java)
        var taskId = -1
        scenario.onActivity { taskId = it.taskId }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val apk = instrumentation.targetContext.applicationInfo.sourceDir
        check(apk.none { it.isWhitespace() })
        val user = android.os.Process.myUid() / 100000
        val fds = instrumentation.uiAutomation.executeShellCommandRw(
            "/system/bin/env CLASSPATH=$apk /system/bin/app_process /system/bin de.mm20.launcher2.freeze.RootRecentsBridge $user")
        val lines = LinkedBlockingQueue<String>()
        val input = ParcelFileDescriptor.AutoCloseInputStream(fds[0])
        val output = ParcelFileDescriptor.AutoCloseOutputStream(fds[1])
        val reader = Thread({ runCatching { input.bufferedReader().useLines { stream -> stream.forEach { lines.offer(it) } } } }).apply { isDaemon = true; start() }
        try {
            fun await(prefix: String): String {
                val unexpected = mutableListOf<String>()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
                while (System.nanoTime() < deadline) {
                    val line = lines.poll(500, TimeUnit.MILLISECONDS) ?: continue
                    if (line.startsWith(prefix)) return line
                    if (unexpected.size < 12) unexpected.add(line)
                }
                error("No $prefix response from the production task listener: $unexpected")
            }
            assertEquals("READY", await("READY"))
            output.write("snapshot\n".toByteArray()); output.flush()
            val tasks = JSONArray(await("S ").removePrefix("S "))
            assertTrue(tasks.length() < 256)
            assertTrue("Snapshot must include the actual test task", (0 until tasks.length()).any { tasks.getJSONObject(it).getInt("id") == taskId })
            repeat(tasks.length()) { index ->
                val task = tasks.getJSONObject(index)
                assertTrue(task.getInt("id") >= 0)
                assertTrue(task.getString("package").isNotBlank())
                assertTrue(task.getInt("user") >= 0)
            }
            scenario.onActivity { it.finishAndRemoveTask() }
            await("R $taskId")
            var absent = false
            repeat(20) {
                if (!absent) {
                    output.write("snapshot\n".toByteArray()); output.flush()
                    val after = JSONArray(await("S ").removePrefix("S "))
                    absent = (0 until after.length()).none { after.getJSONObject(it).getInt("id") == taskId }
                    if (!absent) Thread.sleep(100)
                }
            }
            assertTrue("A fresh snapshot must prove final task removal", absent)
        } finally {
            output.close(); input.close(); reader.interrupt(); scenario.close()
        }
    }
}
