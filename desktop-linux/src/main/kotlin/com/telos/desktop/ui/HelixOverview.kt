package com.telos.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.wm.TelosWindowManager

@Composable
fun HelixOverview(
    wm: TelosWindowManager,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(TelosColors.background.copy(alpha = 0.88f)).clickable { onDismiss() },
    ) {
        Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (row in 0..1) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (col in 0..1) {
                        val i = row * 2 + col
                        WorkspaceTile(wm, i, onDismiss, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceTile(wm: TelosWindowManager, i: Int, onDismiss: () -> Unit, modifier: Modifier) {
    val active = wm.currentWorkspace == i
    val wins = wm.windows.filter { it.workspace == i && !it.minimized }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxSize()
            .clip(shape)
            .background(if (active) TelosColors.surfaceHigh else TelosColors.surfaceContainer)
            .border(1.dp, if (active) TelosColors.secondary else TelosColors.outline, shape)
            .clickable {
                wm.switchWorkspace(i)
                onDismiss()
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Desktop ${i + 1}", color = TelosColors.secondary, fontSize = 16.sp)
        if (wins.isEmpty()) {
            Text("Empty", color = TelosColors.onSurface.copy(0.6f), fontSize = 13.sp)
        } else {
            wins.take(8).forEach { w ->
                Text(w.title, color = TelosColors.onSurface, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}
