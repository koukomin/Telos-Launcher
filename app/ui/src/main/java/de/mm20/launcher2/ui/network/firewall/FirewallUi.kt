package de.mm20.launcher2.ui.network.firewall

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import de.mm20.launcher2.network.api.AppDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The launcher icon of an installed app, loaded off the main thread. A placeholder shows while loading. */
@Composable
internal fun NetAppIcon(packageName: String?, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = if (packageName == null) null else withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    val b = bitmap
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = modifier.size(size))
    } else {
        Icon(
            painterResource(de.mm20.launcher2.base.R.drawable.apps_24px),
            contentDescription = null,
            modifier = modifier.size(size),
        )
    }
}

/** Main package of the app with this uid, for [NetAppIcon]. */
internal fun AppDirectory.packageOf(uid: Int): String? = byUid(uid)?.packageName

internal fun formatDateTime(context: Context, ms: Long): String =
    DateUtils.formatDateTime(
        context, ms,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
    )

internal fun formatClock(context: Context, ms: Long): String =
    DateUtils.formatDateTime(context, ms, DateUtils.FORMAT_SHOW_TIME)
