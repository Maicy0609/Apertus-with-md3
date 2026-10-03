package com.apertus.music.data

import com.apertus.music.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

// TODO: replace with your real API base URL
const val API_BASE_URL = "https://example.com/api"

interface MusicApi {
    suspend fun getTracks(): List<Track>
}

@Serializable
private data class TracksResponse(val tracks: List<Track>)

class KtorMusicApi(
    private val client: HttpClient,
    private val baseUrl: String = API_BASE_URL
) : MusicApi {
    override suspend fun getTracks(): List<Track> {
        return client.get("$baseUrl/tracks").body<TracksResponse>().tracks
    }
}
