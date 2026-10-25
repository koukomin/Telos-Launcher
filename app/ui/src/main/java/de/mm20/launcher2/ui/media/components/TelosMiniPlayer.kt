package de.mm20.launcher2.ui.media.components

import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import de.mm20.launcher2.comms.media.TelosAudioService
import de.mm20.launcher2.comms.youtube.YouTubePlayerService
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.delay

@Composable
fun TelosMiniPlayer(
    modifier: Modifier = Modifier,
    serviceClass: Class<*> = YouTubePlayerService::class.java
) {
    val context = LocalContext.current
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var trackTitle by remember { mutableStateOf("") }
    var artistName by remember { mutableStateOf("") }
    var currentPosition by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(1f) }

    // Connect to MediaSessionService
    DisposableEffect(context, serviceClass) {
        val sessionToken = SessionToken(context, ComponentName(context, serviceClass))
        val controllerFuture: ListenableFuture<MediaController> = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            runCatching {
                mediaController = controllerFuture.get()
            }
        }, MoreExecutors.directExecutor())

        onDispose {
            MediaController.releaseFuture(controllerFuture)
        }
    }

    // Sync player state and position
    mediaController?.let { controller ->
        LaunchedEffect(controller) {
            while (true) {
                isPlaying = controller.isPlaying
                currentPosition = controller.currentPosition.toFloat().coerceAtLeast(0f)
                duration = controller.duration.toFloat().coerceAtLeast(1f)
                val mediaItem = controller.currentMediaItem
                trackTitle = mediaItem?.mediaMetadata?.title?.toString() ?: ""
                artistName = mediaItem?.mediaMetadata?.artist?.toString() ?: ""
                delay(500)
            }
        }
    }

    if (mediaController == null || (trackTitle.isBlank() && !isPlaying)) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trackTitle.ifBlank { stringResource(R.string.telos_youtube_preparing) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artistName.ifBlank { "Telos Media" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val c = mediaController ?: return@IconButton
                        if (c.isPlaying) {
                            c.pause()
                        } else {
                            c.play()
                        }
                    }) {
                        Icon(
                            painter = painterResource(if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px),
                            contentDescription = "Play/Pause",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { mediaController?.seekToNext() }) {
                        Icon(
                            painter = painterResource(R.drawable.skip_next_24px),
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Slider(
                value = currentPosition,
                onValueChange = { newValue ->
                    mediaController?.seekTo(newValue.toLong())
                },
                valueRange = 0f..duration,
                modifier = Modifier.fillMaxWidth().height(20.dp)
            )
        }
    }
}
