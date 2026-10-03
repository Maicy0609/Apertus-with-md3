package com.apertus.music.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apertus.music.components.Artwork
import com.apertus.music.components.EqualizerBars
import com.apertus.music.player.formatPlaybackTime
import com.apertus.music.player.formatRemainingTime
import com.apertus.music.state.AppState
import com.apertus.music.state.Screen
import com.apertus.music.theme.ApertusIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    appState: AppState,
    modifier: Modifier = Modifier
) {
    val playerStore = appState.playerStore
    val currentTrack by playerStore.currentTrack.collectAsState()
    val isPlaying by playerStore.isPlaying.collectAsState()
    val position by playerStore.position.collectAsState()
    val duration by playerStore.duration.collectAsState()
    val isLoading by playerStore.isLoading.collectAsState()
    val error by playerStore.error.collectAsState()

    val durationMs = duration ?: 0L

    /*
     * Scrub state.
     *
     * The slider must NEVER be driven straight off the polled position: that
     * value only refreshes every 300 ms, and the backend keeps reporting the old
     * position while it re-buffers a seek. Binding the thumb to it means every
     * drag movement is visually undone on the next recomposition, and the thumb
     * only "arrives" a second or two later.
     *
     * So while the user is dragging we render a local fraction instead, and we
     * forward exactly one seek when the gesture ends. Zero round-trips during
     * the drag; one seek per gesture instead of one per pixel.
     */
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val liveFraction = if (durationMs > 0) position.toFloat() / durationMs else 0f
    val scrub = scrubFraction
    val shownFraction = (scrub ?: liveFraction).coerceIn(0f, 1f)
    val shownPosition =
        if (scrub != null && durationMs > 0) (scrub * durationMs).toLong() else position

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = { appState.navigateTo(Screen.Home) }) {
                        Icon(ApertusIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            ArtworkStage(
                artworkUrl = currentTrack?.artwork,
                title = currentTrack?.title,
                isPlaying = isPlaying
            )

            // Title + artist crossfade when the track changes, so switching
            // songs reads as a transition instead of a flicker.
            Crossfade(
                targetState = currentTrack,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                label = "trackText"
            ) { track ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = track?.title ?: "Nothing playing",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track?.artist ?: "Pick a track from your library",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Progress
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = shownFraction,
                    onValueChange = { fraction ->
                        // Local only. No player call until the gesture ends.
                        scrubFraction = fraction
                    },
                    onValueChangeFinished = {
                        val fraction = scrubFraction
                        if (fraction != null && durationMs > 0) {
                            // PlayerStore applies this locally first, so clearing
                            // the scrub state right after cannot cause a jump.
                            playerStore.seekTo((fraction * durationMs).toLong())
                        }
                        scrubFraction = null
                    },
                    enabled = durationMs > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatPlaybackTime(shownPosition),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatRemainingTime(shownPosition, durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TransportRow(
                isPlaying = isPlaying,
                onPrevious = { playerStore.skipPrevious() },
                onPlayPause = {
                    if (isPlaying) playerStore.pause() else playerStore.resume()
                },
                onNext = { playerStore.skipNext() }
            )

            // Status line: animated equaliser while buffering, error text if the
            // backend refused to come up.
            //
            // Crossfade rather than AnimatedVisibility on purpose: this Box lives
            // inside a Column, and AnimatedVisibility has a ColumnScope overload
            // that wins implicit resolution here and then refuses to be called
            // from a BoxScope. Crossfade has no scope-restricted variant.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = isLoading,
                    animationSpec = tween(durationMillis = 180),
                    label = "buffering"
                ) { buffering ->
                    if (buffering) EqualizerBars()
                }
                error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Album art with a slow, almost imperceptible breathing scale while playing.
 * Motion that you feel rather than notice.
 */
@Composable
private fun ArtworkStage(
    artworkUrl: String?,
    title: String?,
    isPlaying: Boolean
) {
    val transition = rememberInfiniteTransition(label = "artwork")
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPlaying) 1.015f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "artworkBreath"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = breath
                scaleY = breath
            }
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Crossfade(
            targetState = artworkUrl,
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            label = "artwork"
        ) { url ->
            Artwork(
                url = url,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                cornerRadiusDp = 28f
            )
        }
    }
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    // The primary action scales up slightly while playing: a quiet, continuous
    // signal of state that costs nothing.
    val playScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.08f else 1f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "playScale"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = ApertusIcons.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.size(20.dp))

        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(76.dp)
                .graphicsLayer {
                    scaleX = playScale
                    scaleY = playScale
                },
            shape = RoundedCornerShape(26.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Crossfade(
                targetState = isPlaying,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "playPause"
            ) { playing ->
                Icon(
                    imageVector = if (playing) ApertusIcons.Pause else ApertusIcons.Play,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(Modifier.size(20.dp))

        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = ApertusIcons.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
