package com.apertus.music.player

import com.apertus.music.model.Track
import eu.iamkonstantin.kotlin.gadulka.ErrorListener
import eu.iamkonstantin.kotlin.gadulka.GadulkaPlayer
import eu.iamkonstantin.kotlin.gadulka.GadulkaPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** How often the backend is polled for state / position / duration. */
private const val POLL_INTERVAL_MS = 300L

/**
 * Thin adapter from Gadulka's polling-based API to the app's own PlayerController.
 *
 * Gadulka does not expose StateFlow — it exposes synchronous getters
 * (currentPlayerState / currentPosition / currentDuration). This controller
 * polls every [POLL_INTERVAL_MS] (matching Gadulka's own rememberGadulkaLiveState
 * cadence) and exposes StateFlows so the rest of the app stays reactive.
 *
 * Seeks are optimistic: [seekTo] publishes the new position immediately and a
 * [SeekGate] suppresses the stale reads the backend emits while it re-buffers.
 * Without that the progress bar snaps backwards on every scrub.
 *
 * UI and business logic never touch Gadulka directly.
 *
 * The backend player is built lazily, on first playback, and every call into it
 * is guarded: app startup must never depend on the audio backend. A device where
 * the native player cannot be created gets an in-app error instead of a crash.
 */
class GadulkaPlayerController(
    private val playerFactory: () -> GadulkaPlayer = { GadulkaPlayer() }
) : PlayerController {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var player: GadulkaPlayer? = null
    private var pollJob: Job? = null

    /** Filters the backend's position reports — see [SeekGate]. */
    private val seekGate = SeekGate()

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _currentPositionMillis = MutableStateFlow(0L)
    override val currentPositionMillis: StateFlow<Long> = _currentPositionMillis.asStateFlow()

    private val _durationMillis = MutableStateFlow<Long?>(null)
    override val durationMillis: StateFlow<Long?> = _durationMillis.asStateFlow()

    /** True while the backend is in an error state that playback has not cleared. */
    private val hasError: Boolean
        get() = _state.value.status == PlayerStatus.Error

    private fun playerOrNull(): GadulkaPlayer? {
        player?.let { return it }
        return try {
            playerFactory().also { created ->
                created.setOnErrorListener(object : ErrorListener {
                    override fun onError(message: String?) {
                        _state.value = PlayerState(
                            status = PlayerStatus.Error,
                            error = message ?: "Unknown playback error"
                        )
                    }
                })
                player = created
                startPolling(created)
            }
        } catch (t: Throwable) {
            // NoClassDefFoundError / UnsatisfiedLinkError / init failures all land here.
            _state.value = PlayerState(
                status = PlayerStatus.Error,
                error = "Audio backend unavailable: ${t.message ?: t::class.simpleName}"
            )
            null
        }
    }

    private fun startPolling(backend: GadulkaPlayer) {
        if (pollJob != null) return
        pollJob = scope.launch {
            while (isActive) {
                val polled = when (backend.currentPlayerState()) {
                    GadulkaPlayerState.IDLE -> PlayerState(PlayerStatus.Idle)
                    GadulkaPlayerState.BUFFERING -> PlayerState(
                        status = PlayerStatus.Loading,
                        isBuffering = true
                    )
                    GadulkaPlayerState.PLAYING -> PlayerState(PlayerStatus.Playing)
                    GadulkaPlayerState.PAUSED -> PlayerState(PlayerStatus.Paused)
                    null -> PlayerState(PlayerStatus.Idle)
                }
                // Never let a poll overwrite a real backend error.
                if (!hasError) _state.value = polled

                val reported = backend.currentPosition() ?: 0L
                _currentPositionMillis.value =
                    if (seekGate.accept(reported)) reported else seekGate.target

                _durationMillis.value = backend.currentDuration()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override suspend fun play(track: Track) {
        val backend = playerOrNull() ?: return
        // A new track invalidates any seek that was still settling.
        seekGate.reset()
        _state.value = PlayerState(status = PlayerStatus.Loading, isBuffering = true)
        try {
            backend.play(url = track.streamUrl)
        } catch (t: Throwable) {
            _state.value = PlayerState(
                status = PlayerStatus.Error,
                error = "Cannot play \"${track.title}\": ${t.message ?: t::class.simpleName}"
            )
        }
    }

    override fun pause() {
        player?.let { runCatching { it.pause() } }
    }

    override fun resume() {
        player?.let { runCatching { it.play() } }
    }

    override fun seekTo(positionMillis: Long) {
        val target = positionMillis.coerceAtLeast(0L)
        // Publish immediately instead of waiting up to a whole poll interval,
        // plus a re-buffer, for the backend to admit it moved.
        seekGate.begin(target)
        _currentPositionMillis.value = target
        player?.let { runCatching { it.seekTo(target) } }
    }

    override fun stop() {
        player?.let { runCatching { it.stop() } }
    }

    override fun release() {
        pollJob?.cancel()
        pollJob = null
        seekGate.reset()
        player?.let { runCatching { it.release() } }
        player = null
        _state.value = PlayerState(PlayerStatus.Idle)
        _currentPositionMillis.value = 0L
        _durationMillis.value = null
    }
}
