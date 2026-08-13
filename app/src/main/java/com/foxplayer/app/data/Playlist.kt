package com.foxplayer.app.data

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>
)
