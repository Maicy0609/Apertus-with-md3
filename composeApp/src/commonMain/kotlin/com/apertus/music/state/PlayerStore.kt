package com.apertus.music.state

import com.apertus.music.model.Track
import com.apertus.music.player.PlayerController
import com.apertus.music.player.PlayerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds current track, playback state and controls. UI only talks to this.
 * Playlist is kept here so skipNext/skipPrevious work without a queue abstraction.
 */
class PlayerStore(
    private val controller: PlayerController,
    /** Overridable so unit tests do not need a working Main dispatcher. */
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow<Long?>(null)
    val duration: StateFlow<Long?> = _duration.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var playlist: List<Track> = emptyList()
    private var currentIndex: Int = -1

    init {
        scope.launch {
            controller.state.collect { ps ->
                _isPlaying.value = ps.status == PlayerStatus.Playing
                _isLoading.value = ps.status == PlayerStatus.Loading
                _error.value = ps.error
            }
        }
        scope.launch {
            controller.currentPositionMillis.collect { _position.value = it }
        }
        scope.launch {
            controller.durationMillis.collect { _duration.value = it }
        }
    }

    fun setPlaylist(tracks: List<Track>) {
        playlist = tracks
    }

    fun play(track: Track) {
        currentIndex = playlist.indexOfFirst { it.id == track.id }
        _currentTrack.value = track
        _error.value = null
        scope.launch { controller.play(track) }
    }

    fun pause() = controller.pause()
    fun resume() = controller.resume()

    /**
     * Jumps to [positionMillis].
     *
     * The position is applied locally before the backend is told, so the UI never
     * has to wait for a poll round-trip to reflect the user's intent. The
     * controller holds that value until the backend catches up.
     */
    fun seekTo(positionMillis: Long) {
        val target = positionMillis.coerceAtLeast(0L)
        _position.value = target
        controller.seekTo(target)
    }

    fun skipNext() {
        if (playlist.isEmpty()) return
        currentIndex = (currentIndex + 1).mod(playlist.size)
        play(playlist[currentIndex])
    }

    fun skipPrevious() {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex <= 0) playlist.size - 1 else currentIndex - 1
        play(playlist[currentIndex])
    }
}
