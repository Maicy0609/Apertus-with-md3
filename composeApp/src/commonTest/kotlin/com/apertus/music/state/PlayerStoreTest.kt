package com.apertus.music.state

import com.apertus.music.model.Track
import com.apertus.music.player.PlayerController
import com.apertus.music.player.PlayerState
import com.apertus.music.player.PlayerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun track(id: String) = Track(
    id = id,
    title = "Title $id",
    artist = "Artist $id",
    streamUrl = "https://example.com/$id.mp3"
)

private class FakePlayerController : PlayerController {
    override val state = MutableStateFlow(PlayerState())
    override val currentPositionMillis = MutableStateFlow(0L)
    override val durationMillis = MutableStateFlow<Long?>(null)

    val played = mutableListOf<String>()
    var lastSeekMillis: Long? = null
    var pauseCount = 0
    var resumeCount = 0

    override suspend fun play(track: Track) {
        played += track.id
    }

    override fun pause() {
        pauseCount++
    }

    override fun resume() {
        resumeCount++
    }

    override fun seekTo(positionMillis: Long) {
        lastSeekMillis = positionMillis
    }

    override fun stop() = Unit
    override fun release() = Unit
}

class PlayerStoreTest {

    private fun TestScope.storeWith(controller: PlayerController) = PlayerStore(controller, this)

    /**
     * Regression test for the reported "seeking takes two seconds to respond"
     * defect: the store must publish the seek target itself instead of waiting
     * for a poll round-trip, and a subsequent poll must not drag it back.
     */
    @Test
    fun aSeekIsVisibleBeforeTheBackendReportsIt() = runTest {
        val controller = FakePlayerController()
        val store = storeWith(controller)
        advanceUntilIdle() // collectors are live, both sides sitting at 0

        store.seekTo(42_000L)

        assertEquals(42_000L, store.position.value)
        assertEquals(42_000L, controller.lastSeekMillis)

        // A poll tick happens, and the backend is still reporting its old
        // position. The bar must stay where the user put it.
        advanceUntilIdle()
        assertEquals(
            42_000L,
            store.position.value,
            "the poll must not drag the progress bar back to the pre-seek position"
        )
    }

    @Test
    fun aSeekIsClampedAtZero() = runTest {
        val controller = FakePlayerController()
        val store = storeWith(controller)

        store.seekTo(-5_000L)

        assertEquals(0L, store.position.value)
        assertEquals(0L, controller.lastSeekMillis)
    }

    @Test
    fun backendPositionUpdatesReachTheStore() = runTest {
        val controller = FakePlayerController()
        val store = storeWith(controller)
        advanceUntilIdle()

        controller.currentPositionMillis.value = 5_000L
        advanceUntilIdle()

        assertEquals(5_000L, store.position.value)
    }

    @Test
    fun playbackStatusIsMirroredFromTheController() = runTest {
        val controller = FakePlayerController()
        val store = storeWith(controller)
        advanceUntilIdle()

        controller.state.value = PlayerState(status = PlayerStatus.Playing)
        advanceUntilIdle()
        assertEquals(true, store.isPlaying.value)

        controller.state.value = PlayerState(status = PlayerStatus.Loading, isBuffering = true)
        advanceUntilIdle()
        assertEquals(false, store.isPlaying.value)
        assertEquals(true, store.isLoading.value)

        controller.state.value = PlayerState(status = PlayerStatus.Error, error = "backend exploded")
        advanceUntilIdle()
        assertEquals("backend exploded", store.error.value)
    }

    @Test
    fun playRecordsTheTrackAndAsksTheController() = runTest {
        val controller = FakePlayerController()
        val store = storeWith(controller)
        val chosen = track("a")

        store.play(chosen)

        assertEquals(chosen, store.currentTrack.value)
        advanceUntilIdle()
        assertEquals(listOf("a"), controller.played)
    }

    @Test
    fun skipNextAndPreviousWrapAroundThePlaylist() = runTest {
        val controller = FakePlayerController()
        val store = storeWith(controller)
        store.setPlaylist(listOf(track("a"), track("b"), track("c")))

        store.play(track("a"))
        store.skipNext()
        assertEquals("b", store.currentTrack.value?.id)

        store.skipPrevious()
        assertEquals("a", store.currentTrack.value?.id)

        // Wrapping backwards from the first entry lands on the last.
        store.skipPrevious()
        assertEquals("c", store.currentTrack.value?.id)

        // ...and forwards from the last lands on the first.
        store.skipNext()
        assertEquals("a", store.currentTrack.value?.id)
    }

    @Test
    fun skippingAnEmptyPlaylistIsANoOp() = runTest {
        val controller = FakePlayerController()
        val store = storeWith(controller)

        store.skipNext()
        store.skipPrevious()

        assertNull(store.currentTrack.value)
    }

    @Test
    fun pauseAndResumeAreForwarded() = runTest {
        val controller = FakePlayerController()
        val store = storeWith(controller)

        store.pause()
        store.resume()

        assertEquals(1, controller.pauseCount)
        assertEquals(1, controller.resumeCount)
    }
}
