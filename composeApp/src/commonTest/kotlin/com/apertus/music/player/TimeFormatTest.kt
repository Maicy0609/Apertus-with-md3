package com.apertus.music.player

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatTest {

    @Test
    fun zeroAndNegativeAreRenderedAsZero() {
        assertEquals("0:00", formatPlaybackTime(0L))
        assertEquals("0:00", formatPlaybackTime(-1L))
        assertEquals("0:00", formatPlaybackTime(999L))
    }

    @Test
    fun secondsArePaddedToTwoDigits() {
        assertEquals("0:01", formatPlaybackTime(1_000L))
        assertEquals("0:09", formatPlaybackTime(9_999L))
        assertEquals("0:59", formatPlaybackTime(59_999L))
    }

    @Test
    fun theMinuteBoundaryRollsOver() {
        assertEquals("1:00", formatPlaybackTime(60_000L))
        assertEquals("1:01", formatPlaybackTime(61_500L))
        assertEquals("9:59", formatPlaybackTime(599_000L))
        assertEquals("10:00", formatPlaybackTime(600_000L))
    }

    @Test
    fun longTracksGainAnHoursField() {
        assertEquals("1:00:00", formatPlaybackTime(3_600_000L))
        assertEquals("1:01:01", formatPlaybackTime(3_661_000L))
        assertEquals("10:00:00", formatPlaybackTime(36_000_000L))
    }

    @Test
    fun remainingCountsDownWithAMinusSign() {
        assertEquals("-1:00", formatRemainingTime(0L, 60_000L))
        assertEquals("-0:30", formatRemainingTime(30_000L, 60_000L))
        assertEquals("-0:00", formatRemainingTime(60_000L, 60_000L))
    }

    @Test
    fun remainingNeverGoesPositiveWhenThePositionOvershoots() {
        assertEquals("-0:00", formatRemainingTime(90_000L, 60_000L))
    }

    @Test
    fun remainingIsBlankWhileTheDurationIsUnknown() {
        assertEquals("0:00", formatRemainingTime(0L, 0L))
        assertEquals("0:00", formatRemainingTime(1_000L, -5L))
    }
}
