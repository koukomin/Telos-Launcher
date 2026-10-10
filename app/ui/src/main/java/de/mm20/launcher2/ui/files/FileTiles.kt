package de.mm20.launcher2.ui.files

import android.graphics.Bitmap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private typealias TileIcons = de.mm20.launcher2.base.R.drawable

/**
 * A tile of the picture view: a square rounded preview (or a coloured type tile when there is none) and the name under it.
 * The large variant also shows size and date. [selecting] shows the round selection badge on every tile.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileTile(
    e: FsEntry,
    selected: Boolean,
    selecting: Boolean,
    large: Boolean,
    reduceAnimations: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && !reduceAnimations) 0.97f else 1f,
        animationSpec = if (reduceAnimations) androidx.compose.animation.core.snap() else spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "tilePress",
    )
    val color = thumbColor(e)
    val req = if (large) FileThumbs.LARGE_PX else FileThumbs.MEDIUM_PX
    val eligible = remember(e.path) { FileThumbs.eligible(context, e) }
    val key = FileThumbs.keyOf(e, req)
    var bitmap by remember(key, eligible) { mutableStateOf<Bitmap?>(if (eligible) FileThumbs.peek(key) else null) }
    // runs while the tile is on screen and is cancelled when it scrolls away
    LaunchedEffect(key, eligible) {
        if (eligible && bitmap == null) bitmap = FileThumbs.load(context, e, req)
    }
    val fade by animateFloatAsState(
        if (bitmap != null) 1f else 0f,
        animationSpec = if (reduceAnimations) androidx.compose.animation.core.snap() else tween(200),
        label = "tileFade",
    )
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .combinedClickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                .background(if (e.isDir) color.copy(alpha = 0.22f) else color),
            contentAlignment = Alignment.Center,
        ) {
            val b = bitmap
            if (b == null || fade < 1f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(e.kind.icon), contentDescription = null,
                        tint = if (e.isDir) color else Color.White,
                        modifier = Modifier.size(if (large) 56.dp else 40.dp),
                    )
                    if (!e.isDir && e.extension.isNotEmpty()) {
                        Text(
                            e.extension.uppercase().take(5), color = Color.White, maxLines = 1,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            if (b != null) {
                Image(
                    bitmap = b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(fade),
                )
                if (e.kind == FileKind.Video) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(TileIcons.play_arrow_24px), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
                if (e.isDir) {
                    Box(
                        Modifier.align(Alignment.BottomStart).padding(8.dp).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(TileIcons.folder_24px), contentDescription = null, tint = FileKind.Folder.color, modifier = Modifier.size(18.dp)) }
                }
            }
            if (selected) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)))
            if (selecting) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(8.dp).size(24.dp).clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(painterResource(TileIcons.check_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(
            e.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
        )
        if (large) {
            val detail = listOfNotNull(
                if (!e.isDir && e.size >= 0) formatSize(e.size) else null,
                formatDate(e.modified).ifEmpty { null },
            ).joinToString("  ·  ")
            if (detail.isNotEmpty()) Text(
                detail, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
            )
        }
    }
}
