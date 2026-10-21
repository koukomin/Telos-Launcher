"""Install an emulator-only setup receiver into the debug variant before CI builds."""
from pathlib import Path
root = Path('app/app/src/debug')
source = root / 'java/de/mm20/launcher2/development/ScreenshotSetupReceiver.kt'
source.parent.mkdir(parents=True, exist_ok=True)
(root / 'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.SET_WALLPAPER" />
    <application><receiver android:name="de.mm20.launcher2.development.ScreenshotSetupReceiver" android:exported="true" /></application>
</manifest>''')
source.write_text('''package de.mm20.launcher2.development

import android.app.Activity
import android.app.LocaleManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.LocaleList

/** Only generated in the temporary screenshot build; never part of release source. */
class ScreenshotSetupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en-US")
        val image = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 1080f, 2400f,
            intArrayOf(Color.rgb(8, 18, 34), Color.rgb(22, 23, 44), Color.rgb(47, 19, 34)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1080f, 2400f, paint)
        paint.shader = RadialGradient(120f, 650f, 850f,
            intArrayOf(Color.rgb(27, 77, 105), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1080f, 1700f, paint)
        paint.shader = RadialGradient(1000f, 1900f, 850f,
            intArrayOf(Color.rgb(111, 53, 52), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 1000f, 1080f, 2400f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 16f
        paint.color = Color.argb(70, 107, 163, 181)
        for (i in 0..4) {
            val offset = i * 95f
            val path = Path().apply {
                moveTo(-220f + offset, 0f)
                cubicTo(500f + offset, 450f, -380f + offset, 1250f, 440f + offset, 2400f)
            }
            canvas.drawPath(path, paint)
        }
        WallpaperManager.getInstance(context).setBitmap(image, null, true, WallpaperManager.FLAG_SYSTEM)
        image.recycle()
        resultCode = Activity.RESULT_OK
    }
}
''')
print('Generated debug-only wallpaper and English-locale setup receiver')
