package com.apertus.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apertus.music.data.FakeMusicRepository
import com.apertus.music.player.GadulkaPlayerController
import com.apertus.music.state.AppState
import com.apertus.music.state.PlayerStore
import com.apertus.music.state.Screen
import com.apertus.music.theme.ApertusIcons
import com.apertus.music.theme.AppTheme
import com.apertus.music.ui.HomeScreen
import com.apertus.music.ui.PlayerScreen
import com.apertus.music.ui.SettingsScreen

/**
 * Application root.
 *
 * Wiring (no DI framework — created here):
 *   GadulkaPlayer → GadulkaPlayerController → PlayerStore → AppState → UI
 *
 * FakeMusicRepository provides demo tracks; swap with
 * MusicRepository(KtorMusicApi(httpClient)) when your API is ready.
 */
@Composable
fun App() {
    AppTheme {
        // The audio backend is built lazily on first playback, so nothing
        // platform-specific can abort app startup. Release when composition leaves.
        val controller = remember { GadulkaPlayerController() }
        DisposableEffect(controller) {
            onDispose { controller.release() }
        }

        val playerStore = remember(controller) { PlayerStore(controller) }
        val appState = remember(playerStore) {
            AppState(
                repository = FakeMusicRepository(),
                playerStore = playerStore
            )
        }

        // Load tracks once on launch.
        LaunchedEffect(appState) {
            appState.loadTracks()
        }

        val currentScreen by appState.currentScreen.collectAsState()
        val showPlayer = currentScreen is Screen.Player

        Box(modifier = Modifier.fillMaxSize()) {
            // The library stays composed underneath so returning from the player
            // is instant (no artwork re-fetch, no list rebuild).
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWide = maxWidth > 700.dp
                if (isWide) {
                    WideLayout(appState = appState)
                } else {
                    NarrowLayout(appState = appState)
                }
            }

            // Player rises from the bottom rather than replacing the screen,
            // which makes "open" and "close" read as one continuous motion.
            AnimatedVisibility(
                visible = showPlayer,
                enter = slideInVertically(
                    animationSpec = tween(durationMillis = 380),
                    initialOffsetY = { it }
                ) + fadeIn(animationSpec = tween(durationMillis = 220)),
                exit = slideOutVertically(
                    animationSpec = tween(durationMillis = 320),
                    targetOffsetY = { it }
                ) + fadeOut(animationSpec = tween(durationMillis = 180))
            ) {
                PlayerScreen(appState = appState, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun NarrowLayout(appState: AppState) {
    val currentScreen by appState.currentScreen.collectAsState()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen is Screen.Home,
                    onClick = { appState.navigateTo(Screen.Home) },
                    icon = { Icon(ApertusIcons.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentScreen is Screen.Settings,
                    onClick = { appState.navigateTo(Screen.Settings) },
                    icon = { Icon(ApertusIcons.Tune, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        ScreenHost(appState = appState, modifier = Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
private fun WideLayout(appState: AppState) {
    val currentScreen by appState.currentScreen.collectAsState()
    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail {
            NavigationRailItem(
                selected = currentScreen is Screen.Home,
                onClick = { appState.navigateTo(Screen.Home) },
                icon = { Icon(ApertusIcons.Home, contentDescription = "Home") },
                label = { Text("Home") }
            )
            NavigationRailItem(
                selected = currentScreen is Screen.Settings,
                onClick = { appState.navigateTo(Screen.Settings) },
                icon = { Icon(ApertusIcons.Tune, contentDescription = "Settings") },
                label = { Text("Settings") }
            )
        }
        ScreenHost(appState = appState, modifier = Modifier.fillMaxSize())
    }
}

/** Crossfades between the library and settings instead of cutting. */
@Composable
private fun ScreenHost(appState: AppState, modifier: Modifier = Modifier) {
    val currentScreen by appState.currentScreen.collectAsState()
    Crossfade(
        targetState = currentScreen,
        modifier = modifier,
        animationSpec = tween(durationMillis = 240),
        label = "screen"
    ) { screen ->
        when (screen) {
            is Screen.Home -> HomeScreen(appState = appState)
            is Screen.Settings -> SettingsScreen(appState = appState)
            // Covered by the player overlay; keep the previous frame underneath.
            is Screen.Player -> HomeScreen(appState = appState)
        }
    }
}
