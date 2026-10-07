package de.mm20.launcher2.ui.media.docs

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.theme.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Telos Photos as a viewer for documents: PDF pages, text files (that can be edited), and the text and
 * tables of Word, Excel, PowerPoint, OpenDocument, RTF and EPUB files. The page layout of Office files is not reproduced.
 */
class DocumentViewerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val uri = intent.data ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (uri == null) { finish(); return }
        val name = displayName(uri)
        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    DocumentScreen(uri, name, onClose = { finish() })
                }
            }
        }
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) return it.getString(0) }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document"
    }
}

private sealed interface DocState {
    data object Loading : DocState
    data class Failed(val message: String) : DocState
    class Pdf(val doc: PdfDoc) : DocState
    class Blocks(val items: List<ViewItem>) : DocState
    class Text(val text: String, val truncated: Boolean) : DocState
}

private sealed interface ViewItem {
    class Block(val block: DocBlock) : ViewItem
    class Row(val cells: List<String>, val scroll: androidx.compose.foundation.ScrollState, val header: Boolean) : ViewItem
}

/** A PDF opened with the system's renderer. Only one page can be open at a time. */
private class PdfDoc(file: File) {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    val count: Int = renderer.pageCount

    @Synchronized
    fun aspect(page: Int): Float = renderer.openPage(page).use { it.width.toFloat() / it.height }

    @Synchronized
    fun render(page: Int, width: Int): Bitmap = renderer.openPage(page).use { p ->
        val height = (width * p.height / p.width.toFloat()).toInt().coerceAtLeast(1)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.eraseColor(AndroidColor.WHITE)
            p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }

    fun close() { runCatching { renderer.close() }; runCatching { fd.close() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentScreen(uri: Uri, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<DocState>(DocState.Loading) }
    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        state = withContext(Dispatchers.IO) { load(context, uri, name) }
    }
    DisposableEffect(Unit) { onDispose { (state as? DocState.Pdf)?.doc?.close() } }

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { target ->
        if (target != null) scope.launch(Dispatchers.IO) {
            val ok = runCatching { context.contentResolver.openOutputStream(target, "wt")!!.use { it.write(editText.toByteArray()) } }.isSuccess
            message = if (ok) "Saved" else "Could not save"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(painterResource(de.mm20.launcher2.base.R.drawable.arrow_back_24px), contentDescription = "Back") } },
                actions = {
                    if (state is DocState.Text) {
                        // a longer file is only partly loaded, saving it would cut the original
                        if (!editing) { if (!(state as DocState.Text).truncated) TextButton(onClick = { editText = (state as DocState.Text).text; editing = true }) { Text("Edit") } }
                        else {
                            TextButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    // the original is written when the sender allows it, otherwise it is saved as a new file
                                    val ok = runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(editText.toByteArray()) } }.isSuccess
                                    withContext(Dispatchers.Main) {
                                        if (ok) { message = "Saved"; state = DocState.Text(editText, false); editing = false } else saveAs.launch(name)
                                    }
                                }
                            }) { Text("Save") }
                            TextButton(onClick = { editing = false }) { Text("Cancel") }
                        }
                    }
                    TextButton(onClick = {
                        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { context.startActivity(Intent.createChooser(view, name)) }
                    }) { Text("Open with") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                DocState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is DocState.Failed -> Text(s.message, Modifier.align(Alignment.Center).padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                is DocState.Pdf -> PdfView(s.doc)
                is DocState.Blocks -> BlocksView(s.items)
                is DocState.Text -> if (editing) {
                    OutlinedTextField(
                        value = editText, onValueChange = { editText = it }, modifier = Modifier.fillMaxSize().padding(8.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                } else TextView(s)
            }
            message?.let { m ->
                Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.inverseSurface) {
                    Text(m, Modifier.padding(16.dp, 10.dp), color = MaterialTheme.colorScheme.inverseOnSurface)
                }
                LaunchedEffect(m) { kotlinx.coroutines.delay(2500); message = null }
            }
        }
    }
}

