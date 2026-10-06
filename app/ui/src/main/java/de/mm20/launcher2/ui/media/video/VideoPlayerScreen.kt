package de.mm20.launcher2.ui.media.video

import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import de.mm20.launcher2.comms.media.video.ResumeStore
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    uris: List<Uri>,
    titles: List<String>,
    startIndex: Int,
    onClose: () -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    inPictureInPicture: Boolean,
) {
    val context = LocalContext.current

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val items = uris.mapIndexed { index, uri ->
                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(uri.toString())
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(titles.getOrNull(index)).build())
                    .build()
            }
            val progress = ResumeStore.progress(context, uris[startIndex])
            val resumeAt = if (progress in 0.02f..0.95f) ResumeStore.position(context, uris[startIndex]) else 0L
            setMediaItems(items, startIndex, resumeAt)
            prepare()
            playWhenReady = true
        }
    }

    fun saveProgress() {
        val item = player.currentMediaItem ?: return
        val duration = player.duration
        if (duration > 0) ResumeStore.save(context, Uri.parse(item.mediaId), player.currentPosition, duration)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = onPlayingChanged(isPlaying)

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) saveProgress()
            }
        }
        player.addListener(listener)
        onDispose {
            saveProgress()
            player.removeListener(listener)
            player.release()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { saveProgress() }
    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            saveProgress()
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf(titles.getOrNull(startIndex).orEmpty()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                title = mediaMetadata.title?.toString().orEmpty()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { subtitle ->
        if (subtitle != null) {
            val index = player.currentMediaItemIndex
            val current = player.currentMediaItem ?: return@rememberLauncherForActivityResult
            val name = context.contentResolver.query(subtitle, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }.orEmpty().lowercase()
            val mime = when {
                name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
                name.endsWith(".ass") || name.endsWith(".ssa") -> MimeTypes.TEXT_SSA
                name.endsWith(".ttml") || name.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            val withSubtitle = current.buildUpon()
                .setSubtitleConfigurations(
                    listOf(
                        MediaItem.SubtitleConfiguration.Builder(subtitle)
                            .setMimeType(mime)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                    )
                )
                .build()
            val position = player.currentPosition
            player.replaceMediaItem(index, withSubtitle)
            player.seekTo(index, position)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    setShowSubtitleButton(true)
                    setShowNextButton(true)
                    setShowPreviousButton(true)
                    controllerShowTimeoutMs = 3000
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        controlsVisible = visibility == View.VISIBLE
                    })
                }
            },
            update = { view -> view.useController = !inPictureInPicture },
        )
        AnimatedVisibility(
            visible = controlsVisible && !inPictureInPicture,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { subtitlePicker.launch("*/*") }) {
                    Text("Subtitles…", color = Color.White)
                }
            }
        }
    }
}
