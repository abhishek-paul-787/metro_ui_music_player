package com.metro.music.data

import android.net.Uri

data class Track(
    val id: Long, val uri: Uri, val title: String, val artist: String, val album: String,
    val albumId: Long, val durationMs: Long, val trackNo: Int, val mime: String, val size: Long, val year: Int,
)

data class Album(val id: Long, val title: String, val artist: String, val tracks: List<Track>)
data class Artist(val name: String, val tracks: List<Track>)
