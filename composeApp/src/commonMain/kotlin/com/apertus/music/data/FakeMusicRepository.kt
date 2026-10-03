package com.apertus.music.data

import com.apertus.music.model.Track
import kotlinx.coroutines.delay

/**
 * Minimal in-memory repository so the app runs without a real API.
 * Swap with MusicRepository(KtorMusicApi(client)) when your API is ready.
 */
class FakeMusicRepository : MusicRepository(api = object : MusicApi {
    override suspend fun getTracks(): List<Track> {
        delay(300) // simulate network
        return demoTracks
    }
})

private val demoTracks = listOf(
    Track(
        id = "1",
        title = "SoundHelix Song 1",
        artist = "SoundHelix",
        album = "Demo Collection",
        artwork = "https://picsum.photos/seed/track1/600/600",
        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
    ),
    Track(
        id = "2",
        title = "SoundHelix Song 2",
        artist = "SoundHelix",
        album = "Demo Collection",
        artwork = "https://picsum.photos/seed/track2/600/600",
        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
    ),
    Track(
        id = "3",
        title = "SoundHelix Song 3",
        artist = "SoundHelix",
        album = "Demo Collection",
        artwork = "https://picsum.photos/seed/track3/600/600",
        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"
    ),
    Track(
        id = "4",
        title = "SoundHelix Song 4",
        artist = "SoundHelix",
        album = "Demo Collection",
        artwork = "https://picsum.photos/seed/track4/600/600",
        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"
    )
)
