package com.telos.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import java.io.File

@Composable
fun DiskIcon(file: File?, label: String, size: Dp = 48.dp) {
    val painterBitmap = remember(file?.absolutePath) {
        file?.takeIf { it.isFile }?.let { loadImage(it) }
    }
    if (painterBitmap != null) {
        Image(
            bitmap = painterBitmap,
            contentDescription = label,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(TelosColors.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.take(1).uppercase(),
                color = TelosColors.secondary,
                fontSize = (size.value * 0.4f).sp,
            )
        }
    }
}

private fun loadImage(file: File) = runCatching {
    when {
        file.name.endsWith(".svg", true) -> {
            val bytes = file.readBytes()
            val svg = SVGDOM(Data.makeFromBytes(bytes))
            val w = svg.root?.width?.value?.toInt()?.takeIf { it > 0 } ?: 128
            val h = svg.root?.height?.value?.toInt()?.takeIf { it > 0 } ?: 128
            val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(w.coerceAtMost(256), h.coerceAtMost(256))
            svg.setContainerSize(surface.width.toFloat(), surface.height.toFloat())
            svg.render(surface.canvas)
            surface.makeImageSnapshot().toComposeImageBitmap()
        }
        else -> {
            val bytes = file.readBytes()
            org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }
    }
}.getOrNull()
