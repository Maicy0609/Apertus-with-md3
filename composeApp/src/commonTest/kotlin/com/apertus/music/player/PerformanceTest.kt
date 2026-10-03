package com.apertus.music.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Performance guards for the playback hot path.
 *
 * These budgets are deliberately one to two orders of magnitude above what the
 * code actually costs, so they cannot flake on a loaded CI runner. They exist to
 * fail loudly if somebody makes the seek path do unbounded work — a linear scan
 * per poll, an allocation per pixel, a blocking call on the UI thread.
 *
 * The real, user-visible numbers (cold start, memory) are measured on a device
 * by `.github/scripts/perf-test.sh`.
 */
class PerformanceTest {

    @Test
    fun halfAMillionStaleReportsAreRejectedCheaply() {
        val gate = SeekGate(clock = { 0L })
        gate.begin(60_000L)

        val mark = TimeSource.Monotonic.markNow()
        var adopted = 0
        repeat(500_000) {
            if (gate.accept(1_000L)) adopted++
        }
        val elapsedMs = mark.elapsedNow().inWholeMilliseconds

        assertEquals(0, adopted, "a stale report must never be adopted")
        assertTrue(
            elapsedMs < 2_000L,
            "500k seek-gate checks took ${elapsedMs}ms; the 300ms poll budget is blown"
        )
    }

    @Test
    fun theGateHoldsNoUnboundedStateAcrossManySeeks() {
        val gate = SeekGate(clock = { 0L })

        repeat(100_000) { index ->
            gate.begin(index.toLong())
            gate.reset()
        }

        assertFalse(gate.isPending)
        assertEquals(0L, gate.target)
        assertTrue(gate.accept(123L), "after a reset every report is trusted again")
    }

    @Test
    fun aHundredThousandClockLabelsStayCheap() {
        val mark = TimeSource.Monotonic.markNow()
        var length = 0
        repeat(100_000) { index ->
            length += formatPlaybackTime(index.toLong() * 997L).length
        }
        val elapsedMs = mark.elapsedNow().inWholeMilliseconds

        assertTrue(length > 0)
        assertTrue(elapsedMs < 2_000L, "100k clock labels took ${elapsedMs}ms")
    }
}
