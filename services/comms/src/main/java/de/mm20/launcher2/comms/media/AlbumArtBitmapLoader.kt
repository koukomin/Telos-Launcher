package de.mm20.launcher2.comms.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors

/**
 * Loads the cover for the notification and lock screen. For a local music file this is the cover
 * of the file (or its album) from the media store; other URIs are decoded as plain images.
 */
class AlbumArtBitmapLoader(private val context: Context) : BitmapLoader {

    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
        BitmapFactory.decodeByteArray(data, 0, data.size) ?: error("Cannot decode artwork")
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 29 && uri.scheme == "content" && uri.pathSegments.contains("audio")) {
            resolver.loadThumbnail(uri, Size(512, 512), null)
        } else {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: error("Cannot load artwork")
        }
    }
}
