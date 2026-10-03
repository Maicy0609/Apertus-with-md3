package com.apertus.music.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SeekGate] is the piece that keeps the progress bar from snapping back while
 * the audio backend re-buffers. Its clock is injectable, so these tests are
 * deterministic rather than timing-dependent.
 */
class SeekGateTest {

    private var now = 0L

    private fun gate(toleranceMs: Long = 400L, timeoutMs: Long = 1_500L) =
        SeekGate(toleranceMs = toleranceMs, timeoutMs = timeoutMs, clock = { now })

    @Test
    fun reportsPassThroughWhenNothingIsPending() {
        val gate = gate()
        assertFalse(gate.isPending)
        assertTrue(gate.accept(1_234L))
    }

    @Test
    fun aStaleReportIsRejectedWhileASeekIsPending() {
        val gate = gate()
        gate.begin(60_000L)

        assertTrue(gate.isPending)
        assertEquals(60_000L, gate.target)
        // The backend is still reporting the pre-seek position.
        assertFalse(gate.accept(1_000L))
        assertTrue(gate.isPending, "a rejected report must not clear the pending seek")
    }

    @Test
    fun aReportInsideTheToleranceIsAccepted() {
        val gate = gate(toleranceMs = 400L)
        gate.begin(60_000L)

        assertTrue(gate.accept(60_300L))
        assertFalse(gate.isPending)
    }

    @Test
    fun aReportExactlyAtTheToleranceBoundaryIsAccepted() {
        val gate = gate(toleranceMs = 400L)
        gate.begin(60_000L)
        assertTrue(gate.accept(59_600L))
    }

    @Test
    fun aSeekThatNeverLandsTimesOutSoRealityWins() {
        val gate = gate(timeoutMs = 1_500L)
        gate.begin(60_000L)

        now = 1_499L
        assertFalse(gate.accept(1_000L), "still inside the timeout window")

        now = 1_500L
        assertTrue(gate.accept(1_000L), "past the timeout the backend's word is taken")
        assertFalse(gate.isPending)
    }

    @Test
    fun resetForgetsThePendingSeek() {
        val gate = gate()
        gate.begin(60_000L)
        gate.reset()

        assertFalse(gate.isPending)
        assertEquals(0L, gate.target)
        assertTrue(gate.accept(1_000L))
    }

    @Test
    fun beginReplacesAnEarlierTargetAndRestartsTheTimeout() {
        val gate = gate(timeoutMs = 1_500L)
        gate.begin(10_000L)
        now = 1_000L
        gate.begin(20_000L)

        now = 2_000L
        assertEquals(20_000L, gate.target)
        assertFalse(gate.accept(10_000L), "the first seek's target must not be trusted any more")
    }
}
