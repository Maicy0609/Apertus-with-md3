package com.apertus.music.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artwork: String? = null,
    val streamUrl: String
)
