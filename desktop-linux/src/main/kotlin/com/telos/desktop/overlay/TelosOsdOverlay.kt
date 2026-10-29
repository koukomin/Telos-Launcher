package com.telos.desktop.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.ui.TelosColors
import kotlinx.coroutines.delay

object TelosOsd {
    var visible by mutableStateOf(false)
    var label by mutableStateOf("")
    var progress by mutableStateOf(0f)

    fun show(text: String, value: Float) {
        label = text
        progress = value.coerceIn(0f, 1f)
        visible = true
    }
}

@Composable
fun TelosOsdOverlay() {
    LaunchedEffect(TelosOsd.visible, TelosOsd.label) {
        if (TelosOsd.visible) {
            delay(1400)
            TelosOsd.visible = false
        }
    }
    Box(Modifier.fillMaxWidth().padding(top = 72.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(TelosOsd.visible, enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier.width(280.dp).clip(RoundedCornerShape(20.dp)).background(TelosColors.surfaceContainer.copy(alpha = 0.94f)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(TelosOsd.label, color = TelosColors.secondary, fontSize = 14.sp)
                LinearProgressIndicator(
                    progress = { TelosOsd.progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 8.dp),
                    color = TelosColors.primary,
                    trackColor = TelosColors.surface,
                )
            }
        }
    }
}
