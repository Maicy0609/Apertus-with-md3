package com.apertus.music.data

import com.apertus.music.model.Track

open class MusicRepository(
    private val api: MusicApi
) {
    suspend fun getTracks(): List<Track> = api.getTracks()
}
