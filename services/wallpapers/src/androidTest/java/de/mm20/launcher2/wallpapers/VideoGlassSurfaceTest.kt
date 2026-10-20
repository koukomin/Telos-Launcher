package de.mm20.launcher2.wallpapers

import android.content.Intent
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class VideoGlassSurfaceTest {
    @Test fun renderedFramesReachSharedBlurAndStaleEnginesCannotOverwriteThem() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,
            VideoGlassTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as VideoGlassTestActivity
        try {
            assertTrue(activity.ready.await(10, TimeUnit.SECONDS))
            runBlocking {
                withTimeout(10_000) {
                    val request = VideoGlassRequest(32, 400, 800)
                    val broker = VideoGlass()
                    val client = Any()
                    val oldEngine = Any()
                    val engine = Any()
                    broker.register(client, listOf(request))
                    broker.claim(oldEngine)
                    broker.claim(engine)
                    for (color in listOf(Color.RED, Color.BLUE)) {
                        withContext(Dispatchers.Main) { activity.paint(color) }
                        val captured = withContext(Dispatchers.Main) { copyVideoGlass(activity.surface.holder) }
                        assertNotNull(captured)
                        assertTrue(maxOf(captured!!.width, captured.height) <= 192)
                        val blurred = blurVideoGlass(captured, listOf(request))
                        broker.publish(engine, blurred)
                        val frame = broker.frames.value.getValue(request)
                        assertEquals(color, frame.getPixel(frame.width / 2, frame.height / 2))
                        broker.publish(oldEngine, emptyMap())
                        broker.clear(oldEngine)
                        assertFalse(broker.frames.value.isEmpty())
                    }
                    broker.unregister(client)
                    assertTrue(broker.frames.value.isEmpty())
                    broker.clear(engine)
                }
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
