package com.telos.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

object HelixToast {
    var title by mutableStateOf("")
    var body by mutableStateOf("")
    var visible by mutableStateOf(false)
    private var gen = 0

    fun show(title: String, body: String) {
        this.title = title
        this.body = body
        visible = true
        gen++
    }
}

@Composable
fun HelixToastOverlay(modifier: Modifier = Modifier) {
    LaunchedEffect(HelixToast.visible, HelixToast.title, HelixToast.body) {
        if (HelixToast.visible) {
            delay(HelixMotion.ToastMs)
            HelixToast.visible = false
        }
    }
    AnimatedVisibility(HelixToast.visible, modifier = modifier, enter = HelixMotion.enter, exit = HelixMotion.exit) {
        val shape = RoundedCornerShape(12.dp)
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .background(TelosColors.menu)
                .border(1.dp, TelosColors.outline, shape)
                .padding(12.dp),
        ) {
            Text(HelixToast.title, color = TelosColors.onSurface, fontSize = 14.sp)
            if (HelixToast.body.isNotBlank()) {
                Text(HelixToast.body, color = TelosColors.onSurface.copy(0.7f), fontSize = 12.sp)
            }
        }
    }
}
