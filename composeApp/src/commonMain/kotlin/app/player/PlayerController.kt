package app.player

import app.model.Track
import kotlinx.coroutines.flow.StateFlow

enum class PlayerStatus {
    Idle, Loading, Playing, Paused, Error, Completed
}

data class PlayerState(
    val status: PlayerStatus = PlayerStatus.Idle,
    val isBuffering: Boolean = false,
    val error: String? = null
)

interface PlayerController {
    val state: StateFlow<PlayerState>
    val currentPositionMillis: StateFlow<Long>
    val durationMillis: StateFlow<Long?>

    suspend fun play(track: Track)
    fun pause()
    fun resume()
    fun seekTo(positionMillis: Long)
    fun stop()
}
