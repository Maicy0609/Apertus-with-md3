package app.data

import app.model.Track

open class MusicRepository(
    private val api: MusicApi
) {
    suspend fun getTracks(): List<Track> = api.getTracks()
}
