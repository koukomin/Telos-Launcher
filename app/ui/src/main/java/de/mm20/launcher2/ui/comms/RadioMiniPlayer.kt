// === TELOS_PENDING_REVIEW_START: radio_mini_player ===
package de.mm20.launcher2.ui.comms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R

@Composable
fun RadioMiniPlayer(modifier: Modifier = Modifier) {
    val viewModel: RadioViewModel = viewModel()
    val context = LocalContext.current
    val isVisible by viewModel.isVisible.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val stationName by viewModel.stationName.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlayingMetadata.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            // Glassmorphic translucent fallback using Surface
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Radio Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.music_note_24px), // Fallback icon
                        contentDescription = "Radio",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Metadata Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stationName.ifEmpty { "Transistor Radio" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = nowPlaying.ifEmpty { "Streaming live..." },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Controls
                IconButton(onClick = { viewModel.togglePlayPause() }) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                IconButton(onClick = { viewModel.stop() }) {
                    Icon(
                        painter = painterResource(R.drawable.close_24px),
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
// === TELOS_PENDING_REVIEW_END: radio_mini_player ===
