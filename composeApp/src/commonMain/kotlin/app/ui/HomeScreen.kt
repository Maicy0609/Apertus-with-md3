package app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import app.components.MiniPlayer
import app.components.TrackItem
import app.state.AppState
import app.state.Screen

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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Melody", style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            MiniPlayer(
                track = currentTrack,
                isPlaying = isPlaying,
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
            if (isLoading && tracks.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(tracks, key = { it.id }) { track ->
                        TrackItem(
                            track = track,
                            onClick = {
                                appState.playerStore.play(track)
                                appState.navigateTo(Screen.Player)
                            }
                        )
                    }
                }
            }
        }
    }
}
