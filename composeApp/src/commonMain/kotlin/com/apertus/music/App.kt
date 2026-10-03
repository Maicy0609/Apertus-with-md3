package com.apertus.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.apertus.music.theme.AppTheme
import com.apertus.music.ui.HomeScreen
import com.apertus.music.ui.PlayerScreen
import com.apertus.music.ui.SettingsScreen

/**
 * Application root.
 *
 * Wiring (no DI framework 鈥?created here):
 *   GadulkaPlayer 鈫?GadulkaPlayerController 鈫?PlayerStore 鈫?AppState 鈫?UI
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
        androidx.compose.runtime.LaunchedEffect(appState) {
            appState.loadTracks()
        }

        val currentScreen by appState.currentScreen.collectAsState()

        // Player screen is a full-screen overlay regardless of layout mode.
        if (currentScreen is Screen.Player) {
            PlayerScreen(appState = appState, modifier = Modifier.fillMaxSize())
            return@AppTheme
        }

        // Basic responsive: rail on wide screens, bottom bar on narrow.
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val isWide = maxWidth > 700.dp
            if (isWide) {
                WideLayout(appState = appState)
            } else {
                NarrowLayout(appState = appState)
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
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentScreen is Screen.Settings,
                    onClick = { appState.navigateTo(Screen.Settings) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentScreen) {
                is Screen.Home -> HomeScreen(appState = appState)
                is Screen.Settings -> SettingsScreen(appState = appState)
                is Screen.Player -> {} // handled above
            }
        }
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
                icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                label = { Text("Home") }
            )
            NavigationRailItem(
                selected = currentScreen is Screen.Settings,
                onClick = { appState.navigateTo(Screen.Settings) },
                icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                label = { Text("Settings") }
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                is Screen.Home -> HomeScreen(appState = appState)
                is Screen.Settings -> SettingsScreen(appState = appState)
                is Screen.Player -> {}
            }
        }
    }
}
