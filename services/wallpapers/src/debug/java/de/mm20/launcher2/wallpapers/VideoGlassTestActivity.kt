package de.mm20.launcher2.wallpapers

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.CountDownLatch

/** Debug-only surface producer used by real PixelCopy instrumentation. */
class VideoGlassTestActivity : Activity() {
    lateinit var surface: SurfaceView
    val ready = CountDownLatch(1)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        surface = SurfaceView(this)
        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                paint(Color.RED)
                ready.countDown()
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        })
        setContentView(surface)
    }
    fun paint(color: Int) {
        val canvas = surface.holder.lockCanvas() ?: return
        try { canvas.drawColor(color) } finally { surface.holder.unlockCanvasAndPost(canvas) }
    }
}
