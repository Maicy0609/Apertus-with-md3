package com.apertus.music.state

import com.apertus.music.data.MusicRepository
import com.apertus.music.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Player : Screen
    data object Settings : Screen
}

/**
 * Top-level app state: navigation, track list, and wiring to PlayerStore.
 * No DI framework — instances are created at the platform entry point.
 */
class AppState(
    val repository: MusicRepository,
    val playerStore: PlayerStore
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun loadTracks() {
        scope.launch {
            _isLoading.value = true
            try {
                val tracks = repository.getTracks()
                _tracks.value = tracks
                playerStore.setPlaylist(tracks)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
