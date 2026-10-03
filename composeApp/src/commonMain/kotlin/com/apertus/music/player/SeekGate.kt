package com.apertus.music.player

import kotlin.math.abs
import kotlin.time.TimeSource

/** Monotonic, process-wide reference point for the default clock. */
private val processStart = TimeSource.Monotonic.markNow()

/**
 * Decides whether a position reported by the audio backend can be trusted.
 *
 * This exists because of a real, user-visible defect: ExoPlayer (and most
 * backends) keep reporting the OLD position while they re-buffer a seek. A UI
 * that simply renders whatever the backend says will drag its progress bar back
 * to where it was the instant the user lets go of the thumb, and only catch up
 * a second or two later.
 *
 * A [SeekGate] remembers where a seek was sent. Until the backend agrees (within
 * [toleranceMs]) or [timeoutMs] elapses, reports are rejected and the caller
 * keeps displaying [target]. The timeout matters: a seek that never lands must
 * not freeze the progress bar forever.
 *
 * Deliberately pure logic with an injectable clock, so the behaviour is
 * deterministic in tests instead of depending on wall-clock timing.
 */
class SeekGate(
    private val toleranceMs: Long = DEFAULT_TOLERANCE_MS,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val clock: () -> Long = { processStart.elapsedNow().inWholeMilliseconds }
) {

    private var targetMs: Long = 0L
    private var markedAtMs: Long = 0L
    private var pending: Boolean = false

    /** What the caller should display while a seek is unresolved. */
    val target: Long get() = targetMs

    /** True while a seek has been issued but not yet confirmed by the backend. */
    val isPending: Boolean get() = pending

    /** Records that a seek to [positionMs] was just issued. */
    fun begin(positionMs: Long) {
        targetMs = positionMs
        markedAtMs = clock()
        pending = true
    }

    /**
     * Feeds in the backend's reported position.
     *
     * @return true when the caller should adopt [reportedMs]; false when it must
     *         keep displaying [target] because the seek has not landed yet.
     */
    fun accept(reportedMs: Long): Boolean {
        if (!pending) return true
        if (clock() - markedAtMs >= timeoutMs) {
            // Give up and trust reality: a seek that never lands must not pin
            // the progress bar to a stale target.
            pending = false
            return true
        }
        if (abs(reportedMs - targetMs) <= toleranceMs) {
            pending = false
            return true
        }
        return false
    }

    /** Forgets any pending seek — used on release or when the track changes. */
    fun reset() {
        pending = false
        targetMs = 0L
    }

    companion object {
        /** How close the backend must get before its report is believed. */
        const val DEFAULT_TOLERANCE_MS: Long = 400L

        /** Upper bound on how long an optimistic seek position is trusted. */
        const val DEFAULT_TIMEOUT_MS: Long = 1_500L
    }
}
