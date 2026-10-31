package de.mm20.launcher2.ui.files
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePeekSheet(
    entry: FsEntry,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
) {
    val context = LocalContext.current
    var bmp by remember(entry.path) { mutableStateOf(FileThumbs.peek(FileThumbs.keyOf(entry, FileThumbs.LARGE_PX))) }
    LaunchedEffect(entry.path) {
        if (bmp == null && FileThumbs.eligible(context, entry)) bmp = FileThumbs.load(context, entry, FileThumbs.LARGE_PX)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (bmp != null) {
                Image(
                    bmp!!.asImageBitmap(), entry.name,
                    Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Text(entry.name, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    if (entry.size >= 0) formatSize(entry.size) else null,
                    entry.kind.name.lowercase(),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onOpen(); onDismiss() }) { Text(stringResource(I18nR.string.hc_open)) }
                TextButton(onClick = { onSelect(); onDismiss() }) { Text(stringResource(I18nR.string.telos_files_peek_select)) }
            }
        }
    }
}

@Composable
fun FolderFilmstrip(entries: List<FsEntry>, onOpen: (FsEntry) -> Unit) {
    val photos = entries.filter { !it.isDir && it.kind == FileKind.Image }.take(40)
    if (photos.size < 3) return
    val context = LocalContext.current
    LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { it.path }) { e ->
            var bmp by remember(e.path) { mutableStateOf(FileThumbs.peek(FileThumbs.keyOf(e, FileThumbs.MEDIUM_PX))) }
            LaunchedEffect(e.path) {
                if (bmp == null && FileThumbs.eligible(context, e)) bmp = FileThumbs.load(context, e, FileThumbs.MEDIUM_PX)
            }
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onOpen(e) },
                contentAlignment = Alignment.Center,
            ) {
                if (bmp != null) {
                    Image(bmp!!.asImageBitmap(), e.name, Modifier.size(88.dp), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}
