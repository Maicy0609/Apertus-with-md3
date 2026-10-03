package com.apertus.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apertus.music.components.EqualizerBars
import com.apertus.music.components.MiniPlayer
import com.apertus.music.components.TrackItem
import com.apertus.music.state.AppState
import com.apertus.music.state.Screen
import com.apertus.music.theme.ApertusIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    appState: AppState,
    modifier: Modifier = Modifier
) {
    val tracks by appState.tracks.collectAsState()
    val isLoading by appState.isLoading.collectAsState()
    val currentTrack by appState.playerStore.currentTrack.collectAsState()
    val isPlaying by appState.playerStore.isPlaying.collectAsState()
    val position by appState.playerStore.position.collectAsState()
    val duration by appState.playerStore.duration.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = ApertusIcons.Logo,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Apertus", style = MaterialTheme.typography.headlineSmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            MiniPlayer(
                track = currentTrack,
                isPlaying = isPlaying,
                positionMs = position,
                durationMs = duration ?: 0L,
                onPlayPause = {
                    if (isPlaying) appState.playerStore.pause()
                    else appState.playerStore.resume()
                },
                onClick = { appState.navigateTo(Screen.Player) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Crossfade between "loading" and "list" instead of swapping abruptly.
            androidx.compose.animation.Crossfade(
                targetState = isLoading && tracks.isEmpty(),
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                label = "libraryState"
            ) { loading ->
                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EqualizerBars(barWidth = 6.dp, maxHeight = 34.dp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(tracks, key = { it.id }) { track ->
                            TrackItem(
                                track = track,
                                isCurrent = track.id == currentTrack?.id,
                                isPlaying = isPlaying,
                                onClick = {
                                    appState.playerStore.play(track)
                                    appState.navigateTo(Screen.Player)
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }

            // Empty state, faded in only once loading has actually finished.
            AnimatedVisibility(
                visible = !isLoading && tracks.isEmpty(),
                enter = fadeIn(tween(durationMillis = 260)),
                exit = fadeOut(tween(durationMillis = 160))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = ApertusIcons.Library,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Your library is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
