package com.apertus.music.player

/**
 * Clock formatting for the progress row.
 *
 * Kept out of the composable on purpose so it can be unit tested: the
 * off-by-one at a minute or hour boundary is exactly the kind of thing that
 * otherwise only shows up on a device, at 59:59.
 */
fun formatPlaybackTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

/** The `-m:ss` countdown on the right of the progress bar. */
fun formatRemainingTime(positionMs: Long, durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    return "-" + formatPlaybackTime((durationMs - positionMs).coerceAtLeast(0L))
}