/** Copies the document into the cache (a PDF needs a real file) and reads it. */
private fun load(context: android.content.Context, uri: Uri, name: String): DocState = runCatching {
    val dir = File(context.cacheDir, "doc_view").apply { mkdirs() }
    dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 2 * 60 * 60_000 }?.forEach { it.delete() }
    val file = File(dir, "${System.nanoTime()}_" + name.replace('/', '_'))
    context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
    when {
        DocumentTypes.isPdf(name) -> DocState.Pdf(PdfDoc(file))
        DocumentTypes.isOffice(name) -> {
            val blocks = DocumentReaders.read(file, name)
            if (blocks.isEmpty()) DocState.Failed("Nothing to show in this file. Use \"Open with\" to open it in another app.")
            else DocState.Blocks(flatten(blocks))
        }
        else -> {
            val limit = 2 * 1024 * 1024
            val buffer = java.io.ByteArrayOutputStream()
            file.inputStream().use { input ->
                val chunk = ByteArray(64 * 1024)
                while (buffer.size() <= limit) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buffer.write(chunk, 0, n)
                }
            }
            val bytes = buffer.toByteArray()
            DocState.Text(String(bytes, 0, minOf(bytes.size, limit), Charsets.UTF_8), bytes.size > limit)
        }
    }
}.getOrElse {
    DocState.Failed(
        if (it is SecurityException) "This PDF is protected with a password." else "This file cannot be opened here (${it.message ?: it.javaClass.simpleName}). Use \"Open with\".",
    )
}

private fun flatten(blocks: List<DocBlock>): List<ViewItem> = buildList {
    for (b in blocks) when (b) {
        is DocBlock.Table -> {
            val scroll = androidx.compose.foundation.ScrollState(0)
            b.rows.forEachIndexed { i, row -> add(ViewItem.Row(row, scroll, i == 0)) }
        }
        else -> add(ViewItem.Block(b))
    }
}

// ---------------------------------------------------------------- views

@Composable
private fun PdfView(doc: PdfDoc) {
    val density = LocalDensity.current
    val screenWidth = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    var zoom by remember { mutableStateOf(1f) }
    val widthPx = (screenWidth * zoom).toInt()
    val hScroll = rememberScrollState()
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        Box(
            Modifier.fillMaxSize().horizontalScroll(hScroll).pointerInput(Unit) {
                // only a pinch is taken here, one finger still scrolls
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        ) {
            LazyColumn(Modifier.width(with(density) { widthPx.toDp() }), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(doc.count) { index -> PdfPage(doc, index, widthPx) }
            }
        }
        Surface(Modifier.align(Alignment.BottomEnd).padding(16.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f)) {
            Text("${listState.firstVisibleItemIndex + 1} / ${doc.count}", Modifier.padding(12.dp, 6.dp), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDoc, index: Int, widthPx: Int) {
    val bitmap by produceState<Bitmap?>(null, index, widthPx) {
        value = withContext(Dispatchers.Default) { runCatching { doc.render(index, widthPx) }.getOrNull() }
    }
    val aspect = remember(index) { runCatching { doc.aspect(index) }.getOrDefault(0.7f) }
    val shown = bitmap
    if (shown != null) {
        Image(shown.asImageBitmap(), contentDescription = "Page ${index + 1}", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    } else {
        Box(Modifier.fillMaxWidth().aspectRatio(aspect).background(Color.White), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
private fun TextView(s: DocState.Text) {
    val lines = remember(s.text) { s.text.lines() }
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
            items(lines.size) { i ->
                Text(lines[i].ifEmpty { " " }, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
            }
            if (s.truncated) item { Text("The file is longer, only the first 2 MB are shown. It cannot be edited here, so nothing is overwritten.", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun BlocksView(items: List<ViewItem>) {
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(items) { item ->
                when (item) {
                    is ViewItem.Block -> when (val b = item.block) {
                        is DocBlock.Title -> Text(b.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                        is DocBlock.Heading -> Text(
                            b.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                            style = when (b.level) { 1 -> MaterialTheme.typography.headlineSmall; 2 -> MaterialTheme.typography.titleLarge; else -> MaterialTheme.typography.titleMedium },
                        )
                        is DocBlock.Paragraph -> Text(b.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
                        is DocBlock.Table -> {}
                    }
                    is ViewItem.Row -> Row(Modifier.horizontalScroll(item.scroll)) {
                        item.cells.forEach { cell ->
                            Box(
                                Modifier.width(140.dp).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                    .background(if (item.header) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent).padding(8.dp),
                            ) { Text(cell, style = MaterialTheme.typography.bodySmall, fontWeight = if (item.header) FontWeight.SemiBold else FontWeight.Normal, maxLines = 6) }
                        }
                    }
                }
            }
        }
    }
}
