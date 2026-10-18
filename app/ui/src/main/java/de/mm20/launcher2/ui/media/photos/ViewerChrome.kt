package de.mm20.launcher2.ui.media.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.component.VirtualAppBlur
import de.mm20.launcher2.ui.component.virtualAppElevated
import de.mm20.launcher2.ui.component.virtualAppGlass

@Composable
fun ViewerTopChrome(
    blur: VirtualAppBlur,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val glass = virtualAppElevated()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .virtualAppGlass(blur, glass)
            .statusBarsPadding()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun ViewerPillBar(
    blur: VirtualAppBlur,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val glass = virtualAppElevated()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(28.dp))
            .virtualAppGlass(blur, glass)
            .background(Color.Transparent)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun viewerScrim(): Color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)
