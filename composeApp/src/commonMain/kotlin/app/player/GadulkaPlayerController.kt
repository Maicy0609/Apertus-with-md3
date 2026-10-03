package app.player

import app.model.Track
import eu.iamkonstantin.kotlin.gadulka.ErrorListener
import eu.iamkonstantin.kotlin.gadulka.GadulkaPlayer
import eu.iamkonstantin.kotlin.gadulka.GadulkaPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Thin adapter from Gadulka's polling-based API to the app's own PlayerController.
 *
 * Gadulka does not expose StateFlow — it exposes synchronous getters
 * (currentPlayerState / currentPosition / currentDuration). This controller
 * polls every 300 ms (matching Gadulka's own rememberGadulkaLiveState cadence)
 * and exposes StateFlows so the rest of the app stays reactive.
 *
 * UI and business logic never touch Gadulka directly.
 */
class GadulkaPlayerController(
    private val player: GadulkaPlayer
) : PlayerController {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _currentPositionMillis = MutableStateFlow(0L)
    override val currentPositionMillis: StateFlow<Long> = _currentPositionMillis.asStateFlow()

    private val _durationMillis = MutableStateFlow<Long?>(null)
    override val durationMillis: StateFlow<Long?> = _durationMillis.asStateFlow()

    init {
        player.setOnErrorListener(object : ErrorListener {
            override fun onError(message: String?) {
                _state.value = PlayerState(
                    status = PlayerStatus.Error,
                    error = message ?: "Unknown playback error"
                )
            }
        })

        scope.launch {
            while (true) {
                val gadulkaState = player.currentPlayerState()
                _state.value = when (gadulkaState) {
                    GadulkaPlayerState.IDLE -> PlayerState(PlayerStatus.Idle)
                    GadulkaPlayerState.BUFFERING -> PlayerState(
                        status = PlayerStatus.Loading,
                        isBuffering = true
                    )
                    GadulkaPlayerState.PLAYING -> PlayerState(PlayerStatus.Playing)
                    GadulkaPlayerState.PAUSED -> PlayerState(PlayerStatus.Paused)
                    null -> PlayerState(PlayerStatus.Idle)
                }
                _currentPositionMillis.value = player.currentPosition() ?: 0L
                _durationMillis.value = player.currentDuration()
                delay(300)
            }
        }
    }

    override suspend fun play(track: Track) {
        player.play(url = track.streamUrl)
    }

    override fun pause() = player.pause()

    override fun resume() = player.play()

    override fun seekTo(positionMillis: Long) = player.seekTo(positionMillis)

    override fun stop() = player.stop()
}
